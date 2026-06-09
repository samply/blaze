(ns blaze.fhir.writing-context
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec.resource :as res]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.string-util :as su]
   [blaze.fhir.structure-definition-repo :as sdr]
   [blaze.fhir.structure-definition-repo.spec]
   [blaze.fhir.writing-context.spec]
   [blaze.module :as m]
   [blaze.util :refer [str]]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [integrant.core :as ig]
   [taoensso.timbre :as log])
  (:import
   [blaze ReducibleArray]
   [blaze.fhir.spec.type Base Complex FieldName Primitive Xhtml]
   [clojure.data.xml.node Element]
   [com.fasterxml.jackson.core JsonGenerator SerializableString]
   [java.io Writer]))

(set! *warn-on-reflection* true)

(def ^:const fhir-namespace "http://hl7.org/fhir")

(defn- fhir-type-keyword [type]
  (let [parts (cons "fhir" (seq (str/split type #"\.")))]
    (keyword (str/join "." (butlast parts)) (last parts))))

(deftype PropertyHandler [key field-name polymorphic type])

(defn- polymorphic-field-names [base-field-name element-types]
  (into
   {}
   (map
    (fn [{:keys [code]}]
      [(keyword "fhir" code)
       (FieldName/of (str base-field-name (su/capital code)))]))
   element-types))

(defn- property-handler-definitions
  "Takes `element-definition` and returns possibly multiple
  property handler definitions, one for each polymorphic type.

  An element handler definition contains:
   * field-name - the name of the JSON property
   * polymorphic - true/false"
  {:arglists '([parent-type element-definition])}
  [parent-type
   {:keys [path] content-reference :contentReference element-types :type}]
  (if content-reference
    (let [base-field-name (res/base-field-name parent-type path false)
          field-name (FieldName/of base-field-name)]
      (PropertyHandler.
       (keyword base-field-name)
       (fn [_type] field-name)
       false
       (fhir-type-keyword (subs content-reference 1))))
    (let [polymorphic (< 1 (count element-types))
          first-type-code (:code (first element-types))
          element-type (and (= 1 (count element-types))
                            (#{"BackboneElement" "Element"} first-type-code))
          complex-type (and (= 1 (count element-types))
                            (Character/isUpperCase ^char (first first-type-code))
                            (not (#{"BackboneElement" "Element" "Resource"} first-type-code)))
          base-field-name (res/base-field-name parent-type path polymorphic)]
      (PropertyHandler.
       (keyword base-field-name)
       (if polymorphic
         (polymorphic-field-names base-field-name element-types)
         (let [field-name (FieldName/of base-field-name)]
           (fn [_type] field-name)))
       polymorphic
       (if complex-type
         (keyword "fhir" first-type-code)
         (if element-type
           (fhir-type-keyword path)
           (when (= "http://hl7.org/fhirpath/System.String" first-type-code)
             :system/string)))))))

(defn- create-property-handlers
  "Returns a map of JSON property names to property handlers."
  [type element-definitions]
  (ReducibleArray. (map (partial property-handler-definitions type) element-definitions)))

(defn- field-name ^FieldName [^PropertyHandler property-handler type]
  ((.-field-name property-handler) type))

(defn- write-system-string-field [^JsonGenerator generator ^SerializableString field-name value]
  (.writeFieldName generator field-name)
  (.writeString generator ^String value))

(defn- write-field!
  [type-handlers ^JsonGenerator gen ^PropertyHandler property-handler value]
  (if (sequential? value)
    (when-some [first-value (first value)]
      (when-some [type (or (.-type property-handler) (:fhir/type first-value))]
        (if-some [handler (type-handlers type)]
          (do (.writeFieldName gen (.normal (field-name property-handler type)))
              (.writeStartArray gen)
              (run! #(handler type-handlers gen %) value)
              (.writeEndArray gen))
          (Primitive/serializeJsonPrimitiveList value gen (field-name property-handler type)))))
    (if-some [type (or (.-type property-handler) (:fhir/type value))]
      (if-some [handler (type-handlers type)]
        (do (.writeFieldName gen (.normal (field-name property-handler type)))
            (handler type-handlers gen value))
        (if (identical? :system/string type)
          (write-system-string-field gen (.normal (field-name property-handler type)) value)
          (.serializeJsonField ^Base value gen (field-name property-handler type))))
      (throw (IllegalArgumentException. (format "Value `%s` is no FHIR type." value))))))

(defn- write-fields! [type-handlers property-handlers gen m]
  (when-not (map? m)
    (throw (IllegalArgumentException. (format "Value `%s` is no FHIR type." m))))
  (run!
   (fn [property-handler]
     (when-some [value (m (.-key ^PropertyHandler property-handler))]
       (write-field! type-handlers gen property-handler value)))
   property-handlers))

(def ^:private complex-types
  #{"Address" "Age" "Annotation" "Attachment" "Bundle.entry.search"
    "CodeableConcept" "Coding" "ContactDetail" "ContactPoint" "Contributor"
    "Count" "DataRequirement" "DataRequirement.codeFilter"
    "DataRequirement.dateFilter" "DataRequirement.sort" "Distance" "Dosage"
    "Dosage.doseAndRate" "Duration" "Expression" "Extension" "HumanName"
    "Identifier" "Meta" "Money" "Narrative" "ParameterDefinition" "Period"
    "Quantity" "Range" "Ratio" "Reference" "RelatedArtifact" "SampledData"
    "Signature" "Timing" "Timing.repeat" "TriggerDefinition" "UsageContext"})

(defn- create-type-handler
  "Creates a handler for `type` using `element-definitions`.

  The element definitions must not contain nested backbone element definitions.
  Use the `separate-element-definitions` function to separate nested backbone
  element definitions."
  [kind type element-definitions]
  (if (complex-types type)
    (fn complex-java-type-handler [_type-handlers gen value]
      (.serializeAsJsonValue ^Complex value gen))
    (let [property-handlers (create-property-handlers type element-definitions)]
      (condp = kind
        :resource
        (fn resource-handler [type-handlers ^JsonGenerator gen resource]
          (.writeStartObject gen)
          (.writeStringField gen "resourceType" type)
          (write-fields! type-handlers property-handlers gen resource)
          (.writeEndObject gen))
        :complex-type
        (fn complex-type-handler [type-handlers ^JsonGenerator gen value]
          (.writeStartObject gen)
          (write-fields! type-handlers property-handlers gen value)
          (.writeEndObject gen))))))

(defn create-type-handlers
  "Creates a map of keyword type names to type-handlers from the snapshot
  `element-definitions` of a StructureDefinition resource.

  Returns an anomaly in case of errors."
  {:arglists '([kind element-definitions])}
  [kind [{type :path} & more]]
  (reduce-kv
   (fn [res type element-definitions]
     (let [kind (if (str/includes? type ".") :complex-type kind)]
       (assoc res (fhir-type-keyword type) (create-type-handler kind type element-definitions))))
   {}
   (res/separate-element-definitions type more)))

(defn- valid-xml-char? [^long c]
  (or (== 0x09 c) (== 0x0A c) (== 0x0D c)
      (and (<= 0x20 c) (<= c 0xD7FF))
      (and (<= 0xE000 c) (<= c 0xFFFD))))

(defn- write-xml-str [^Writer writer s]
  (let [^String s (str s)
        len (.length s)]
    (loop [i 0
           start 0]
      (if (< i len)
        (let [c (.charAt s i)]
          (case c
            \& (do
                 (when (< start i)
                   (.write writer s start (- i start)))
                 (.write writer "&amp;")
                 (recur (inc i) (inc i)))
            \< (do
                 (when (< start i)
                   (.write writer s start (- i start)))
                 (.write writer "&lt;")
                 (recur (inc i) (inc i)))
            \> (do
                 (when (< start i)
                   (.write writer s start (- i start)))
                 (.write writer "&gt;")
                 (recur (inc i) (inc i)))
            \" (do
                 (when (< start i)
                   (.write writer s start (- i start)))
                 (.write writer "&quot;")
                 (recur (inc i) (inc i)))
            (cond
              (Character/isHighSurrogate c)
              (let [i' (inc i)]
                (if (and (< i' len) (Character/isLowSurrogate (.charAt s i')))
                  (recur (inc i') start)
                  (do
                    (when (< start i)
                      (.write writer s start (- i start)))
                    (.write writer "?")
                    (recur i' i'))))

              (or (Character/isLowSurrogate c) (not (valid-xml-char? (int c))))
              (do
                (when (< start i)
                  (.write writer s start (- i start)))
                (.write writer "?")
                (recur (inc i) (inc i)))

              :else
              (recur (inc i) start))))
        (when (< start len)
          (.write writer s start (- len start)))))))

(defn- write-xml-attr [^Writer writer k v]
  (.write writer " ")
  (.write writer (name k))
  (.write writer "=\"")
  (write-xml-str writer v)
  (.write writer "\""))

(defn- write-start-tag [^Writer writer tag attrs]
  (.write writer "<")
  (.write writer ^String tag)
  (run! (fn [[k v]] (write-xml-attr writer k v)) attrs))

(declare write-element-tree!)

(defn- write-element-tree-content! [^Writer writer content]
  (run!
   (fn [x]
     (if (instance? Element x)
       (write-element-tree! writer x)
       (write-xml-str writer x)))
   content))

(defn- write-element-tree! [^Writer writer {:keys [tag attrs content]}]
  (write-start-tag writer (name tag) attrs)
  (if (seq content)
    (do
      (.write writer ">")
      (write-element-tree-content! writer content)
      (.write writer "</")
      (.write writer (name tag))
      (.write writer ">"))
    (.write writer "/>")))

(defn- write-end-tag [^Writer writer tag]
  (.write writer "</")
  (.write writer ^String tag)
  (.write writer ">"))

(defn- write-primitive-attrs! [^Writer writer ^Primitive value]
  (when-some [id (.id value)]
    (write-xml-attr writer :id id))
  (when-some [value (.valueAsString value)]
    (write-xml-attr writer :value value)))

(declare write-value!)

(defn- write-primitive! [xml-handlers ^Writer writer tag ^Primitive value]
  (if (instance? Xhtml value)
    (write-element-tree! writer (type/xhtml-to-xml value))
    (let [extensions (.extension value)]
      (write-start-tag writer tag nil)
      (write-primitive-attrs! writer value)
      (if (seq extensions)
        (do
          (.write writer ">")
          (run! #(write-value! xml-handlers writer "extension" %) extensions)
          (write-end-tag writer tag))
        (.write writer "/>")))))

(defn- write-system-string! [^Writer writer tag value]
  (write-start-tag writer tag {:value value})
  (.write writer "/>"))

(defn- write-resource-wrapper! [xml-handlers ^Writer writer value]
  (write-start-tag writer "resource" nil)
  (.write writer ">")
  (write-value! xml-handlers writer nil value)
  (write-end-tag writer "resource"))

(defn- xml-type-tag [base-tag value]
  (str base-tag (su/capital (name (:fhir/type value)))))

(deftype XmlPropertyHandler [key tag polymorphic type resource-wrapper])

(defn- xml-type-keyword [path code]
  (cond
    (= "http://hl7.org/fhirpath/System.String" code)
    :system/string

    (#{"Element" "BackboneElement"} code)
    (fhir-type-keyword path)

    (Character/isUpperCase ^char (first code))
    (keyword "fhir" code)

    :else
    (keyword "fhir" code)))

(defn- xml-property-handler-definition
  [parent-type {:keys [path] content-reference :contentReference element-types :type}]
  (let [polymorphic (< 1 (count element-types))
        base-tag (res/base-field-name parent-type path polymorphic)
        first-type-code (:code (first element-types))]
    (XmlPropertyHandler.
     (keyword base-tag)
     base-tag
     polymorphic
     (if content-reference
       (fhir-type-keyword (subs content-reference 1))
       (when (= 1 (count element-types))
         (xml-type-keyword path first-type-code)))
     (= "Resource" first-type-code))))

(defn- create-xml-property-handlers [type element-definitions]
  (ReducibleArray. (map (partial xml-property-handler-definition type) element-definitions)))

(defn- write-xml-field-1! [xml-handlers writer ^XmlPropertyHandler property-handler value]
  (cond
    (.-resource-wrapper property-handler)
    (write-resource-wrapper! xml-handlers writer value)

    (.-polymorphic property-handler)
    (write-value! xml-handlers writer (xml-type-tag (.-tag property-handler) value) value)

    :else
    (write-value! xml-handlers writer (.-tag property-handler) value)))

(defn- write-xml-field! [xml-handlers writer ^XmlPropertyHandler property-handler value]
  (if (sequential? value)
    (run! #(write-xml-field-1! xml-handlers writer property-handler %) value)
    (write-xml-field-1! xml-handlers writer property-handler value)))

(defn- write-xml-fields! [xml-handlers writer property-handlers m]
  (run!
   (fn [property-handler]
     (when-some [value (get m (.-key ^XmlPropertyHandler property-handler))]
       (write-xml-field! xml-handlers writer property-handler value)))
   property-handlers))

(defn- write-complex! [xml-handlers ^Writer writer tag attrs property-handlers value]
  (write-start-tag writer tag attrs)
  (if (seq value)
    (do
      (.write writer ">")
      (write-xml-fields! xml-handlers writer property-handlers value)
      (write-end-tag writer tag))
    (.write writer "/>")))

(defn- create-xml-type-handler [kind type element-definitions]
  (let [property-handlers (create-xml-property-handlers type element-definitions)]
    (case kind
      :resource
      (fn resource-xml-handler [xml-handlers ^Writer writer value]
        (write-complex! xml-handlers writer type {:xmlns fhir-namespace} property-handlers value))
      :complex-type
      (fn complex-xml-handler [xml-handlers ^Writer writer tag value]
        (write-complex! xml-handlers writer tag nil property-handlers value)))))

(defn- create-xml-type-handlers
  "Creates a map of keyword type names to XML type-handlers."
  [kind [{type :path} & more]]
  (reduce-kv
   (fn [res type element-definitions]
     (let [kind (if (str/includes? type ".") :complex-type kind)]
       (assoc res (fhir-type-keyword type) (create-xml-type-handler kind type element-definitions))))
   {}
   (res/separate-element-definitions type more)))

(defn- write-value! [xml-handlers writer tag value]
  (cond
    (instance? Primitive value)
    (write-primitive! xml-handlers writer tag value)

    (string? value)
    (write-system-string! writer tag value)

    :else
    (if-some [handler (xml-handlers (:fhir/type value))]
      (if tag
        (handler xml-handlers writer tag value)
        (handler xml-handlers writer value))
      (throw (IllegalArgumentException. (format "Value `%s` is no supported FHIR XML type." value))))))

(defn- build-context [complex-types resources]
  (let [structure-definitions (into complex-types resources)
        json-handlers
        (reduce
         (fn [r {:keys [kind] {elements :element} :snapshot}]
           (into r (create-type-handlers (keyword kind) elements)))
         {}
         structure-definitions)
        xml-handlers
        (reduce
         (fn [r {:keys [kind] {elements :element} :snapshot}]
           (into r (create-xml-type-handlers (keyword kind) elements)))
         {}
         structure-definitions)]
    (with-meta json-handlers {:xml-handlers xml-handlers})))

(defmethod m/pre-init-spec :blaze.fhir/writing-context [_]
  (s/keys :req-un [:blaze.fhir/structure-definition-repo]))

(defmethod ig/init-key :blaze.fhir/writing-context
  [_ {:keys [structure-definition-repo]}]
  (log/info "Init writing context")
  (ba/throw-when
   (build-context (sdr/complex-types structure-definition-repo)
                  (sdr/resources structure-definition-repo))))
