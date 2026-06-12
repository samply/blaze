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
   [blaze.fhir XmlUtil]
   [blaze.fhir.spec.type Base BundleEntrySearch CodeableConcept Coding Complex FieldName
    Identifier Meta Period Primitive Xhtml XmlDirectWriter]
   [clojure.data.xml.node Element]
   [clojure.lang IPersistentMap Indexed]
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

(defn- write-xml-str [^Writer writer s]
  (XmlUtil/writeEscaped writer (str s)))

(defn- write-xml-attr [^Writer writer k v]
  (.write writer " ")
  (.write writer (name k))
  (.write writer "=\"")
  (write-xml-str writer v)
  (.write writer "\""))

(defn- write-start-tag [^Writer writer tag attrs]
  (.write writer "<")
  (.write writer ^String tag)
  (when attrs
    (run! (fn [[k v]] (write-xml-attr writer k v)) attrs)))

(defn- write-resource-start-tag [^Writer writer tag]
  (.write writer "<")
  (.write writer ^String tag)
  (.write writer " xmlns=\"")
  (.write writer fhir-namespace)
  (.write writer "\""))

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

(defn- write-primitive-field!
  [xml-handlers ^Writer writer ^String start-tag ^String end-tag ^String value-start-tag ^Primitive value]
  (if (instance? Xhtml value)
    (write-element-tree! writer (type/xhtml-to-xml value))
    (let [id (.id value)
          string-value (.valueAsString value)
          extensions (.extension value)]
      (if (and (nil? id) string-value (empty? extensions))
        (do
          (.write writer value-start-tag)
          (write-xml-str writer string-value)
          (.write writer "\"/>"))
        (do
          (.write writer start-tag)
          (when id
            (write-xml-attr writer :id id))
          (when string-value
            (write-xml-attr writer :value string-value))
          (if (seq extensions)
            (do
              (.write writer ">")
              (run! #(write-value! xml-handlers writer "extension" %) extensions)
              (.write writer end-tag))
            (.write writer "/>")))))))

(defn- write-system-string! [^Writer writer tag value]
  (write-start-tag writer tag {:value value})
  (.write writer "/>"))

(defn- write-system-string-field! [^Writer writer ^String value-start-tag value]
  (.write writer value-start-tag)
  (write-xml-str writer value)
  (.write writer "\"/>"))

(defn- write-resource-wrapper! [xml-handlers ^Writer writer value]
  (write-start-tag writer "resource" nil)
  (.write writer ">")
  (write-value! xml-handlers writer nil value)
  (write-end-tag writer "resource"))

(defn- write-bundle-entry-search! [xml-handlers ^Writer writer ^BundleEntrySearch search]
  (when (and (nil? (.id search)) (empty? (.extension search)))
    (.write writer "<search")
    (if (or (.mode search) (.score search))
      (do
        (.write writer ">")
        (when-some [mode (.mode search)]
          (write-primitive-field! xml-handlers writer "<mode" "</mode>" "<mode value=\"" mode))
        (when-some [score (.score search)]
          (write-primitive-field! xml-handlers writer "<score" "</score>" "<score value=\"" score))
        (.write writer "</search>"))
      (.write writer "/>"))
    true))

(defn- write-search-bundle-entry! [xml-handlers ^Writer writer value]
  (let [m ^IPersistentMap value
        full-url (.valAt m :fullUrl)
        resource (.valAt m :resource)
        search (.valAt m :search)]
    (when (and (nil? (.valAt m :id))
               (nil? (.valAt m :extension))
               (nil? (.valAt m :modifierExtension))
               (nil? (.valAt m :link))
               (nil? (.valAt m :request))
               (nil? (.valAt m :response))
               (or (nil? search) (instance? BundleEntrySearch search))
               (or full-url resource search))
      (.write writer "<entry>")
      (when full-url
        (write-primitive-field! xml-handlers writer "<fullUrl" "</fullUrl>" "<fullUrl value=\"" full-url))
      (when resource
        (write-resource-wrapper! xml-handlers writer resource))
      (if search
        (when-not (write-bundle-entry-search! xml-handlers writer search)
          (write-value! xml-handlers writer "search" search))
        nil)
      (.write writer "</entry>")
      true)))

(defn- fhir-type [value]
  (when (instance? IPersistentMap value)
    (.valAt ^IPersistentMap value Base/FHIR_TYPE_KEY)))

(defn- xml-type-tag [base-tag value]
  (str base-tag (su/capital (name (fhir-type value)))))

(deftype XmlPropertyHandler
         [key tag polymorphic-tags type resource-wrapper start-tag end-tag value-start-tag])

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
     (when polymorphic
       (into
        {}
        (map
         (fn [{:keys [code]}]
           [(xml-type-keyword path code)
            (str base-tag (su/capital code))]))
        element-types))
     (if content-reference
       (fhir-type-keyword (subs content-reference 1))
       (when (= 1 (count element-types))
         (xml-type-keyword path first-type-code)))
     (= "Resource" first-type-code)
     (str "<" base-tag)
     (str "</" base-tag ">")
     (str "<" base-tag " value=\""))))

(defn- create-xml-property-handlers [type element-definitions]
  (ReducibleArray. (map (partial xml-property-handler-definition type) element-definitions)))

