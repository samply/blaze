(ns blaze.fhir.writing-context
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec.resource :as res]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.json :as json]
   [blaze.fhir.spec.type.protocols :as p]
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
   [com.fasterxml.jackson.core JsonGenerator SerializableString]))

(set! *warn-on-reflection* true)

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
       (json/field-name (str base-field-name (su/capital code)))]))
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
          field-name (json/field-name base-field-name)]
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
         (let [field-name (json/field-name base-field-name)]
           (fn [_type] field-name)))
       polymorphic
       (if complex-type
         (keyword "fhir" first-type-code)
         (when element-type
           (fhir-type-keyword path)))))))

(defn- create-property-handlers
  "Returns a map of JSON property names to property handlers."
  [type element-definitions]
  (doall (map (partial property-handler-definitions type) element-definitions)))

(defn- field-name ^SerializableString [^PropertyHandler def type]
  ((.-field-name def) type))

(defn- write-field! [type-handlers ^JsonGenerator gen ^PropertyHandler def value]
  (if (sequential? value)
    (when-some [first-value (first value)]
      (when-some [type (or (.-type def) (type/type first-value))]
        (if-some [handler (type-handlers type)]
          (do (.writeFieldName gen (field-name def type))
              (.writeStartArray gen)
              (run! (partial handler type-handlers gen) value)
              (.writeEndArray gen))
          (json/write-field gen (field-name def type) value))))
    (when-some [type (or (.-type def) (type/type value))]
      (if-some [handler (type-handlers type)]
        (do (.writeFieldName gen (field-name def type))
            (handler type-handlers gen value))
        (json/write-field gen (field-name def type) value)))))

(defn- write-fields! [type-handlers property-handlers gen value]
  (loop [[def & more] property-handlers]
    (when def
      (when-some [child-value (value (.-key ^PropertyHandler def))]
        (write-field! type-handlers gen def child-value))
      (recur more))))

(def ^:private record-types
  #{"Attachment" "Extension" "Coding" "CodeableConcept" "CodeableReference"
    "Quantity" "Ratio" "Period" "Identifier" "HumanName" "Address" "Reference"
    "Meta" "Bundle.entry.search"})

(defn- create-type-handler
  "Creates a handler for `type` using `element-definitions`.

  The element definitions must not contain nested backbone element definitions.
  Use the `separate-element-definitions` function to separate nested backbone
  element definitions."
  [kind type element-definitions]
  (if (record-types type)
    (fn record-type-handler [_type-handlers gen value]
      (p/-serialize-json value gen))
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

(defn- build-context [complex-types resources]
  (reduce
   (fn [r {:keys [kind] {elements :element} :snapshot}]
     (into r (create-type-handlers (keyword kind) elements)))
   {}
   (into complex-types resources)))

(defmethod m/pre-init-spec :blaze.fhir/writing-context [_]
  (s/keys :req-un [:blaze.fhir/structure-definition-repo]))

(defmethod ig/init-key :blaze.fhir/writing-context
  [_ {:keys [structure-definition-repo]}]
  (log/info "Init writing context")
  (ba/throw-when
   (build-context (sdr/complex-types structure-definition-repo)
                  (sdr/resources structure-definition-repo))))
