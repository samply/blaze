(ns blaze.fhir.spec.resource
  "JSON Parsing.

  Use `create-type-handlers` to create a map of type-handlers that has to be
  given to `parse-json` in order to parse JSON from a source.

  A locator is a vector of path segments already parsed in order to report the
  location of an error. Path segments are either strings of field names or
  indices of arrays.

  A type-handler in this namespace is a function of two arities. On arity-0 the
  function returns the name of the type as string. On arity-3 it takes a map of
  all type handlers, a parser and a locator and returns either a FHIR value or
  an anomaly in case of errors.

  A property-handler in this namespace is a function taking a map of all type
  handlers, a parser, a locator and a partially constructed FHIR value and
  returns either the FHIR value with data added or an anomaly in case of errors."
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.string-util :as su]
   [blaze.fhir.spec.type.system :as system]
   [clojure.alpha.spec :as s2]
   [clojure.string :as str]
   [cognitect.anomalies :as anom])
  (:import
   [com.fasterxml.jackson.core JsonFactory JsonParseException JsonParser JsonToken StreamReadConstraints]
   [com.fasterxml.jackson.core.io JsonEOFException]
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

(defn- find-fhir-type [{:keys [extension]}]
  (some
   #(when (= "http://hl7.org/fhir/StructureDefinition/structuredefinition-fhir-type" (:url %))
      (:valueUrl %))
   extension))

(defn- prepare-element-type [{:keys [code] :as type} path]
  (condp = code
    "http://hl7.org/fhirpath/System.String"
    (if (= "uri" (find-fhir-type type))
      :system.string/uri
      :system/string)
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

(defmacro current-token [parser]
  `(.currentToken ~(with-meta parser {:tag `JsonParser})))

(defn- get-current-value [parser]
  (condp identical? (current-token parser)
    JsonToken/VALUE_NULL "value null"
    JsonToken/VALUE_TRUE "boolean value true"
    JsonToken/VALUE_FALSE "boolean value false"
    JsonToken/VALUE_STRING (format "value `%s`" (.getText ^JsonParser parser))
    JsonToken/VALUE_NUMBER_INT (format "integer value %d" (.getLongValue ^JsonParser parser))
    JsonToken/VALUE_NUMBER_FLOAT (format "float value %s" (.getDecimalValue ^JsonParser parser))
    JsonToken/START_OBJECT "object start"
    JsonToken/START_ARRAY "array start"
    (format "token %s" (current-token parser))))

(defn- expression [locator]
  (loop [sb (StringBuilder. (str (first locator)))
         more (next locator)]
    (if more
      (if (string? (first more))
        (recur (-> sb (.append ".") (.append (first more))) (next more))
        (recur (-> sb (.append "[") (.append (str (first more))) (.append "]")) (next more)))
      (str sb))))

(defn- fhir-issue [msg locator]
  {:fhir.issues/code "invariant"
   :fhir.issues/diagnostics msg
   :fhir.issues/expression (expression locator)})

(defn- unexpected-end-of-input-msg [^JsonEOFException e]
  (condp identical? (.getTokenBeingDecoded e)
    JsonToken/FIELD_NAME "Unexpected end of input while parsing a field name."
    "Unexpected end of input."))

(defn- next-token [parser locator]
  (try
    (.nextToken ^JsonParser parser)
    (catch JsonEOFException e
      (let [msg (unexpected-end-of-input-msg e)]
        (ba/incorrect msg :fhir/issues [(fhir-issue msg locator)])))
    (catch JsonParseException _
      (let [msg "JSON parsing error."]
        (ba/incorrect msg :fhir/issues [(fhir-issue msg locator)])))))

(defmacro current-location [parser]
  `(.currentLocation ~(with-meta parser {:tag `JsonParser})))

(defmacro current-name [parser]
  `(.currentName ~(with-meta parser {:tag `JsonParser})))

(defn- get-text [parser locator]
  (try
    (.getText ^JsonParser parser)
    (catch JsonEOFException _
      (let [msg "Unexpected end of input while reading a string value."]
        (ba/incorrect msg :fhir/issues [(fhir-issue msg locator)])))))

(defn- get-long [parser _locator]
  (.getLongValue ^JsonParser parser))

(defn- get-decimal [parser _locator]
  (.getDecimalValue ^JsonParser parser))

