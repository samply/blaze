(ns blaze.fhir.spec.resource
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
   [java.io InputStream Reader]))

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
  (ba/incorrect (format "unknown property `%s`" name)))

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

(defn- element-handler-definitions
  "Takes `element-definition` and returns possibly multiple
  element handler definitions, one for each polymorphic type.

  An element handler definition contains:
   * field-name - the name of the JSON property
   * key - the key of the internal representation
   * element-type - a keyword of the type
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

(defn- incorrect-token-anom [^JsonParser p & expected-tokens]
  (ba/incorrect
   (if (.currentToken p)
     (format "Expected token %s but was token %s at %s"
           (if (= 1 (count expected-tokens))
             (first expected-tokens)
             (format "one of %s" (str/join ", " expected-tokens)))
           (.currentToken p) (.currentLocation p))
     (str "End of input at " (.currentLocation p)))))

(defn- system-string-handler [assoc-fn]
  (fn [_ ^JsonParser p m]
    (if (= JsonToken/VALUE_STRING (.nextToken p))
      (assoc-fn m (type/string (.getText p)))
      (incorrect-token-anom p JsonToken/VALUE_STRING))))

(defn- duplicate-property-anom [field-name]
  (ba/incorrect (format "duplicate property `%s`" field-name)))

(defn- assoc-primitive-value
  "Associates `value` to `m` under `key`.

  In case an extended primitive value exists already, updates that primitive
  value with `value`. Otherwise uses `constructor` to create a new primitive
  value."
  [{:keys [field-name key]} m constructor value]
  (if-some [primitive-value (m key)]
    (if (some? (type/value primitive-value))
      (duplicate-property-anom field-name)
      (assoc m key (type/assoc-value primitive-value value)))
    (let [primitive-value (constructor value)]
      (if (s2/invalid? primitive-value)
        (ba/incorrect (format "Invalid value `%s`." value))
        (assoc m key primitive-value)))))

(defn- assoc-primitive-many-value
  "Like `assoc-primitive-value` but with a single value for cardinality many."
  [{:keys [field-name key]} m constructor value]
  (if-some [primitive-value (first (m key))]
    (if (some? (type/value primitive-value))
      (duplicate-property-anom field-name)
      (assoc m key [(type/assoc-value primitive-value value)]))
    (assoc m key [(constructor value)])))

(defn primitive-boolean-value-handler [def]
  (fn [_ ^JsonParser p m]
    (condp = (.nextToken p)
      JsonToken/VALUE_TRUE (assoc-primitive-value def m type/boolean true)
      JsonToken/VALUE_FALSE (assoc-primitive-value def m type/boolean false)
      (incorrect-token-anom p JsonToken/VALUE_TRUE JsonToken/VALUE_FALSE))))

(defn- primitive-value-handler
  ([{:keys [field-name key cardinality] :as def} constructor token extract-value]
   (if (= :single cardinality)
     (fn [_ ^JsonParser p m]
       (if (= token (.nextToken p))
         (when-ok [value (extract-value p)]
           (assoc-primitive-value def m constructor value))
         (incorrect-token-anom p token)))
     (fn [_ ^JsonParser p m]
       (condp = (.nextToken p)
         JsonToken/START_ARRAY
         (loop [l (m key []) i 0]
           (condp = (.nextToken p)
             token
             (when-ok [value (extract-value p)]
               (if-some [primitive-value (get l i)]
                 (if (some? (type/value primitive-value))
                   (duplicate-property-anom field-name)
                   (recur (update l i type/assoc-value value) (inc i)))
                 (recur (assoc l i (constructor value)) (inc i))))
             JsonToken/END_ARRAY (assoc m key l)
             JsonToken/VALUE_NULL
             (recur (update l i identity) (inc i))
             (incorrect-token-anom p token JsonToken/END_ARRAY JsonToken/VALUE_NULL)))
         token
         (when-ok [value (extract-value p)]
           (assoc-primitive-many-value def m constructor value))
         (incorrect-token-anom p JsonToken/START_ARRAY token)))))
  ([{:keys [field-name key cardinality] :as def} constructor token-1
    extract-value-1 token-2 extract-value-2]
   (if (= :single cardinality)
     (fn [_ ^JsonParser p m]
       (condp = (.nextToken p)
         token-1
         (when-ok [value (extract-value-1 p)]
           (assoc-primitive-value def m constructor value))
         token-2
         (when-ok [value (extract-value-2 p)]
           (assoc-primitive-value def m constructor value))
         (incorrect-token-anom p token-1 token-2)))
     (fn [_ ^JsonParser p m]
       (condp = (.nextToken p)
         JsonToken/START_ARRAY
         (loop [l (m key []) i 0]
           (condp = (.nextToken p)
             token-1
             (when-ok [value (extract-value-1 p)]
               (if-some [primitive-value (get l i)]
                 (if (some? (type/value primitive-value))
                   (duplicate-property-anom field-name)
                   (recur (update l i type/assoc-value value) (inc i)))
                 (recur (conj l (constructor value)) (inc i))))
             token-2
             (when-ok [value (extract-value-2 p)]
               (if-some [primitive-value (get l i)]
                 (if (some? (type/value primitive-value))
                   (duplicate-property-anom field-name)
                   (recur (update l i type/assoc-value value) (inc i)))
                 (recur (conj l (constructor value)) (inc i))))
             JsonToken/END_ARRAY (assoc m key l)
             JsonToken/VALUE_NULL
             (recur (update l i identity) (inc i))
             (incorrect-token-anom p token-1 token-2 JsonToken/END_ARRAY JsonToken/VALUE_NULL)))
         token-1
         (when-ok [value (extract-value-1 p)]
           (assoc-primitive-many-value def m constructor value))
         token-2
         (when-ok [value (extract-value-2 p)]
           (assoc-primitive-many-value def m constructor value))
         (incorrect-token-anom p JsonToken/START_ARRAY token-1 token-2))))))

(defmacro recur-ok [expr-form]
  `(when-ok [r# ~expr-form]
     (recur r#)))

(def ^:private primitive-id-handler
  (system-string-handler type/assoc-id))

(defn- parse-complex-list [handler type-handlers ^JsonParser p]
  (loop [l (transient [])]
    (condp = (.nextToken p)
      JsonToken/START_OBJECT
      (when-ok [v (handler type-handlers p)]
        (recur (conj! l v)))
      JsonToken/END_ARRAY (persistent! l)
      (incorrect-token-anom p JsonToken/START_OBJECT JsonToken/END_ARRAY))))

(defn- parse-extended-primitive-properties [type-handlers ^JsonParser p data]
  (loop [data data]
    (condp = (.nextToken p)
      JsonToken/FIELD_NAME
      (condp = (.currentName p)
        "id" (recur-ok (primitive-id-handler type-handlers p data))
        "extension"
        (if-let [extension-handler (get type-handlers :Extension)]
          (condp = (.nextToken p)
            JsonToken/START_ARRAY
            (when-ok [list (parse-complex-list extension-handler type-handlers p)]
              (recur (type/assoc-extension data list)))
            JsonToken/START_OBJECT
            (when-ok [extension (extension-handler type-handlers p)]
              (recur (type/assoc-extension data [extension])))
            (incorrect-token-anom p JsonToken/START_ARRAY JsonToken/START_OBJECT))
          (ba/unsupported "unsupported type: Extension"))
        (unknown-property-anom (.currentName p)))
      JsonToken/END_OBJECT data
      (incorrect-token-anom p JsonToken/FIELD_NAME JsonToken/END_OBJECT))))

(defn- extended-primitive-handler [{:keys [key cardinality]} constructor]
  (if (= :single cardinality)
    (fn [type-handlers ^JsonParser p m]
      (if (= JsonToken/START_OBJECT (.nextToken p))
        (if-some [primitive-value (m key)]
          (when-ok [primitive-value (parse-extended-primitive-properties type-handlers p primitive-value)]
            (assoc m key primitive-value))
          (when-ok [data (parse-extended-primitive-properties type-handlers p {})]
            (assoc m key (constructor data))))
        (incorrect-token-anom p JsonToken/START_OBJECT)))
    (fn [type-handlers ^JsonParser p m]
      (condp = (.nextToken p)
        JsonToken/START_ARRAY
        (loop [l (m key []) i 0]
          (condp = (.nextToken p)
            JsonToken/START_OBJECT
            (if-some [primitive-value (get l i)]
              (when-ok [primitive-value (parse-extended-primitive-properties type-handlers p primitive-value)]
                (recur (assoc l i primitive-value) (inc i)))
              (when-ok [data (parse-extended-primitive-properties type-handlers p {})]
                (recur (assoc l i (constructor data)) (inc i))))
            JsonToken/END_ARRAY (assoc m key l)
            JsonToken/VALUE_NULL
            (recur (update l i identity) (inc i))
            (incorrect-token-anom p JsonToken/START_OBJECT JsonToken/END_ARRAY JsonToken/VALUE_NULL)))
        JsonToken/START_OBJECT
        (if-some [primitive-value (m key)]
          (when-ok [primitive-value (parse-extended-primitive-properties type-handlers p primitive-value)]
            (assoc m key primitive-value))
          (when-ok [data (parse-extended-primitive-properties type-handlers p {})]
            (assoc m key (constructor data))))
        (incorrect-token-anom p JsonToken/START_ARRAY JsonToken/START_OBJECT)))))

(defn- primitive-handler [{:keys [field-name] :as def} constructor value-handler]
  {field-name value-handler
   (str "_" field-name) (extended-primitive-handler def constructor)})

(defn- get-long [p]
  (.getLongValue ^JsonParser p))

(defn- get-decimal [p]
  (.getDecimalValue ^JsonParser p))

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
   (primitive-handler def constructor (primitive-value-handler def constructor JsonToken/VALUE_STRING get-text)))
  ([def constructor system-parser]
   (primitive-handler def constructor (primitive-value-handler def constructor JsonToken/VALUE_STRING (create-system-value system-parser))))
  ([def constructor system-parser pattern]
   (primitive-handler def constructor (primitive-value-handler def constructor JsonToken/VALUE_STRING (create-system-value system-parser pattern)))))

(defn- complex-handler [{:keys [field-name key type cardinality]}]
  {field-name
   (let [type-name (keyword (name type))]
     (if (= :single cardinality)
       (fn [type-handlers ^JsonParser p m]
         (if-let [handler (get type-handlers type-name)]
           (if (= JsonToken/START_OBJECT (.nextToken p))
             (when-ok [value (handler type-handlers p)]
               (assoc m key value))
             (incorrect-token-anom p JsonToken/START_OBJECT))
           (ba/unsupported (format "unsupported type: %s" (name type-name)))))
       (fn [type-handlers ^JsonParser p m]
         (if-let [handler (get type-handlers type-name)]
           (condp = (.nextToken p)
             JsonToken/START_ARRAY
             (when-ok [list (parse-complex-list handler type-handlers p)]
               (assoc m key list))
             JsonToken/START_OBJECT
             (when-ok [value (handler type-handlers p)]
               (assoc m key [value]))
             (incorrect-token-anom p JsonToken/START_ARRAY JsonToken/START_OBJECT))
           (ba/unsupported (format "unsupported type: %s" (name type-name)))))))})

(defn- create-element-handler
  "Creates a parsing handler for a FHIR data element.

  An element handler is a function taking a parser and a map and returns either
  the map with the added content or an anomaly."
  [{:keys [field-name key type] :as def}]
  (condp = type
    :system/string
    {field-name (system-string-handler #(assoc %1 key %2))}

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
    (primitive-string-handler def type/instant)

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
      (complex-handler def)
      (ba/unsupported (format "unsupported type: %s" (name type))))))

(defn- create-element-handlers
  "Returns a map of field names to preliminary handlers."
  [type element-definitions]
  (transduce
   (mapcat (partial element-handler-definitions type))
   (completing
    (fn [handlers def]
      (if-ok [handler (create-element-handler def)]
        (into handlers handler)
        reduced)))
   {}
   element-definitions))

(defn- fhir-type-keyword [type]
  (let [parts (cons "fhir" (seq (str/split type #"\.")))]
    (keyword (str/join "." (butlast parts)) (last parts))))

(defn- finalizer [type]
  (condp = type
    "Address" type/map->Address
    "Attachment" type/map->Attachment
    "CodeableConcept" type/map->CodeableConcept
    "Coding" type/map->Coding
    "Extension" type/map->Extension
    "HumanName" type/map->HumanName
    "Identifier" type/map->Identifier
    "Period" type/map->Period
    "Quantity" type/map->Quantity
    "Ratio" type/map->Ratio
    "Reference" type/map->Reference
    "Meta" type/map->Meta
    #(assoc % :fhir/type (fhir-type-keyword type))))

(defn- create-type-handler
  "Creates a handler for `type` using `element-definitions`.

  The element definitions must not contain nested backbone element definitions.
  Use the `separate-element-definitions` function to separate nested backbone
  element definitions."
  [type element-definitions]
  (when-ok [element-handlers (create-element-handlers type element-definitions)]
    (let [finalizer (finalizer type)]
      (fn [type-handlers ^JsonParser p]
        (loop [m {}]
          (condp = (.nextToken p)
            JsonToken/FIELD_NAME
            (if-let [handler (element-handlers (.currentName p))]
              (recur-ok (handler type-handlers p m))
              (if (= "resourceType" (.currentName p))
                (do (.nextToken p) (recur m))
                (unknown-property-anom (.currentName p))))
            JsonToken/END_OBJECT (finalizer m)
            (incorrect-token-anom p JsonToken/FIELD_NAME JsonToken/END_OBJECT)))))))

(defn create-handlers
  "Creates a map of type names to type handlers from the `element-definitions`
  of a type."
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
  (-create-parser [source]))

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

(defn- read-type* [handler type-handlers ^JsonParser p]
  (if (= JsonToken/START_OBJECT (.nextToken p))
    (when-ok [type (handler type-handlers p)]
      (if-let [token (.nextToken p)]
        (ba/incorrect (format "incorrect trailing token %s" token))
        type))
    (incorrect-token-anom p JsonToken/START_OBJECT)))

(defn- prefix-msg [msg]
  (str "Invalid JSON representation of a resource. " msg))

(defn- read-type [handler type-handlers p]
  (-> (read-type* handler type-handlers p)
      (ba/exceptionally #(update % ::anom/message prefix-msg))))

(defn parse-json
  ([type-handlers source]
   (with-open [^JsonParser p (-create-parser source)]
     (read-type resource-handler type-handlers p)))
  ([type-handlers type source]
   (if-let [handler (get type-handlers (keyword type))]
     (with-open [^JsonParser p (-create-parser source)]
       (read-type handler type-handlers p))
     (ba/unsupported (format "unsupported resource type: %s" type)))))
