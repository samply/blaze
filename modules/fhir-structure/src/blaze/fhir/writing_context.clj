(ns blaze.fhir.writing-context
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec.resource :as res]
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
   [blaze.fhir.spec.type FieldName]
   [blaze.fhir.writing ComplexListPropertyHandler ComplexPropertyHandler
    ComplexTypeHandler
    MapListPropertyHandler MapPropertyHandler MapTypeHandler
    PolymorphicPropertyHandler
    PrimitivePropertyHandler PropertyHandler ResourcePropertyHandler
    ResourceTypeHandler StringPropertyHandler TypeHandler]
   [clojure.lang Keyword]))

(set! *warn-on-reflection* true)

(defn- fhir-type-keyword [type]
  (let [parts (cons "fhir" (seq (str/split type #"\.")))]
    (keyword (str/join "." (butlast parts)) (last parts))))

(def ^:private complex-types
  "Names of all complex types with a Java implementation.

  Values of those types write themselves, while all other types are represented
  as maps and are written by a `MapTypeHandler`."
  #{"Address" "Age" "Annotation" "Attachment" "Bundle.entry.search"
    "CodeableConcept" "Coding" "ContactDetail" "ContactPoint" "Contributor"
    "Count" "DataRequirement" "DataRequirement.codeFilter"
    "DataRequirement.dateFilter" "DataRequirement.sort" "Distance" "Dosage"
    "Dosage.doseAndRate" "Duration" "Expression" "Extension" "HumanName"
    "Identifier" "Meta" "Money" "Narrative" "ParameterDefinition" "Period"
    "Quantity" "Range" "Ratio" "Reference" "RelatedArtifact" "SampledData"
    "Signature" "Timing" "Timing.repeat" "TriggerDefinition" "UsageContext"})

(defn- complex-property-handler
  "Creates a property handler for the complex type with name `type-name`.

  The handler is specialized on the cardinality, so many-valued properties are
  always output as a list, were single-valued properties are output as a single
  value."
  [key type-name base-field-name many]
  (let [field-name (.normal (FieldName/of base-field-name))]
    (if (complex-types type-name)
      (if many
        (ComplexListPropertyHandler. key field-name)
        (ComplexPropertyHandler. key field-name))
      (let [type (fhir-type-keyword type-name)]
        (if many
          (MapListPropertyHandler. key type field-name)
          (MapPropertyHandler. key type field-name))))))

(defn- polymorphic-property-handler [key base-field-name element-types]
  (PolymorphicPropertyHandler.
   key
   (into-array Keyword (map (fn [{:keys [code]}] (keyword "fhir" code)) element-types))
   (into-array FieldName (map (fn [{:keys [code]}] (FieldName/of (str base-field-name (su/capital code)))) element-types))))

(defn- create-property-handler
  "Takes `element-definition` and returns a property handler."
  {:arglists '([parent-type element-definition])}
  [parent-type
   {:keys [path max] content-reference :contentReference element-types :type}]
  (if content-reference
    (let [base-field-name (res/base-field-name parent-type path false)]
      (complex-property-handler
       (keyword base-field-name)
       (subs content-reference 1)
       base-field-name
       (= "*" max)))
    (let [polymorphic (< 1 (count element-types))
          first-type-code (:code (first element-types))
          element-type (and (= 1 (count element-types))
                            (#{"BackboneElement" "Element"} first-type-code))
          base-field-name (res/base-field-name parent-type path polymorphic)
          key (keyword base-field-name)]
      (condp = first-type-code
        "Resource"
        (ResourcePropertyHandler. key (.normal (FieldName/of base-field-name)))

        "http://hl7.org/fhirpath/System.String"
        (StringPropertyHandler. key (.normal (FieldName/of base-field-name)))

        (if polymorphic
          (polymorphic-property-handler key base-field-name element-types)

          (if (Character/isUpperCase ^char (first first-type-code))
            (complex-property-handler
             key
             (if element-type path first-type-code)
             base-field-name
             (= "*" max))
            (PrimitivePropertyHandler.
             key
             (FieldName/of base-field-name))))))))

(defn- create-property-handlers
  "Returns an array of property handlers, one for each element definition."
  [type element-definitions]
  (into-array PropertyHandler (map (partial create-property-handler type) element-definitions)))

(defn- create-type-handler
  "Creates a handler for `type` using `element-definitions`.

  The element definitions must not contain nested backbone element definitions.
  Use the `separate-element-definitions` function to separate nested backbone
  element definitions."
  [kind type element-definitions]
  (if (complex-types type)
    ComplexTypeHandler/INSTANCE
    (let [property-handlers (create-property-handlers type element-definitions)]
      (case kind
        :resource (ResourceTypeHandler. type property-handlers)
        :complex-type (MapTypeHandler. property-handlers)))))

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

(defn- link-type-handlers!
  "Resolves the type handlers all property handlers of `type-handlers` need at
  write time.

  Has to happen after all type handlers are created, because type handlers can
  reference each other recursively."
  [type-handlers]
  (run!
   (fn [[_ type-handler]]
     (.link ^TypeHandler type-handler type-handlers))
   type-handlers)
  type-handlers)

(defmethod m/pre-init-spec :blaze.fhir/writing-context [_]
  (s/keys :req-un [:blaze.fhir/structure-definition-repo]))

(defmethod ig/init-key :blaze.fhir/writing-context
  [_ {:keys [structure-definition-repo]}]
  (log/info "Init writing context")
  (-> (build-context (sdr/complex-types structure-definition-repo)
                     (sdr/resources structure-definition-repo))
      (ba/throw-when)
      (link-type-handlers!)))
