(ns blaze.fhir.type-metadata
  "The registry of `TypeMetadata` instances, one per FHIR type that is
  represented as a map.

  A `TypeMetadata` instance is the shape of one FHIR type: its properties in
  element-definition order together with the property handler writing each of
  them, the keyword to slot index and the lexical key order used for hashing.
  All values of a type share one instance, which is what keeps the four map
  classes free of per-type variation.

  The registry is global, because both the `#fhir/map` data reader and the
  Clojure compiler, which recreates constants of `IRecord` types via their
  static `create` method, need to find the metadata of a type without being
  given a context. There is no deployment variance in the set of FHIR types, the
  same way there is none in the structure definition repository. The parsing
  context references this one registry, so that the parser builds values whose
  metadata is identical to the one the writer and the data reader use."
  (:refer-clojure :exclude [str])
  (:require
   [blaze.fhir.spec.resource :as res]
   [blaze.fhir.spec.type.string-util :as su]
   [blaze.fhir.structure-definition-repo :as sdr]
   [blaze.util :refer [str]]
   [clojure.string :as str])
  (:import
   [blaze.fhir.spec.type FieldName TypeMetadata TypeMetadata$Kind]
   [blaze.fhir.writing ComplexListPropertyHandler ComplexPropertyHandler
    PolymorphicPropertyHandler PrimitivePropertyHandler PropertyHandler
    StringPropertyHandler]
   [clojure.lang Keyword]))

(set! *warn-on-reflection* true)

(defn- fhir-type-keyword [type]
  (let [parts (cons "fhir" (seq (str/split type #"\.")))]
    (keyword (str/join "." (butlast parts)) (last parts))))

(def ^:private complex-types
  "Names of all complex types with a Java implementation.

  Values of those types have their own class and need no type metadata. All
  other types are represented as maps."
  #{"Address" "Age" "Annotation" "Attachment" "Bundle.entry.search"
    "CodeableConcept" "Coding" "ContactDetail" "ContactPoint" "Contributor"
    "Count" "DataRequirement" "DataRequirement.codeFilter"
    "DataRequirement.dateFilter" "DataRequirement.sort" "Distance" "Dosage"
    "Dosage.doseAndRate" "Duration" "Expression" "Extension" "HumanName"
    "Identifier" "Meta" "Money" "Narrative" "ParameterDefinition" "Period"
    "Quantity" "Range" "Ratio" "Reference" "RelatedArtifact" "SampledData"
    "Signature" "Timing" "Timing.repeat" "TriggerDefinition" "UsageContext"})

(defn- complex-property-handler
  "Creates a property handler for a value that is able to write itself.

  Since every FHIR value is either a complex type with a Java implementation, a
  value of one of the four map classes or a resource, all of which implement
  `Complex`, the only distinction left is the cardinality."
  [key base-field-name many]
  (let [field-name (.normal (FieldName/of base-field-name))]
    (if many
      (ComplexListPropertyHandler. key field-name)
      (ComplexPropertyHandler. key field-name))))

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
      (complex-property-handler (keyword base-field-name) base-field-name (= "*" max)))
    (let [polymorphic (< 1 (count element-types))
          first-type-code (:code (first element-types))
          base-field-name (res/base-field-name parent-type path polymorphic)
          key (keyword base-field-name)]
      (cond
        (= "http://hl7.org/fhirpath/System.String" first-type-code)
        (StringPropertyHandler. key (.normal (FieldName/of base-field-name)))

        polymorphic
        (polymorphic-property-handler key base-field-name element-types)

        (Character/isUpperCase ^char (first first-type-code))
        (complex-property-handler key base-field-name (= "*" max))

        :else
        (PrimitivePropertyHandler. key (FieldName/of base-field-name))))))

(def ^:private domain-resource-properties
  ["id" "meta" "implicitRules" "language" "text" "contained" "extension"
   "modifierExtension"])

(def ^:private resource-properties
  ["id" "meta" "implicitRules" "language"])

(def ^:private backbone-element-properties
  ["id" "extension" "modifierExtension"])

(def ^:private element-properties
  ["id" "extension"])

(defn- metadata-kind
  "Determines the FHIR abstract type `type` is derived from by its leading
  properties, which are fixed per abstract type."
  [type property-names]
  (cond
    (= domain-resource-properties (take 8 property-names)) TypeMetadata$Kind/DOMAIN_RESOURCE
    (= resource-properties (take 4 property-names)) TypeMetadata$Kind/RESOURCE
    (= backbone-element-properties (take 3 property-names)) TypeMetadata$Kind/BACKBONE_ELEMENT
    (= element-properties (take 2 property-names)) TypeMetadata$Kind/ELEMENT
    :else (throw (ex-info (format "Unsupported leading properties of type `%s`." type)
                          {:type type :properties (vec property-names)}))))

(defn- create-type-metadata* [type element-definitions]
  (let [property-handlers (into-array PropertyHandler (map (partial create-property-handler type) element-definitions))
        keys (into-array Keyword (map #(.key ^PropertyHandler %) property-handlers))]
    (TypeMetadata. (fhir-type-keyword type) (metadata-kind type (map name keys))
                   type keys property-handlers)))

(defn create-type-metadata
  "Creates a map of keyword type names to type metadata from the snapshot
  `element-definitions` of a StructureDefinition resource.

  Types with a Java implementation are skipped, because their values have their
  own class."
  {:arglists '([element-definitions])}
  [[{type :path} & more]]
  (reduce-kv
   (fn [res type element-definitions]
     (cond-> res
       (not (complex-types type))
       (assoc (fhir-type-keyword type) (create-type-metadata* type element-definitions))))
   {}
   (res/separate-element-definitions type more)))

(defn build
  "Builds the type metadata of all FHIR types of `structure-definition-repo`
  that are represented as maps."
  [structure-definition-repo]
  (reduce
   (fn [r {{elements :element} :snapshot}]
     (into r (create-type-metadata elements)))
   {}
   (into (sdr/complex-types structure-definition-repo)
         (sdr/resources structure-definition-repo))))

(def ^:private global-registry
  (delay (build sdr/repo)))

(defn registry
  "Returns the global map of keyword type names to type metadata."
  []
  @global-registry)

(defn type-metadata
  "Returns the type metadata of the FHIR `type` keyword or nil if `type` is
  either unknown or a complex type with a Java implementation."
  ^TypeMetadata [type]
  ((registry) type))