(defn- write-xml-field-1! [xml-handlers writer ^XmlPropertyHandler property-handler value]
  (cond
    (.-resource-wrapper property-handler)
    (write-resource-wrapper! xml-handlers writer value)

    (.-polymorphic-tags property-handler)
    (write-value!
     xml-handlers writer
     (or ((.-polymorphic-tags property-handler) (fhir-type value))
         (xml-type-tag (.-tag property-handler) value))
     value)

    (instance? Primitive value)
    (write-primitive-field!
     xml-handlers writer
     (.-start-tag property-handler)
     (.-end-tag property-handler)
     (.-value-start-tag property-handler)
     value)

    (string? value)
    (write-system-string-field! writer (.-value-start-tag property-handler) value)

    (identical? :fhir/CodeableConcept (.-type property-handler))
    (when-not (XmlDirectWriter/writeCodeableConcept writer (.-tag property-handler) ^CodeableConcept value)
      (write-value! xml-handlers writer (.-tag property-handler) value))

    (identical? :fhir/Coding (.-type property-handler))
    (when-not (XmlDirectWriter/writeCoding writer (.-tag property-handler) ^Coding value)
      (write-value! xml-handlers writer (.-tag property-handler) value))

    (identical? :fhir/Period (.-type property-handler))
    (when-not (XmlDirectWriter/writePeriod writer (.-tag property-handler) ^Period value)
      (write-value! xml-handlers writer (.-tag property-handler) value))

    (identical? :fhir/Identifier (.-type property-handler))
    (when-not (XmlDirectWriter/writeIdentifier writer (.-tag property-handler) ^Identifier value)
      (write-value! xml-handlers writer (.-tag property-handler) value))

    (identical? :fhir/Meta (.-type property-handler))
    (when-not (XmlDirectWriter/writeMeta writer (.-tag property-handler) ^Meta value)
      (write-value! xml-handlers writer (.-tag property-handler) value))

    :else
    (write-value! xml-handlers writer (.-tag property-handler) value)))

(defn- write-xml-field-values! [xml-handlers writer ^XmlPropertyHandler property-handler value]
  (if (instance? Indexed value)
    (let [len (count value)]
      (loop [i 0]
        (when (< i len)
          (write-xml-field-1! xml-handlers writer property-handler (nth value i))
          (recur (inc i)))))
    (run! #(write-xml-field-1! xml-handlers writer property-handler %) value)))

(defn- write-xml-field! [xml-handlers writer ^XmlPropertyHandler property-handler value]
  (if (sequential? value)
    (write-xml-field-values! xml-handlers writer property-handler value)
    (write-xml-field-1! xml-handlers writer property-handler value)))

(defn- write-xml-fields! [xml-handlers ^Writer writer property-handlers m]
  (let [property-handlers (.array ^ReducibleArray property-handlers)
        len (alength property-handlers)]
    (loop [i 0 wrote? false]
      (if (< i len)
        (let [property-handler (aget property-handlers i)]
          (if-some [value (.valAt ^IPersistentMap m (.-key ^XmlPropertyHandler property-handler))]
            (do
              (when-not wrote?
                (.write writer ">"))
              (write-xml-field! xml-handlers writer property-handler value)
              (recur (inc i) true))
            (recur (inc i) wrote?)))
        wrote?))))

(defn- write-complex! [xml-handlers ^Writer writer tag attrs property-handlers value]
  (write-start-tag writer tag attrs)
  (if (write-xml-fields! xml-handlers writer property-handlers value)
    (write-end-tag writer tag)
    (.write writer "/>")))

(defn- write-resource! [xml-handlers ^Writer writer tag property-handlers value]
  (write-resource-start-tag writer tag)
  (if (write-xml-fields! xml-handlers writer property-handlers value)
    (write-end-tag writer tag)
    (.write writer "/>")))

(defn- create-xml-type-handler [kind type element-definitions]
  (let [property-handlers (create-xml-property-handlers type element-definitions)]
    (if (= "Bundle.entry" type)
      (fn bundle-entry-xml-handler [xml-handlers ^Writer writer tag value]
        (if (and (= "entry" tag) (write-search-bundle-entry! xml-handlers writer value))
          nil
          (write-complex! xml-handlers writer tag nil property-handlers value)))
      (case kind
        :resource
        (fn resource-xml-handler [xml-handlers ^Writer writer value]
          (write-resource! xml-handlers writer type property-handlers value))
        :complex-type
        (fn complex-xml-handler [xml-handlers ^Writer writer tag value]
          (write-complex! xml-handlers writer tag nil property-handlers value))))))

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

    (and tag (instance? CodeableConcept value))
    (when-not (XmlDirectWriter/writeCodeableConcept writer tag value)
      (if-some [handler (xml-handlers (fhir-type value))]
        (handler xml-handlers writer tag value)
        (throw (IllegalArgumentException. (format "Value `%s` is no supported FHIR XML type." value)))))

    (and tag (instance? Coding value))
    (when-not (XmlDirectWriter/writeCoding writer tag value)
      (if-some [handler (xml-handlers (fhir-type value))]
        (handler xml-handlers writer tag value)
        (throw (IllegalArgumentException. (format "Value `%s` is no supported FHIR XML type." value)))))

    (and tag (instance? Period value))
    (when-not (XmlDirectWriter/writePeriod writer tag value)
      (if-some [handler (xml-handlers (fhir-type value))]
        (handler xml-handlers writer tag value)
        (throw (IllegalArgumentException. (format "Value `%s` is no supported FHIR XML type." value)))))

    (and tag (instance? Identifier value))
    (when-not (XmlDirectWriter/writeIdentifier writer tag value)
      (if-some [handler (xml-handlers (fhir-type value))]
        (handler xml-handlers writer tag value)
        (throw (IllegalArgumentException. (format "Value `%s` is no supported FHIR XML type." value)))))

    (and tag (instance? Meta value))
    (when-not (XmlDirectWriter/writeMeta writer tag value)
      (if-some [handler (xml-handlers (fhir-type value))]
        (handler xml-handlers writer tag value)
        (throw (IllegalArgumentException. (format "Value `%s` is no supported FHIR XML type." value)))))

    :else
    (if-some [handler (xml-handlers (fhir-type value))]
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
