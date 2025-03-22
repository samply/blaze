(ns blaze.fhir.spec.resource
  "JSON Parsing.

  Use `create-type-handlers` to create a map of type-handlers that has to be
  given to `parse-json` in order to parse JSON from a source.

  A type-handler in this namespace is a function taking a parser and returns
  either a FHIR value or an anomaly in case of errors.

  A property-handler in this namespace is a function taking a parser and a
  partially constructed FHIR value and returns either the FHIR value with data
  added or an anomaly in case of errors."
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.string-util :as su]
   [blaze.fhir.spec.type.system :as system]
   [clojure.alpha.spec :as s2]
   [clojure.string :as str]
   [cognitect.anomalies :as anom])
  (:import
   [com.fasterxml.jackson.core JsonFactory JsonParser JsonToken StreamReadConstraints]
   [com.fasterxml.jackson.databind JsonNode ObjectMapper]
   [com.fasterxml.jackson.databind.node TreeTraversingParser]
   [java.io InputStream Reader]
   [java.util Arrays]))

(set! *warn-on-reflection* true)

(defn- backbone-element-definition? [{types :type}]
  (and (= 1 (count types)) (#{"Element" "BackboneElement"} (-> types first :code))))

(defn- separate-element-definitions*
  [parent-type element-definitions]
  (loop [[{:keys [path] :as ed} & more :as all] element-definitions
         types {}
         out []]
    (cond
      (nil? ed)
      {:types (assoc types parent-type out)}

      (not (str/starts-with? path parent-type))
      {:types (assoc types parent-type out) :more all}

      (backbone-element-definition? ed)
      (let [{:keys [more] child-types :types} (separate-element-definitions* path more)]
        (recur more (merge types child-types) (conj out ed)))

      :else
      (recur more types (conj out ed)))))

(defn- separate-element-definitions
  "Separates nested backbone element definitions from `element-definitions` and
  returns a map of type name to non-nesting element definitions.

  In case `parent-type` has no nested backbone element definitions, the map will
  only contain only the parent type as key."
  [parent-type element-definitions]
  (:types (separate-element-definitions* parent-type element-definitions)))

(defn- unknown-property-anom [name]
  (ba/incorrect (format "Unknown property `%s`." name)))

(defn- prepare-element-type [{:keys [code]} path]
  (condp = code
    "http://hl7.org/fhirpath/System.String" :system/string
    "http://hl7.org/fhirpath/System.Time" :system/time
    "http://hl7.org/fhirpath/System.Date" :system/date
    "http://hl7.org/fhirpath/System.DateTime" :system/date-time
    "http://hl7.org/fhirpath/System.Integer" :system/integer
    "http://hl7.org/fhirpath/System.Decimal" :system/decimal
    "http://hl7.org/fhirpath/System.Boolean" :system/boolean
    "Element" (keyword "element" path)
    "BackboneElement" (keyword "backboneElement" path)
    (keyword
     (if (Character/isLowerCase ^char (first code))
       "primitive"
       "complex")
     code)))

(defn- base-field-name
  "The field name without possible polymorphic type."
  [parent-type path polymorphic]
  (subs path (inc (count parent-type))
        (cond-> (count path) polymorphic (- 3))))

(defn- property-handler-definitions
  "Takes `element-definition` and returns possibly multiple
  property handler definitions, one for each polymorphic type.

  An element handler definition contains:
   * field-name - the name of the JSON property
   * key - the key of the internal representation
   * type - a keyword of the FHIR element type
   * cardinality - :single or :many"
  {:arglists '([parent-type element-definition])}
  [parent-type
   {:keys [path max] content-reference :contentReference element-types :type}]
  (if content-reference
    (let [base-field-name (base-field-name parent-type path false)]
      [{:field-name base-field-name
        :key (keyword base-field-name)
        :type (keyword "backboneElement" (subs content-reference 1))
        :cardinality (if (= "*" max) :many :single)}])
    (let [polymorphic (< 1 (count element-types))]
      (map
       (fn [element-type]
         (let [element-type (prepare-element-type element-type path)
               base-field-name (base-field-name parent-type path polymorphic)]
           {:field-name (cond-> base-field-name polymorphic (str (su/capital (name element-type))))
            :key (keyword base-field-name)
            :type element-type
            :cardinality (if (= "*" max) :many :single)}))
       element-types))))

(defn- incorrect-token-anom [^JsonParser parser & expected-tokens]
  (ba/incorrect
   (if (.currentToken parser)
     (format "Expected token %s but was token %s at %s"
             (if (= 1 (count expected-tokens))
               (first expected-tokens)
               (format "one of %s" (str/join ", " expected-tokens)))
             (.currentToken parser) (.currentLocation parser))
     (str "End of input at " (.currentLocation parser)))))

(defn- create-system-string-handler [assoc-fn]
  (fn system-string-handler [_ ^JsonParser parser m]
    (if (identical? JsonToken/VALUE_STRING (.nextToken parser))
      (assoc-fn m (type/string (.getText parser)))
      (incorrect-token-anom parser JsonToken/VALUE_STRING))))

(defn- duplicate-property-anom [field-name]
  (ba/incorrect (format "duplicate property `%s`" field-name)))

(defn- wrap-constructor [constructor]
  (fn [value]
    (let [primitive-value (constructor value)]
      (if (s2/invalid? primitive-value)
        (ba/incorrect (format "Invalid value `%s`." value))
        primitive-value))))

(defn- assoc-primitive-value
  "Associates `value` to `m` under `key`.

  In case an extended primitive value exists already, updates that primitive
  value with `value`. Otherwise uses `constructor` to create a new primitive
  value."
  [field-name key m constructor value]
  (if-some [primitive-value (get m key)]
    (if (some? (type/value primitive-value))
      (duplicate-property-anom field-name)
      (assoc m key (type/assoc-value primitive-value value)))
    (when-ok [primitive-value (constructor value)]
      (assoc m key primitive-value))))

(defn- assoc-primitive-many-value
  "Like `assoc-primitive-value` but with a single value for cardinality many."
  [{:keys [field-name key]} m constructor value]
  (if-some [primitive-value (first (get m key))]
    (if (some? (type/value primitive-value))
      (duplicate-property-anom field-name)
      (assoc m key [(type/assoc-value primitive-value value)]))
    (when-ok [primitive-value (constructor value)]
      (assoc m key [primitive-value]))))

(defn- primitive-boolean-value-handler [{:keys [field-name key]}]
  (fn [_ ^JsonParser parser m]
    (condp identical? (.nextToken parser)
      JsonToken/VALUE_TRUE (assoc-primitive-value field-name key m type/boolean true)
      JsonToken/VALUE_FALSE (assoc-primitive-value field-name key m type/boolean false)
      (incorrect-token-anom parser JsonToken/VALUE_TRUE JsonToken/VALUE_FALSE))))

(defn- primitive-value-handler
  "Returns a property-handler."
  {:arglists '([property-handler-definition constructor token extract-value])}
  ([{:keys [field-name key cardinality] :as def} constructor token extract-value]
   (if (= :single cardinality)
     (fn primitive-property-handler-one-token-cardinality-single [_ ^JsonParser parser m]
       (if (identical? token (.nextToken parser))
         (when-ok [value (extract-value parser)]
           (assoc-primitive-value field-name key m constructor value))
         (incorrect-token-anom parser token)))
     (fn primitive-property-handler-one-token-cardinality-many [_ ^JsonParser parser m]
       (condp identical? (.nextToken parser)
         JsonToken/START_ARRAY
         (loop [l (or (get m key) []) i 0]
           (condp identical? (.nextToken parser)
             token
             (when-ok [value (extract-value parser)]
               (if-some [primitive-value (get l i)]
                 (if (some? (type/value primitive-value))
                   (duplicate-property-anom field-name)
                   (recur (update l i type/assoc-value value) (inc i)))
                 (when-ok [primitive-value (constructor value)]
                   (recur (assoc l i primitive-value) (inc i)))))
             JsonToken/END_ARRAY (assoc m key l)
             JsonToken/VALUE_NULL
             (recur (update l i identity) (inc i))
             (incorrect-token-anom parser token JsonToken/END_ARRAY
                                   JsonToken/VALUE_NULL)))
         token
         (when-ok [value (extract-value parser)]
           (assoc-primitive-many-value def m constructor value))
         (incorrect-token-anom parser JsonToken/START_ARRAY token)))))
  ([{:keys [field-name key cardinality] :as def} constructor token-1
    extract-value-1 token-2 extract-value-2]
   (if (= :single cardinality)
     (fn primitive-property-handler-two-tokens-cardinality-single [_ ^JsonParser parser m]
       (condp identical? (.nextToken parser)
         token-1
         (when-ok [value (extract-value-1 parser)]
           (assoc-primitive-value field-name key m constructor value))
         token-2
         (when-ok [value (extract-value-2 parser)]
           (assoc-primitive-value field-name key m constructor value))
         (incorrect-token-anom parser token-1 token-2)))
     (fn primitive-property-handler-two-tokens-cardinality-many [_ ^JsonParser parser m]
       (condp identical? (.nextToken parser)
         JsonToken/START_ARRAY
         (loop [l (or (get m key) []) i 0]
           (condp identical? (.nextToken parser)
             token-1
             (when-ok [value (extract-value-1 parser)]
               (if-some [primitive-value (get l i)]
                 (if (some? (type/value primitive-value))
                   (duplicate-property-anom field-name)
                   (recur (update l i type/assoc-value value) (inc i)))
                 (when-ok [primitive-value (constructor value)]
                   (recur (assoc l i primitive-value) (inc i)))))
             token-2
             (when-ok [value (extract-value-2 parser)]
               (if-some [primitive-value (get l i)]
                 (if (some? (type/value primitive-value))
                   (duplicate-property-anom field-name)
                   (recur (update l i type/assoc-value value) (inc i)))
                 (when-ok [primitive-value (constructor value)]
                   (recur (assoc l i primitive-value) (inc i)))))
             JsonToken/END_ARRAY (assoc m key l)
             JsonToken/VALUE_NULL
             (recur (update l i identity) (inc i))
             (incorrect-token-anom parser token-1 token-2 JsonToken/END_ARRAY
                                   JsonToken/VALUE_NULL)))
         token-1
         (when-ok [value (extract-value-1 parser)]
           (assoc-primitive-many-value def m constructor value))
         token-2
         (when-ok [value (extract-value-2 parser)]
           (assoc-primitive-many-value def m constructor value))
         (incorrect-token-anom parser JsonToken/START_ARRAY token-1 token-2))))))

(defmacro recur-ok [expr-form]
  `(when-ok [r# ~expr-form]
     (recur r#)))

(def ^:private primitive-id-handler
  (create-system-string-handler type/assoc-id))

(defn- parse-complex-list [handler type-handlers ^JsonParser p]
  (loop [list (transient [])]
    (condp identical? (.nextToken p)
      JsonToken/START_OBJECT
      (when-ok [value (handler type-handlers p)]
        (recur (conj! list value)))
      JsonToken/END_ARRAY (persistent! list)
      (incorrect-token-anom p JsonToken/START_OBJECT JsonToken/END_ARRAY))))

(defn- unsupported-type-anom [type]
  (ba/unsupported (format "Unsupported type `%s`." type)))

(defn- parse-extended-primitive-properties
  [type-handlers ^JsonParser parser data]
  (loop [data data]
    (condp identical? (.nextToken parser)
      JsonToken/FIELD_NAME
      (condp = (.currentName parser)
        "id" (recur-ok (primitive-id-handler type-handlers parser data))
        "extension"
        (if-let [extension-handler (get type-handlers :Extension)]
          (condp identical? (.nextToken parser)
            JsonToken/START_ARRAY
            (when-ok [list (parse-complex-list extension-handler type-handlers parser)]
              (recur (type/assoc-extension data list)))
            JsonToken/START_OBJECT
            (when-ok [extension (extension-handler type-handlers parser)]
              (recur (type/assoc-extension data [extension])))
            (incorrect-token-anom parser JsonToken/START_ARRAY JsonToken/START_OBJECT))
          (unsupported-type-anom "Extension"))
        (unknown-property-anom (.currentName parser)))
      JsonToken/END_OBJECT data
      (incorrect-token-anom parser JsonToken/FIELD_NAME JsonToken/END_OBJECT))))

(defn- extended-primitive-handler
  "Returns a property-handler."
  [{:keys [key cardinality]} constructor]
  (if (= :single cardinality)
    (fn [type-handlers ^JsonParser parser m]
      (if (identical? JsonToken/START_OBJECT (.nextToken parser))
        (if-some [primitive-value (get m key)]
          (when-ok [primitive-value (parse-extended-primitive-properties type-handlers parser primitive-value)]
            (assoc m key primitive-value))
          (when-ok [data (parse-extended-primitive-properties type-handlers parser {})]
            (assoc m key (constructor data))))
        (incorrect-token-anom parser JsonToken/START_OBJECT)))
    (fn [type-handlers ^JsonParser parser m]
      (condp identical? (.nextToken parser)
        JsonToken/START_ARRAY
        (loop [l (or (get m key) []) i 0]
          (condp identical? (.nextToken parser)
            JsonToken/START_OBJECT
            (if-some [primitive-value (get l i)]
              (when-ok [primitive-value (parse-extended-primitive-properties type-handlers parser primitive-value)]
                (recur (assoc l i primitive-value) (inc i)))
              (when-ok [data (parse-extended-primitive-properties type-handlers parser {})]
                (recur (assoc l i (constructor data)) (inc i))))
            JsonToken/END_ARRAY (assoc m key l)
            JsonToken/VALUE_NULL
            (recur (update l i identity) (inc i))
            (incorrect-token-anom parser JsonToken/START_OBJECT JsonToken/END_ARRAY JsonToken/VALUE_NULL)))
        JsonToken/START_OBJECT
        (if-some [primitive-value (get m key)]
          (when-ok [primitive-value (parse-extended-primitive-properties type-handlers parser primitive-value)]
            (assoc m key primitive-value))
          (when-ok [data (parse-extended-primitive-properties type-handlers parser {})]
            (assoc m key (constructor data))))
        (incorrect-token-anom parser JsonToken/START_ARRAY JsonToken/START_OBJECT)))))

(defn- primitive-handler
  "Returns a map of two property-handlers, one for the field-name of
  `property-handler-definition` and one for _field-name for handling extended
  primitive data."
  {:arglists '([property-handler-definition constructor value-handler])}
  [{:keys [field-name] :as def} constructor value-handler]
  {field-name value-handler
   (str "_" field-name) (extended-primitive-handler def constructor)})

(defn- get-long [parser]
  (.getLongValue ^JsonParser parser))

(defn- get-decimal [parser]
  (.getDecimalValue ^JsonParser parser))

(defn- primitive-integer-handler
  "A handler that reads an integer value and creates the internal representation
  using `constructor`."
  [def constructor]
  (->> (primitive-value-handler def constructor JsonToken/VALUE_NUMBER_INT get-long)
       (primitive-handler def constructor)))

(defn- primitive-decimal-handler
  "A handler that reads an integer or decimal value and creates the internal
  representation using `constructor`."
  [def constructor]
  (->> (primitive-value-handler def constructor JsonToken/VALUE_NUMBER_INT
                                get-long JsonToken/VALUE_NUMBER_FLOAT get-decimal)
       (primitive-handler def constructor)))

(defn- get-text [p]
  (.getText ^JsonParser p))

(defn- get-text-pattern [pattern]
  (fn [p]
    (let [s (get-text p)]
      (if (.matches (re-matcher pattern s))
        s
        (ba/incorrect (format "Error on value `%s`. Expected type is `string`, regex `%s`." s pattern))))))

(defn- create-system-value
  ([system-constructor]
   (fn [p]
     (when-ok [text (get-text p)]
       (system-constructor text))))
  ([system-constructor pattern]
   (let [get-text (get-text-pattern pattern)]
     (fn [p]
       (when-ok [text (get-text p)]
         (system-constructor text))))))

(defn- primitive-string-handler
  "A handler that reads a string value and creates the internal representation
  using `constructor` and optional `system-parser` and `pattern`.

  The system parser has to be a function from string to system value or anomaly."
  ([def constructor]
   (->> (primitive-value-handler def constructor JsonToken/VALUE_STRING get-text)
        (primitive-handler def constructor)))
  ([def constructor system-parser]
   (->> (primitive-value-handler def constructor JsonToken/VALUE_STRING
                                 (create-system-value system-parser))
        (primitive-handler def constructor)))
  ([def constructor system-parser pattern]
   (->> (primitive-value-handler def constructor JsonToken/VALUE_STRING
                                 (create-system-value system-parser pattern))
        (primitive-handler def constructor))))

(defn- create-complex-property-handler
  "Returns a map of a single JSON property name to a property-handler that
  delegates handling of the property value to the type-handler of the complex
  type of `property-handler-definition`."
  {:arglists '([property-handler-definition])}
  [{:keys [field-name key type cardinality]}]
  {field-name
   (let [type (keyword (name type))]
     (if (= :single cardinality)
       (fn complex-property-handler-cardinality-single [type-handlers ^JsonParser parser m]
         (if-some [handler (type-handlers type)]
           (if (identical? JsonToken/START_OBJECT (.nextToken parser))
             (when-ok [value (handler type-handlers parser)]
               (assoc m key value))
             (incorrect-token-anom parser JsonToken/START_OBJECT))
           (unsupported-type-anom (name type))))
       (fn complex-property-handler-cardinality-many [type-handlers ^JsonParser parser m]
         (if-some [handler (type-handlers type)]
           (condp identical? (.nextToken parser)
             JsonToken/START_ARRAY
             (when-ok [list (parse-complex-list handler type-handlers parser)]
               (assoc m key list))
             JsonToken/START_OBJECT
             (when-ok [value (handler type-handlers parser)]
               (assoc m key [value]))
             (incorrect-token-anom parser JsonToken/START_ARRAY JsonToken/START_OBJECT))
           (unsupported-type-anom (name type))))))})

(defn- create-property-handlers*
  "Returns a map of JSON property names to handlers."
  {:arglists '([property-handler-definition])}
  [{:keys [field-name key type] :as def}]
  (condp = type
    :system/string
    {field-name (create-system-string-handler #(assoc %1 key %2))}

    :primitive/boolean
    (primitive-handler def type/boolean (primitive-boolean-value-handler def))

    :primitive/integer
    (primitive-integer-handler def type/integer)

    :primitive/string
    (primitive-string-handler def type/string identity #"[\r\n\t\u0020-\uFFFF]+")

    :primitive/decimal
    (primitive-decimal-handler def type/decimal)

    :primitive/uri
    (primitive-string-handler def type/uri)

    :primitive/url
    (primitive-string-handler def type/url)

    :primitive/canonical
    (primitive-string-handler def type/canonical)

    :primitive/base64Binary
    (primitive-string-handler def type/base64Binary)

    :primitive/instant
    (primitive-string-handler def (wrap-constructor type/instant))

    :primitive/date
    (primitive-string-handler def type/date system/parse-date)

    :primitive/dateTime
    (primitive-string-handler def type/dateTime system/parse-date-time)

    :primitive/time
    (primitive-string-handler def type/time system/parse-time)

    :primitive/code
    (primitive-string-handler def type/code)

    :primitive/oid
    (primitive-string-handler def type/oid)

    :primitive/id
    (primitive-string-handler def type/id)

    :primitive/markdown
    (primitive-string-handler def type/markdown)

    :primitive/unsignedInt
    (primitive-integer-handler def type/unsignedInt)

    :primitive/positiveInt
    (primitive-integer-handler def type/positiveInt)

    :primitive/uuid
    (primitive-string-handler def type/uuid)

    :primitive/xhtml
    (primitive-string-handler def type/->Xhtml)

    (if (#{"complex" "element" "backboneElement"} (namespace type))
      (create-complex-property-handler def)
      (ba/unsupported (format "unsupported type: %s" (name type))))))

(defn- create-property-handlers
  "Returns a map of JSON property names to handlers."
  [type element-definitions]
  (transduce
   (mapcat (partial property-handler-definitions type))
   (fn
     ([m]
      (let [s (sort-by first (seq m))
            names (object-array (map first s))
            handlers (object-array (map second s))]
        (fn find-property-handler [field-name]
          (let [idx (Arrays/binarySearch names field-name)]
            (when-not (neg? idx)
              (aget handlers idx))))))
     ([handlers property-handler-definition]
      (if-ok [handler (create-property-handlers* property-handler-definition)]
        (into handlers handler)
        reduced)))
   {}
   element-definitions))

(defn- fhir-type-keyword [type]
  (let [parts (cons "fhir" (seq (str/split type #"\.")))]
    (keyword (str/join "." (butlast parts)) (last parts))))

(defn- create-empty-value [type]
  (condp = type
    "Address" (type/map->Address {})
    "Attachment" (type/map->Attachment {})
    "CodeableConcept" (type/map->CodeableConcept {})
    "Coding" (type/map->Coding {})
    "Extension" (type/map->Extension {})
    "HumanName" (type/map->HumanName {})
    "Identifier" (type/map->Identifier {})
    "Period" (type/map->Period {})
    "Quantity" (type/map->Quantity {})
    "Ratio" (type/map->Ratio {})
    "Reference" (type/map->Reference {})
    "Meta" (type/map->Meta {})
    {:fhir/type (fhir-type-keyword type)}))

(defn- create-type-handler
  "Creates a handler for `type` using `element-definitions`.

  The element definitions must not contain nested backbone element definitions.
  Use the `separate-element-definitions` function to separate nested backbone
  element definitions.
  
  A type-handler reads a JSON object. It expects that the `START_OBJECT` token
  is already read and will try to read a `FIELD_NAME` or `END_OBJECT` token. It
  either returns a value of `type` or an anomaly in case of errors."
  [type element-definitions]
  (when-ok [property-handlers (create-property-handlers type element-definitions)]
    (let [empty-value (create-empty-value type)]
      (fn type-handler [type-handlers ^JsonParser parser]
        (loop [m empty-value]
          (condp identical? (.nextToken parser)
            JsonToken/FIELD_NAME
            (if-some [handler (property-handlers (.currentName parser))]
              (recur-ok (handler type-handlers parser m))
              (if (= "resourceType" (.currentName parser))
                (do (.nextToken parser) (recur m))
                (unknown-property-anom (.currentName parser))))
            JsonToken/END_OBJECT m
            (incorrect-token-anom parser JsonToken/FIELD_NAME JsonToken/END_OBJECT)))))))

(defn create-type-handlers
  "Creates a map of keyword type names to type-handlers from the snapshot
  `element-definitions` of a StructureDefinition resource.
  
  Returns an anomaly in case of errors."
  {:arglists '([element-definitions])}
  [[{type :path} & more]]
  (reduce-kv
   (fn [res type element-definitions]
     (if-ok [handler (create-type-handler type element-definitions)]
       (assoc res (keyword type) handler)
       reduced))
   {}
   (separate-element-definitions type more)))

(defn- findResourceType [^JsonNode node]
  (when-let [node (.get node "resourceType")]
    (when (.isTextual node)
      (.asText node))))

(def resource-handler
  (fn [type-handlers ^JsonParser p]
    (let [^JsonNode tree (.readValueAsTree p)]
      (if-let [type (findResourceType tree)]
        (if-let [type-handler (get type-handlers (keyword type))]
          (let [p (TreeTraversingParser. tree (.getCodec p))]
            ;; skip the first token
            (.nextToken p)
            (type-handler type-handlers p))
          (ba/unsupported (format "unsupported resource type: %s" type)))
        (ba/incorrect "Missing property `resourceType`.")))))

(defn- read-value*
  "Uses `handler` to read a complex value from `parser`."
  [type-handlers ^JsonParser parser handler]
  (if (identical? JsonToken/START_OBJECT (.nextToken parser))
    (when-ok [type (handler type-handlers parser)]
      (if-let [token (.nextToken parser)]
        (ba/incorrect (format "incorrect trailing token %s" token))
        type))
    (incorrect-token-anom parser JsonToken/START_OBJECT)))

(defn- prefix-msg [msg]
  (str "Invalid JSON representation of a resource. " msg))

(defn- read-value [type-handlers parser handler]
  (-> (read-value* type-handlers parser handler)
      (ba/exceptionally #(update % ::anom/message prefix-msg))))

(def ^:private stream-read-constraints
  (-> (StreamReadConstraints/builder)
      (.maxStringLength 5e7)
      (.build)))

(def ^:private json-factory
  (-> (JsonFactory/builder)
      (.streamReadConstraints stream-read-constraints)
      (.build)))

(def ^:private ^ObjectMapper json-object-mapper
  (ObjectMapper. json-factory))

(def ^:private ^JsonFactory factory
  (.getFactory json-object-mapper))

(defprotocol ParserFactory
  (-create-parser ^JsonParser [source]))

(extend-protocol ParserFactory
  byte/1
  (-create-parser [source]
    (.createParser factory source))
  InputStream
  (-create-parser [source]
    (.createParser factory source))
  Reader
  (-create-parser [source]
    (.createParser factory source))
  String
  (-create-parser [source]
    (.createParser factory source)))

(defn parse-json
  "Parses a complex value from JSON `source`.

  For resources, the two-arity version can be used. In this case the
  `resourceType` JSON property is used to determine the `type`.
  
  For complex types, the `type` has to be given.
  
  Returns an anomaly in case of errors."
  ([type-handlers source]
   (with-open [parser (-create-parser source)]
     (read-value type-handlers parser resource-handler)))
  ([type-handlers type source]
   (if-some [handler (get type-handlers (keyword type))]
     (with-open [parser (-create-parser source)]
       (read-value type-handlers parser handler))
     (ba/unsupported (format "unsupported resource type: %s" type)))))
