(ns blaze.terminology-service.local.lookup
  (:require
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util :as fu]))

(defn- designation-param [{:keys [language use additionalUse value]}]
  ["language" language
   "use" use
   "additionalUse" additionalUse
   "value" value])

(def ^:private concept-properties
  {"status"
   {:description "A code that indicates the status of the concept. Typical values are active, experimental, deprecated, and retired"}
   "inactive"
   {:description "True if the concept is not considered active - e.g. not a valid concept any more. Property type is boolean, default value is false. Note that the status property may also be used to indicate that a concept is inactive"}
   "effectiveDate"
   {:description "The date at which the concept status was last changed"}
   "deprecated"
   {:description "The date at which a concept was deprecated. Concepts that are deprecated but not inactive can still be used, but their use is discouraged, and they should be expected to be made inactive in a future release. Property type is dateTime"}
   "deprecationDate"
   {:description "The date at which a concept was deprecated. Concepts that are deprecated but not inactive can still be used, but their use is discouraged, and they should be expected to be made inactive in a future release. Property type is dateTime"}
   "retirementDate"
   {:description "The date at which a concept was retired"}
   "notSelectable"
   {:description "The concept is not intended to be chosen by the user - only intended to be used as a selector for other concepts. Note, though, that the interpretation of this is highly contextual; all concepts are selectable in some context. Property type is boolean"}
   "parent"
   {:description "The concept identified in this property is a parent of the concept on which it is a property. The property type will be 'code'. The meaning of 'parent' is defined by the hierarchyMeaning attribute"}
   "child"
   {:description "The concept identified in this property is a child of the concept on which it is a property. The property type will be 'code'. The meaning of 'child' is defined by the hierarchyMeaning attribute"}
   "partOf"
   {:description "The concept identified in this property (by its code) contains this concept as a component (i.e. a part-of relationship rather than a subsumption relationship such as elbow is part-of arm"}
   "synonym"
   {:description "This property contains an alternative code that may be used to identify this concept instead of the primary code"}
   "comment"
   {:description "A string that provides additional detail pertinent to the use or understanding of the concept"}
   "itemWeight"
   {:description "A numeric value that allows the comparison (less than, greater than) or other numerical manipulation of a concept (e.g. Adding up components of a score). Scores are usually a whole number, but occasionally decimals are encountered in scores. In questionnaires, the item weight may be represented using the http://hl7.org/fhir/StructureDefinition/itemWeight extension"}})

(defn- standard-property-description [property-code]
  (some-> (get-in concept-properties [property-code :description]) type/string))

(defn- property-description* [code-system property-code]
  (some #(when (= property-code (:value (:code %))) (:description %)) (:property code-system)))

(defn- property-description [code-system property-code]
  (or (property-description* code-system property-code)
      (standard-property-description property-code)))

(defn- property-param [code-system {:keys [code value source]}]
  (let [description (property-description code-system (:value code))]
    (cond->
     ["code" code
      "value" value
      "source" source]
      description (conj "description" description))))

(defn parameters-from-concept
  {:arglists '([code-system concept])}
  [{:keys [name version] :as code-system}
   {:keys [display definition designation property]}]

  (fu/parameters
   "name" name
   "version" version
   "display" (if (nil? display) nil (type/string display))
   "definition" definition
   "designation" (map designation-param designation)
   "property" (map (partial property-param code-system) property)))