(defn- incorrect-token-anom [parser & expected-tokens]
  (ba/incorrect
   (if (current-token parser)
     (format "Expected token %s but was token %s at %s"
             (if (= 1 (count expected-tokens))
               (first expected-tokens)
               (format "one of %s" (str/join ", " expected-tokens)))
             (current-token parser) (current-location parser))
     (str "End of input at " (current-location parser)))))

(defn- incorrect-value-anom [parser locator expected-type]
  (let [msg (format "Error on %s. Expected type is `%s`." (get-current-value parser) expected-type)]
    (ba/incorrect msg :fhir/issues [(fhir-issue msg locator)])))

(defn- unknown-property-anom [locator name]
  (let [msg (format "Unknown property `%s`." name)]
    (ba/incorrect msg :fhir/issues [(fhir-issue msg locator)])))

(defmacro cond-next-token [parser locator & body]
  `(when-ok [token# (next-token ~parser ~locator)]
     (condp identical? token#
       ~@body)))

(defn- create-system-string-handler [assoc-fn path expected-type]
  (fn system-string-handler [_ parser locator m]
    (cond-next-token parser locator
      JsonToken/VALUE_STRING (assoc-fn m (type/string (.getText ^JsonParser parser)))
      (incorrect-value-anom parser (conj locator path) expected-type))))

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
  (fn [_ parser locator m]
    (cond-next-token parser locator
      JsonToken/VALUE_TRUE (assoc-primitive-value field-name key m type/boolean true)
      JsonToken/VALUE_FALSE (assoc-primitive-value field-name key m type/boolean false)
      (incorrect-value-anom parser (conj locator (name key)) "boolean"))))

(defn- primitive-value-handler
  "Returns a property-handler."
  {:arglists '([property-handler-definition constructor token extract-value expected-type])}
  ([{:keys [field-name key cardinality] :as def} constructor token extract-value expected-type]
   (let [path (name key)]
     (if (= :single cardinality)
       (fn primitive-property-handler-one-token-cardinality-single [_ parser locator m]
         (cond-next-token parser locator
           token
           (when-ok [value (extract-value parser (conj locator path))]
             (assoc-primitive-value field-name key m constructor value))
           (incorrect-value-anom parser (conj locator path) expected-type)))
       (fn primitive-property-handler-one-token-cardinality-many [_ parser locator m]
         (cond-next-token parser locator
           JsonToken/START_ARRAY
           (loop [l (or (get m key) []) i 0]
             (when-ok [t (next-token parser locator)]
               (condp identical? t
                 token
                 (when-ok [value (extract-value parser (conj locator path))]
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
                                       JsonToken/VALUE_NULL))))
           token
           (when-ok [value (extract-value parser (conj locator path))]
             (assoc-primitive-many-value def m constructor value))
           (incorrect-value-anom parser (conj locator path) (str expected-type "[]")))))))
  ([{:keys [field-name key cardinality] :as def} constructor token-1
    extract-value-1 token-2 extract-value-2 expected-type]
   (let [path (name key)]
     (if (= :single cardinality)
       (fn primitive-property-handler-two-tokens-cardinality-single [_ parser locator m]
         (cond-next-token parser locator
           token-1
           (when-ok [value (extract-value-1 parser (conj locator path))]
             (assoc-primitive-value field-name key m constructor value))
           token-2
           (when-ok [value (extract-value-2 parser (conj locator path))]
             (assoc-primitive-value field-name key m constructor value))
           (incorrect-value-anom parser (conj locator path) expected-type)))
       (fn primitive-property-handler-two-tokens-cardinality-many [_ parser locator m]
         (cond-next-token parser locator
           JsonToken/START_ARRAY
           (loop [l (or (get m key) []) i 0]
             (when-ok [t (next-token parser locator)]
               (condp identical? t
                 token-1
                 (when-ok [value (extract-value-1 parser (conj locator path))]
                   (if-some [primitive-value (get l i)]
                     (if (some? (type/value primitive-value))
                       (duplicate-property-anom field-name)
                       (recur (update l i type/assoc-value value) (inc i)))
                     (when-ok [primitive-value (constructor value)]
                       (recur (assoc l i primitive-value) (inc i)))))
                 token-2
                 (when-ok [value (extract-value-2 parser (conj locator path))]
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
                                       JsonToken/VALUE_NULL))))
           token-1
           (when-ok [value (extract-value-1 parser (conj locator path))]
             (assoc-primitive-many-value def m constructor value))
           token-2
           (when-ok [value (extract-value-2 parser (conj locator path))]
             (assoc-primitive-many-value def m constructor value))
           (incorrect-token-anom parser JsonToken/START_ARRAY token-1 token-2)))))))

(defmacro recur-ok [expr-form]
  `(when-ok [r# ~expr-form]
     (recur r#)))

(def ^:private primitive-id-handler
  (create-system-string-handler type/assoc-id "id" "string"))

(defn- parse-complex-list [handler type-handlers parser locator]
  (loop [list (transient [])]
    (cond-next-token parser locator
      JsonToken/START_OBJECT
      (when-ok [value (handler type-handlers parser (conj locator (count list)))]
        (recur (conj! list value)))
      JsonToken/END_ARRAY (persistent! list)
      (incorrect-token-anom parser JsonToken/START_OBJECT JsonToken/END_ARRAY))))

(defn- unsupported-type-anom [type]
  (ba/unsupported (format "Unsupported type `%s`." type)))

(defn- parse-extended-primitive-properties
  [type-handlers parser locator data]
  (loop [data data]
    (cond-next-token parser locator
      JsonToken/FIELD_NAME
      (condp = (current-name parser)
        "id" (recur-ok (primitive-id-handler type-handlers parser locator data))
        "extension"
        (if-let [extension-handler (get type-handlers :Extension)]
          (cond-next-token parser locator
            JsonToken/START_ARRAY
            (when-ok [list (parse-complex-list extension-handler type-handlers parser (conj locator "extension"))]
              (recur (type/assoc-extension data list)))
            JsonToken/START_OBJECT
            (when-ok [extension (extension-handler type-handlers parser (conj locator "extension" 0))]
              (recur (type/assoc-extension data [extension])))
            (incorrect-token-anom parser JsonToken/START_ARRAY JsonToken/START_OBJECT))
          (unsupported-type-anom "Extension"))
        (unknown-property-anom locator (current-name parser)))
      JsonToken/END_OBJECT data
      (incorrect-token-anom parser JsonToken/FIELD_NAME JsonToken/END_OBJECT))))

(defn- extended-primitive-handler
  "Returns a property-handler."
  [{:keys [key cardinality]} constructor]
  (if (= :single cardinality)
    (let [path (name key)]
      (fn [type-handlers parser locator m]
        (cond-next-token parser locator
          JsonToken/START_OBJECT
          (if-some [primitive-value (get m key)]
            (when-ok [primitive-value (parse-extended-primitive-properties type-handlers parser locator primitive-value)]
              (assoc m key primitive-value))
            (when-ok [data (parse-extended-primitive-properties type-handlers parser (conj locator path) {})]
              (assoc m key (constructor data))))
          (incorrect-token-anom parser JsonToken/START_OBJECT))))
    (fn [type-handlers parser locator m]
      (cond-next-token parser locator
        JsonToken/START_ARRAY
        (loop [l (or (get m key) []) i 0]
          (when-ok [t (next-token parser locator)]
            (condp identical? t
              JsonToken/START_OBJECT
              (if-some [primitive-value (get l i)]
                (when-ok [primitive-value (parse-extended-primitive-properties type-handlers parser locator primitive-value)]
                  (recur (assoc l i primitive-value) (inc i)))
                (when-ok [data (parse-extended-primitive-properties type-handlers parser locator {})]
                  (recur (assoc l i (constructor data)) (inc i))))
              JsonToken/END_ARRAY (assoc m key l)
              JsonToken/VALUE_NULL
              (recur (update l i identity) (inc i))
              (incorrect-token-anom parser JsonToken/START_OBJECT JsonToken/END_ARRAY JsonToken/VALUE_NULL))))
        JsonToken/START_OBJECT
        (if-some [primitive-value (get m key)]
          (when-ok [primitive-value (parse-extended-primitive-properties type-handlers parser locator primitive-value)]
            (assoc m key primitive-value))
          (when-ok [data (parse-extended-primitive-properties type-handlers parser locator {})]
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

(defn- primitive-integer-handler
  "A handler that reads an integer value and creates the internal representation
  using `constructor`."
  [def constructor]
  (->> (primitive-value-handler def constructor JsonToken/VALUE_NUMBER_INT
                                get-long "integer")
       (primitive-handler def constructor)))

(defn- primitive-decimal-handler
  "A handler that reads an integer or decimal value and creates the internal
  representation using `constructor`."
  [def constructor]
  (->> (primitive-value-handler def constructor JsonToken/VALUE_NUMBER_INT
                                get-long JsonToken/VALUE_NUMBER_FLOAT get-decimal
                                "decimal")
       (primitive-handler def constructor)))

(defn- get-text-pattern [pattern]
  (fn [parser locator expected-type]
    (when-ok [s (get-text parser locator)]
      (if (.matches (re-matcher pattern s))
        s
        (incorrect-value-anom parser locator (format "%s, regex %s" expected-type pattern))))))

(defn- create-system-value
  ([system-constructor expected-type]
   (fn [parser locator]
     (when-ok [text (get-text parser locator)]
       (-> (system-constructor text)
           (ba/exceptionally
            (fn [_]
              (incorrect-value-anom parser locator expected-type)))))))
  ([system-constructor expected-type pattern]
   (let [get-text (get-text-pattern pattern)]
     (fn [parser locator]
       (when-ok [text (get-text parser locator expected-type)]
         (system-constructor text))))))

(defn- primitive-string-handler
  "A handler that reads a string value and creates the internal representation
  using `constructor` and optional `system-parser` and `pattern`.

  The system parser has to be a function from string to system value or anomaly."
  ([def constructor expected-type]
   (->> (primitive-value-handler def constructor JsonToken/VALUE_STRING get-text
                                 expected-type)
        (primitive-handler def constructor)))
  ([def constructor system-parser expected-type]
   (->> (primitive-value-handler def constructor JsonToken/VALUE_STRING
                                 (create-system-value system-parser expected-type)
                                 expected-type)
        (primitive-handler def constructor)))
  ([def constructor system-parser expected-type pattern]
   (->> (primitive-value-handler def constructor JsonToken/VALUE_STRING
                                 (create-system-value system-parser expected-type pattern)
                                 expected-type)
        (primitive-handler def constructor))))

(defn- create-complex-property-handler
  "Returns a map of a single JSON property name to a property-handler that
  delegates handling of the property value to the type-handler of the complex
  type of `property-handler-definition`."
  {:arglists '([property-handler-definition])}
  [{:keys [field-name key type cardinality]}]
  {field-name
   (let [type-name (if (= "backboneElement" (namespace type))
                     "BackboneElement"
                     (name type))
         type (keyword (name type))
         path (name key)]
     (if (= :single cardinality)
       (fn complex-property-handler-cardinality-single [type-handlers parser locator m]
         (if-some [handler (type-handlers type)]
           (cond-next-token parser locator
             JsonToken/START_OBJECT
             (when-ok [value (handler type-handlers parser (conj locator path))]
               (assoc m key value))
             (incorrect-value-anom parser (conj locator path) type-name))
           (unsupported-type-anom (name type))))
       (fn complex-property-handler-cardinality-many [type-handlers parser locator m]
         (if-some [handler (type-handlers type)]
           (cond-next-token parser locator
             JsonToken/START_ARRAY
             (when-ok [list (parse-complex-list handler type-handlers parser (conj locator path))]
               (assoc m key list))
             JsonToken/START_OBJECT
             (when-ok [value (handler type-handlers parser (conj locator path 0))]
               (assoc m key [value]))
             (incorrect-value-anom parser (conj locator path) type-name))
           (unsupported-type-anom (name type))))))})

(defn- create-property-handlers*
  "Returns a map of JSON property names to handlers."
  {:arglists '([property-handler-definition])}
  [{:keys [field-name key type] :as def}]
  (condp = type
    :system/string
    {field-name (create-system-string-handler #(assoc %1 key %2) (name key) "string")}

    :system.string/uri
    {field-name (create-system-string-handler #(assoc %1 key %2) (name key) "uri")}

    :primitive/boolean
    (primitive-handler def type/boolean (primitive-boolean-value-handler def))

    :primitive/integer
    (primitive-integer-handler def type/integer)

    :primitive/string
    (primitive-string-handler def type/string identity "string" #"[\r\n\t\u0020-\uFFFF]+")

    :primitive/decimal
    (primitive-decimal-handler def type/decimal)

    :primitive/uri
    (primitive-string-handler def type/uri "uri")

    :primitive/url
    (primitive-string-handler def type/url "url")

    :primitive/canonical
    (primitive-string-handler def type/canonical "canonical")

    :primitive/base64Binary
    (primitive-string-handler def type/base64Binary "base64Binary")

    :primitive/instant
    (primitive-string-handler def (wrap-constructor type/instant) "instant")

    :primitive/date
    (primitive-string-handler def type/date system/parse-date "date")

    :primitive/dateTime
    (primitive-string-handler def type/dateTime system/parse-date-time "date-time")

    :primitive/time
    (primitive-string-handler def type/time system/parse-time "time")

    :primitive/code
    (primitive-string-handler def type/code identity "code" #"[\u0021-\uFFFF]+([ \t\n\r][\u0021-\uFFFF]+)*")

    :primitive/oid
    (primitive-string-handler def type/oid "oid")

    :primitive/id
    (primitive-string-handler def type/id "id")

    :primitive/markdown
    (primitive-string-handler def type/markdown "markdown")

    :primitive/unsignedInt
    (primitive-integer-handler def type/unsignedInt)

    :primitive/positiveInt
    (primitive-integer-handler def type/positiveInt)

    :primitive/uuid
    (primitive-string-handler def type/uuid "uuid")

    :primitive/xhtml
    (primitive-string-handler def type/->Xhtml "xhtml")

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
      (fn type-handler
        ([] type)
        ([type-handlers parser locator]
         (loop [value empty-value]
           (cond-next-token parser locator
             JsonToken/FIELD_NAME
             (let [field-name (current-name parser)]
               (if-some [handler (property-handlers field-name)]
                 (recur-ok (handler type-handlers parser locator value))
                 (if (= "resourceType" field-name)
                   (when-ok [_ (next-token parser locator)] (recur value))
                   (unknown-property-anom locator field-name))))
             JsonToken/END_OBJECT value
             (incorrect-token-anom parser JsonToken/FIELD_NAME JsonToken/END_OBJECT))))))))

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
  "A special type-handler that works for all resources. It first reads the
  `resourceType` property and delegates the handling to the corresponding
  type-handler."
  (fn
    ([] "Resource")
    ([type-handlers ^JsonParser parser locator]
     (let [^JsonNode tree (.readValueAsTree parser)]
       (if-let [type (findResourceType tree)]
         (if-let [type-handler (get type-handlers (keyword type))]
           (let [parser (TreeTraversingParser. tree (.getCodec parser))]
             ;; skip the first token
             (next-token parser locator)
             (type-handler type-handlers parser (if (empty? locator) [type] locator)))
           (ba/unsupported (format "Unsupported resource type: %s" type)))
         (let [msg "Missing property `resourceType`."]
           (ba/incorrect msg :fhir/issues [(fhir-issue msg locator)])))))))

(defn- read-value* [type-handlers parser locator handler]
  (cond-next-token parser locator
    JsonToken/START_OBJECT
    (when-ok [type (handler type-handlers parser locator)
              token (next-token parser locator)]
      (if token
        (ba/incorrect (format "incorrect trailing token %s" token))
        type))
    (incorrect-value-anom parser locator (handler))))

(defn- prefix-msg [msg]
  (str "Invalid JSON representation of a resource. " msg))

(defn- read-value
  "Reads a complex value from `parser` using `handler`.

  The handler will determine the type of the value."
  [type-handlers parser locator handler]
  (-> (read-value* type-handlers parser locator handler)
      (ba/exceptionally #(update % ::anom/message prefix-msg))))

(def ^:private stream-read-constraints
  (-> (StreamReadConstraints/builder)
      (.maxStringLength Integer/MAX_VALUE)
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
  InputStream
  (-create-parser [source]
    (.createParser factory source))
  Reader
  (-create-parser [source]
    (.createParser factory source))
  String
  (-create-parser [source]
    (.createParser factory source)))

;; TODO: use bytes/1 in extend-protocol if Clojure 1.12 can be used by Cloverage
#_:clj-kondo/ignore
(extend-type (Class/forName "[B")
  ParserFactory
  (-create-parser [source] (.createParser factory ^bytes source)))

(defn parse-json
  "Parses a complex value from JSON `source`.

  For resources, the two-arity version can be used. In this case the
  `resourceType` JSON property is used to determine the `type`.
  
  For complex types, the `type` has to be given.
  
  Returns an anomaly in case of errors."
  ([type-handlers source]
   (with-open [parser (-create-parser source)]
     (read-value type-handlers parser [] resource-handler)))
  ([type-handlers type source]
   (if-some [handler (get type-handlers (keyword type))]
     (with-open [parser (-create-parser source)]
       (read-value type-handlers parser [type] handler))
     (ba/unsupported (format "Unsupported resource type: %s" type)))))
