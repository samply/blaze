(ns blaze.datomic.schema-test
  (:require
    [blaze.datomic.schema
     :refer
     [element-definition-tx-data
      path->ident
      search-parameter-tx-data]]
    [blaze.structure-definition
     :refer [read-structure-definitions read-search-parameters]]
    [clojure.spec.test.alpha :as st]
    [clojure.string :as str]
    [clojure.test :refer [are deftest is testing]]))


(st/instrument)


(defonce structure-definitions (read-structure-definitions))
(defonce search-parameters (read-search-parameters))


(defn- structure-definition [id]
  (some #(when (= id (:id %)) %) structure-definitions))


(defn- element-definition [path]
  (let [[id] (str/split path #"\.")]
    (->> (-> (structure-definition id) :snapshot :element)
         (some #(when (= path (:path %)) %)))))


(deftest path->ident-test
  (are [path key] (= key (path->ident path))
    "Patient.gender" :Patient/gender
    "Patient.deceased[x]" :Patient/deceased
    "Patient.contact.name" :Patient.contact/name))


(deftest element-definition-test
  (testing "Patient"
    (is (= (element-definition-tx-data
             (structure-definition "Patient")
             (element-definition "Patient"))
           [{:db/id "Patient"
             :db/ident :Patient}
            {:db/id "part.Patient"
             :db/ident :part/Patient}
            [:db/add :db.part/db :db.install/partition "part.Patient"]])))

  (testing "Patient.id"
    (is (= (element-definition-tx-data
             (structure-definition "Patient")
             (element-definition "Patient.id"))
           [{:db/id "Patient.id"
             :db/ident :Patient/id
             :db/valueType :db.type/string
             :db/cardinality :db.cardinality/one
             :db/unique :db.unique/identity
             :element/primitive? true
             :element/choice-type? false
             :ElementDefinition/isSummary true
             :element/type-code "http://hl7.org/fhirpath/System.String"
             :element/json-key "id"}
            [:db/add "Patient" :type/elements "Patient.id"]])))

  (testing "Primitive Single-Valued Single-Typed"
    (testing "Patient.active"
      (is (= (element-definition-tx-data
               (structure-definition "Patient")
               (element-definition "Patient.active"))
             [{:db/id "Patient.active"
               :db/ident :Patient/active
               :db/valueType :db.type/boolean
               :db/cardinality :db.cardinality/one
               :element/primitive? true
               :element/choice-type? false
               :ElementDefinition/isSummary true
               :element/type-code "boolean"
               :element/json-key "active"}
              [:db/add "Patient" :type/elements "Patient.active"]]))))

  (testing "Primitive Multi-Valued Single-Typed"
    (testing "ServiceRequest.instantiatesUri"
      (is (= (element-definition-tx-data
               (structure-definition "ServiceRequest")
               (element-definition "ServiceRequest.instantiatesUri"))
             [{:db/id "ServiceRequest.instantiatesUri"
               :db/ident :ServiceRequest/instantiatesUri
               :db/valueType :db.type/string
               :db/cardinality :db.cardinality/many
               :element/primitive? true
               :element/choice-type? false
               :ElementDefinition/isSummary true
               :element/type-code "uri"
               :element/json-key "instantiatesUri"}
              [:db/add "ServiceRequest" :type/elements "ServiceRequest.instantiatesUri"]]))))

  (testing "Patient.gender"
    (is (= (element-definition-tx-data
             (structure-definition "Patient")
             (element-definition "Patient.gender"))
           [{:db/id "Patient.gender"
             :db/ident :Patient/gender
             :db/valueType :db.type/ref
             :db/cardinality :db.cardinality/one
             :element/primitive? true
             :element/choice-type? false
             :ElementDefinition/isSummary true
             :element/type-code "code"
             :element/json-key "gender"
             :element/value-set-binding "http://hl7.org/fhir/ValueSet/administrative-gender|4.0.1"}
            [:db/add "Patient" :type/elements "Patient.gender"]])))

  (testing "Patient.birthDate"
    (is (= (element-definition-tx-data
             (structure-definition "Patient")
             (element-definition "Patient.birthDate"))
           [{:db/id "Patient.birthDate"
             :db/ident :Patient/birthDate
             :db/valueType :db.type/bytes
             :db/cardinality :db.cardinality/one
             :element/primitive? true
             :element/choice-type? false
             :ElementDefinition/isSummary true
             :element/type-code "date"
             :element/json-key "birthDate"}
            [:db/add "Patient" :type/elements "Patient.birthDate"]])))

  (testing "Choice-Typed"
    (testing "Patient.deceased[x]"
      (is (= (element-definition-tx-data
               (structure-definition "Patient")
               (element-definition "Patient.deceased[x]"))
             [{:db/id "Patient.deceased[x]"
               :db/ident :Patient/deceased
               :db/valueType :db.type/ref
               :db/cardinality :db.cardinality/one
               :element/choice-type? true
               :ElementDefinition/isSummary true}
              {:db/id "Patient.deceasedBoolean"
               :db/ident :Patient/deceasedBoolean
               :db/valueType :db.type/boolean
               :db/cardinality :db.cardinality/one
               :element/primitive? true
               :element/part-of-choice-type? true
               :element/type-attr-ident :Patient/deceased
               :element/type-code "boolean"
               :element/json-key "deceasedBoolean"}
              [:db/add "Patient.deceased[x]" :element/type-choices "Patient.deceasedBoolean"]
              {:db/id "Patient.deceasedDateTime"
               :db/ident :Patient/deceasedDateTime
               :db/valueType :db.type/bytes
               :db/cardinality :db.cardinality/one
               :element/primitive? true
               :element/part-of-choice-type? true
               :element/type-attr-ident :Patient/deceased
               :element/type-code "dateTime"
               :element/json-key "deceasedDateTime"}
              [:db/add "Patient.deceased[x]" :element/type-choices "Patient.deceasedDateTime"]
              [:db/add "Patient" :type/elements "Patient.deceased[x]"]]))))

  (testing "Patient.telecom"
    (is (= (element-definition-tx-data
             (structure-definition "Patient")
             (element-definition "Patient.telecom"))
           [{:db/id "Patient.telecom"
             :db/ident :Patient/telecom
             :db/valueType :db.type/ref
             :db/cardinality :db.cardinality/many
             :db/isComponent true
             :element/primitive? false
             :element/choice-type? false
             :ElementDefinition/isSummary true
             :element/type-code "ContactPoint"
             :element/json-key "telecom"}
            [:db/add "Patient.telecom" :element/type "ContactPoint"]
            [:db/add "Patient" :type/elements "Patient.telecom"]])))

  (testing "Patient.link"
    (is (= (element-definition-tx-data
             (structure-definition "Patient")
             (element-definition "Patient.link"))
           [{:db/id "Patient.link"
             :db/ident :Patient/link
             :db/valueType :db.type/ref
             :db/cardinality :db.cardinality/many
             :db/isComponent true
             :element/primitive? false
             :element/choice-type? false
             :ElementDefinition/isSummary true
             :element/type-code "BackboneElement"
             :element/json-key "link"}
            {:db/id "part.Patient.link"
             :db/ident :part/Patient.link}
            [:db/add :db.part/db :db.install/partition "part.Patient.link"]
            [:db/add "Patient.link" :element/type "Patient.link"]
            [:db/add "Patient" :type/elements "Patient.link"]])))

  (testing "Patient.link.other"
    (is (= (element-definition-tx-data
             (structure-definition "Patient")
             (element-definition "Patient.link.other"))
           [{:db/id "Patient.link.other"
             :db/ident :Patient.link/other
             :db/valueType :db.type/ref
             :db/cardinality :db.cardinality/one
             :db/isComponent true
             :element/primitive? false
             :element/choice-type? false
             :ElementDefinition/isSummary true
             :element/type-code "Reference"
             :element/json-key "other"}
            [:db/add "Patient.link.other" :element/type "Reference"]
            [:db/add "Patient.link" :type/elements "Patient.link.other"]
            {:db/ident :Reference.Patient.link/other
             :db/valueType :db.type/ref
             :db/cardinality :db.cardinality/one}])))

  (testing "Patient.link.type"
    (is (= (element-definition-tx-data
             (structure-definition "Patient")
             (element-definition "Patient.link.type"))
           [{:db/id "Patient.link.type"
             :db/ident :Patient.link/type
             :db/valueType :db.type/ref
             :db/cardinality :db.cardinality/one
             :element/primitive? true
             :element/choice-type? false
             :ElementDefinition/isSummary true
             :element/type-code "code"
             :element/json-key "type"
             :element/value-set-binding "http://hl7.org/fhir/ValueSet/link-type|4.0.1"}
            [:db/add "Patient.link" :type/elements "Patient.link.type"]])))

  (testing "Patient.extension"
    (is (= (element-definition-tx-data
             (structure-definition "Patient")
             (element-definition "Patient.extension"))
           [{:db/valueType :db.type/ref
             :db/isComponent true
             :element/type-code "Extension"
             :element/choice-type? false
             :element/json-key "extension"
             :db/cardinality :db.cardinality/many
             :db/id "Patient.extension"
             :db/ident :Patient/extension
             :element/primitive? false}
            [:db/add "Patient.extension" :element/type "Extension"]
            [:db/add "Patient" :type/elements "Patient.extension"]])))

  (testing "Patient.contained"
    (is (= (element-definition-tx-data
             (structure-definition "Patient")
             (element-definition "Patient.contained"))
           [{:db/valueType :db.type/ref
             :db/isComponent true
             :element/type-code "Resource"
             :element/choice-type? false
             :element/json-key "contained"
             :db/cardinality :db.cardinality/many
             :db/id "Patient.contained"
             :db/ident :Patient/contained
             :element/primitive? false}
            [:db/add "Patient.contained" :element/type "Resource"]
            [:db/add "Patient" :type/elements "Patient.contained"]])))

  (testing "Observation.code"
    (is (= (element-definition-tx-data
             (structure-definition "Observation")
             (element-definition "Observation.code"))
           [{:db/id "Observation.code"
             :db/ident :Observation/code
             :db/valueType :db.type/ref
             :db/cardinality :db.cardinality/one
             :db/isComponent true
             :element/primitive? false
             :element/choice-type? false
             :ElementDefinition/isSummary true
             :element/type-code "CodeableConcept"
             :element/json-key "code"}
            [:db/add "Observation.code" :element/type "CodeableConcept"]
            [:db/add "Observation" :type/elements "Observation.code"]
            {:db/ident :Observation.index/code
             :db/valueType :db.type/ref
             :db/cardinality :db.cardinality/many}])))

  (testing "Observation.subject"
    (is (= (element-definition-tx-data
             (structure-definition "Observation")
             (element-definition "Observation.subject"))
           [{:db/id "Observation.subject"
             :db/ident :Observation/subject
             :db/valueType :db.type/ref
             :db/cardinality :db.cardinality/one
             :db/isComponent true
             :element/primitive? false
             :element/choice-type? false
             :ElementDefinition/isSummary true
             :element/type-code "Reference"
             :element/json-key "subject"}
            [:db/add "Observation.subject" :element/type "Reference"]
            [:db/add "Observation" :type/elements "Observation.subject"]
            {:db/ident :Reference.Observation/subject
             :db/valueType :db.type/ref
             :db/cardinality :db.cardinality/one}])))

  (testing "Observation.performer"
    (is (= (element-definition-tx-data
             (structure-definition "Observation")
             (element-definition "Observation.performer"))
           [{:db/id "Observation.performer"
             :db/ident :Observation/performer
             :db/valueType :db.type/ref
             :db/cardinality :db.cardinality/many
             :db/isComponent true
             :element/primitive? false
             :element/choice-type? false
             :ElementDefinition/isSummary true
             :element/type-code "Reference"
             :element/json-key "performer"}
            [:db/add "Observation.performer" :element/type "Reference"]
            [:db/add "Observation" :type/elements "Observation.performer"]
            {:db/ident :Reference.Observation/performer
             :db/valueType :db.type/ref
             :db/cardinality :db.cardinality/many}])))

  (testing "CodeSystem.concept"
    (is (= (element-definition-tx-data
             (structure-definition "CodeSystem")
             (element-definition "CodeSystem.concept"))
           [{:db/id "CodeSystem.concept"
             :db/ident :CodeSystem/concept
             :db/valueType :db.type/ref
             :db/isComponent true
             :element/type-code "BackboneElement"
             :element/choice-type? false
             :element/json-key "concept"
             :db/cardinality :db.cardinality/many
             :element/primitive? false}
            {:db/id "part.CodeSystem.concept" :db/ident :part/CodeSystem.concept}
            [:db/add :db.part/db :db.install/partition "part.CodeSystem.concept"]
            [:db/add "CodeSystem.concept" :element/type "CodeSystem.concept"]
            [:db/add "CodeSystem" :type/elements "CodeSystem.concept"]])))

  (testing "CodeSystem.concept.concept"
    (is (= (element-definition-tx-data
             (structure-definition "CodeSystem")
             (element-definition "CodeSystem.concept.concept"))
           [{:db/id "CodeSystem.concept.concept"
             :db/ident :CodeSystem.concept/concept
             :db/valueType :db.type/ref
             :db/isComponent true
             :element/type-code "BackboneElement"
             :element/choice-type? false
             :element/json-key "concept"
             :db/cardinality :db.cardinality/many
             :element/primitive? false}
            [:db/add "CodeSystem.concept.concept" :element/type "CodeSystem.concept"]
            [:db/add "CodeSystem.concept" :type/elements "CodeSystem.concept.concept"]])))

  (testing "ImplementationGuide.definition.page.page"
    (is (= (element-definition-tx-data
             (structure-definition "ImplementationGuide")
             (element-definition "ImplementationGuide.definition.page.page"))
           [{:db/id "ImplementationGuide.definition.page.page"
             :db/ident :ImplementationGuide.definition.page/page
             :db/valueType :db.type/ref
             :db/isComponent true
             :element/type-code "BackboneElement"
             :element/choice-type? false
             :element/json-key "page"
             :db/cardinality :db.cardinality/many
             :element/primitive? false}
            [:db/add "ImplementationGuide.definition.page.page" :element/type "ImplementationGuide.definition.page"]
            [:db/add "ImplementationGuide.definition.page" :type/elements "ImplementationGuide.definition.page.page"]])))

  (testing "Bundle"
    (is (= (element-definition-tx-data
             (structure-definition "Bundle")
             (element-definition "Bundle"))
           [{:db/id "Bundle"
             :db/ident :Bundle}
            {:db/id "part.Bundle"
             :db/ident :part/Bundle}
            [:db/add :db.part/db :db.install/partition "part.Bundle"]])))

  (testing "Bundle.id"
    (is (= (element-definition-tx-data
             (structure-definition "Bundle")
             (element-definition "Bundle.id"))
           [{:db/id "Bundle.id"
             :db/ident :Bundle/id
             :db/valueType :db.type/string
             :db/cardinality :db.cardinality/one
             :db/unique :db.unique/identity
             :element/primitive? true
             :element/choice-type? false
             :ElementDefinition/isSummary true
             :element/type-code "http://hl7.org/fhirpath/System.String"
             :element/json-key "id"}
            [:db/add "Bundle" :type/elements "Bundle.id"]])))

  (testing "ElementDefinition.id"
    (is (= (element-definition-tx-data
             (structure-definition "ElementDefinition")
             (element-definition "ElementDefinition.id"))
           [{:db/id "ElementDefinition.id"
             :db/ident :ElementDefinition/id
             :db/valueType :db.type/string
             :db/cardinality :db.cardinality/one
             :element/primitive? true
             :element/choice-type? false
             :element/type-code "http://hl7.org/fhirpath/System.String"
             :element/json-key "id"}
            [:db/add "ElementDefinition" :type/elements "ElementDefinition.id"]])))

  (testing "Money.id"
    (is (= (element-definition-tx-data
             (structure-definition "Money")
             (element-definition "Money.id"))
           [{:db/id "Money.id"
             :db/ident :Money/id
             :db/valueType :db.type/string
             :db/cardinality :db.cardinality/one
             :element/primitive? true
             :element/choice-type? false
             :element/type-code "http://hl7.org/fhirpath/System.String"
             :element/json-key "id"}
            [:db/add "Money" :type/elements "Money.id"]])))

  (testing "Money.value"
    (is (= (element-definition-tx-data
             (structure-definition "Money")
             (element-definition "Money.value"))
           [{:db/id "Money.value"
             :db/ident :Money/value
             :db/valueType :db.type/bigdec
             :db/cardinality :db.cardinality/one
             :element/primitive? true
             :element/choice-type? false
             :ElementDefinition/isSummary true
             :element/type-code "decimal"
             :element/json-key "value"}
            [:db/add "Money" :type/elements "Money.value"]])))

  (testing "SubstanceSpecification.name"
    (is (= (element-definition-tx-data
             (structure-definition "SubstanceSpecification")
             (element-definition "SubstanceSpecification.name"))
           [{:db/id "SubstanceSpecification.name"
             :db/ident :SubstanceSpecification/name
             :db/valueType :db.type/ref
             :db/cardinality :db.cardinality/many
             :db/isComponent true
             :element/primitive? false
             :element/choice-type? false
             :ElementDefinition/isSummary true
             :element/type-code "BackboneElement"
             :element/json-key "name"}
            {:db/id "part.SubstanceSpecification.name"
             :db/ident :part/SubstanceSpecification.name}
            [:db/add :db.part/db :db.install/partition "part.SubstanceSpecification.name"]
            [:db/add "SubstanceSpecification.name" :element/type "SubstanceSpecification.name"]
            [:db/add "SubstanceSpecification" :type/elements "SubstanceSpecification.name"]])))

  (testing "SubstanceSpecification.name.name"
    (is (= (element-definition-tx-data
             (structure-definition "SubstanceSpecification")
             (element-definition "SubstanceSpecification.name.name"))
           [{:db/id "SubstanceSpecification.name.name"
             :db/ident :SubstanceSpecification.name/name
             :db/valueType :db.type/string
             :db/cardinality :db.cardinality/one
             :element/primitive? true
             :element/choice-type? false
             :ElementDefinition/isSummary true
             :element/type-code "string"
             :element/json-key "name"}
            [:db/add "SubstanceSpecification.name" :type/elements "SubstanceSpecification.name.name"]])))

  (testing "SubstanceSpecification.name.synonym"
    (is (= (element-definition-tx-data
             (structure-definition "SubstanceSpecification")
             (element-definition "SubstanceSpecification.name.synonym"))
           [{:db/id "SubstanceSpecification.name.synonym"
             :db/ident :SubstanceSpecification.name/synonym
             :db/valueType :db.type/ref
             :db/cardinality :db.cardinality/many
             :db/isComponent true
             :element/primitive? false
             :element/choice-type? false
             :ElementDefinition/isSummary true
             :element/type-code "BackboneElement"
             :element/json-key "synonym"}
            [:db/add "SubstanceSpecification.name.synonym" :element/type "SubstanceSpecification.name"]
            [:db/add "SubstanceSpecification.name" :type/elements "SubstanceSpecification.name.synonym"]])))

  (testing "Library.url"
    (is (= (element-definition-tx-data
             (structure-definition "Library")
             (element-definition "Library.url"))
           [{:db/id "Library.url"
             :db/ident :Library/url
             :db/valueType :db.type/string
             :db/cardinality :db.cardinality/one
             :db/index true
             :element/primitive? true
             :element/choice-type? false
             :ElementDefinition/isSummary true
             :element/type-code "uri"
             :element/json-key "url"}
            [:db/add "Library" :type/elements "Library.url"]])))

  (testing "Specimen.collection.bodySite"
    (is (= (element-definition-tx-data
             (structure-definition "Specimen")
             (element-definition "Specimen.collection.bodySite"))
           [{:db/id "Specimen.collection.bodySite"
             :db/ident :Specimen.collection/bodySite
             :db/valueType :db.type/ref
             :db/cardinality :db.cardinality/one
             :db/isComponent true
             :element/primitive? false
             :element/choice-type? false
             :element/type-code "CodeableConcept"
             :element/json-key "bodySite"}
            [:db/add "Specimen.collection.bodySite" :element/type "CodeableConcept"]
            [:db/add "Specimen.collection" :type/elements "Specimen.collection.bodySite"]
            #:db{:cardinality :db.cardinality/many
                 :ident :Specimen.collection.index/bodySite
                 :valueType :db.type/ref}])))

  (testing "ContactPoint"
    (is (= (element-definition-tx-data
             (structure-definition "ContactPoint")
             (element-definition "ContactPoint"))
           [{:db/id "ContactPoint"
             :db/ident :ContactPoint}
            {:db/id "part.ContactPoint"
             :db/ident :part/ContactPoint}
            [:db/add :db.part/db :db.install/partition "part.ContactPoint"]])))

  (testing "ActivityDefinition.timing[x]"
    (is (= (element-definition-tx-data
             (structure-definition "ActivityDefinition")
             (element-definition "ActivityDefinition.timing[x]"))
           [{:db/id "ActivityDefinition.timing[x]"
             :db/ident :ActivityDefinition/timing
             :db/cardinality :db.cardinality/one
             :element/choice-type? true
             :db/valueType :db.type/ref}
            {:element/type-attr-ident :ActivityDefinition/timing
             :db/valueType :db.type/ref
             :element/type-code "Timing"
             :element/json-key "timingTiming"
             :element/part-of-choice-type? true
             :db/cardinality :db.cardinality/one
             :db/id "ActivityDefinition.timingTiming"
             :db/ident :ActivityDefinition/timingTiming
             :element/primitive? false}
            [:db/add "ActivityDefinition.timing[x]" :element/type-choices "ActivityDefinition.timingTiming"]
            [:db/add "ActivityDefinition.timingTiming" :element/type "Timing"]
            {:element/type-attr-ident :ActivityDefinition/timing
             :db/valueType :db.type/bytes
             :element/type-code "dateTime"
             :element/json-key "timingDateTime"
             :element/part-of-choice-type? true
             :db/cardinality :db.cardinality/one
             :db/id "ActivityDefinition.timingDateTime"
             :db/ident :ActivityDefinition/timingDateTime
             :element/primitive? true}
            [:db/add "ActivityDefinition.timing[x]" :element/type-choices "ActivityDefinition.timingDateTime"]
            {:element/type-attr-ident :ActivityDefinition/timing
             :db/valueType :db.type/bytes
             :element/type-code "Age"
             :element/json-key "timingAge"
             :element/part-of-choice-type? true
             :db/cardinality :db.cardinality/one
             :db/id "ActivityDefinition.timingAge"
             :db/ident :ActivityDefinition/timingAge
             :element/primitive? true}
            [:db/add "ActivityDefinition.timing[x]" :element/type-choices "ActivityDefinition.timingAge"]
            {:element/type-attr-ident :ActivityDefinition/timing
             :db/valueType :db.type/ref
             :element/type-code "Period"
             :element/json-key "timingPeriod"
             :element/part-of-choice-type? true
             :db/cardinality :db.cardinality/one
             :db/id "ActivityDefinition.timingPeriod"
             :db/ident :ActivityDefinition/timingPeriod
             :element/primitive? false}
            [:db/add "ActivityDefinition.timing[x]" :element/type-choices "ActivityDefinition.timingPeriod"]
            [:db/add "ActivityDefinition.timingPeriod" :element/type "Period"]
            {:element/type-attr-ident :ActivityDefinition/timing
             :db/valueType :db.type/ref
             :element/type-code "Range"
             :element/json-key "timingRange"
             :element/part-of-choice-type? true
             :db/cardinality :db.cardinality/one
             :db/id "ActivityDefinition.timingRange"
             :db/ident :ActivityDefinition/timingRange
             :element/primitive? false}
            [:db/add "ActivityDefinition.timing[x]" :element/type-choices "ActivityDefinition.timingRange"]
            [:db/add "ActivityDefinition.timingRange" :element/type "Range"]
            {:element/type-attr-ident :ActivityDefinition/timing
             :db/valueType :db.type/bytes
             :element/type-code "Duration"
             :element/json-key "timingDuration"
             :element/part-of-choice-type? true
             :db/cardinality :db.cardinality/one
             :db/id "ActivityDefinition.timingDuration"
             :db/ident :ActivityDefinition/timingDuration
             :element/primitive? true}
            [:db/add "ActivityDefinition.timing[x]" :element/type-choices "ActivityDefinition.timingDuration"]
            [:db/add "ActivityDefinition" :type/elements "ActivityDefinition.timing[x]"]])))

  (testing "Measure.title"
    (is (= (element-definition-tx-data
             (structure-definition "Measure")
             (element-definition "Measure.title"))
           [{:db/id "Measure.title"
             :db/ident :Measure/title
             :db/valueType :db.type/string
             :db/cardinality :db.cardinality/one
             :element/primitive? true
             :element/choice-type? false
             :ElementDefinition/isSummary true
             :element/type-code "string"
             :element/json-key "title"}
            [:db/add "Measure" :type/elements "Measure.title"]])))

  (testing "Reference.reference"
    (is (= (element-definition-tx-data
             (structure-definition "Reference")
             (element-definition "Reference.reference"))
           [{:db/id "Reference.reference"
             :db/ident :Reference/reference
             :db/valueType :db.type/string
             :db/cardinality :db.cardinality/one
             :element/primitive? true
             :element/choice-type? false
             :ElementDefinition/isSummary true
             :element/type-code "string"
             :element/json-key "reference"}
            [:db/add "Reference" :type/elements "Reference.reference"]])))

  (testing "Extension.value[x]"
    (is (= (element-definition-tx-data
             (structure-definition "Extension")
             (element-definition "Extension.value[x]"))
           [{:db/id "Extension.value[x]"
             :db/ident :Extension/value,
             :db/cardinality :db.cardinality/one,
             :element/choice-type? true,
             :db/valueType :db.type/ref}
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/bytes,
             :element/type-code "base64Binary"
             :element/json-key "valueBase64Binary"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueBase64Binary"
             :db/ident :Extension/valueBase64Binary,
             :element/primitive? true}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueBase64Binary"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/boolean,
             :element/type-code "boolean"
             :element/json-key "valueBoolean"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueBoolean"
             :db/ident :Extension/valueBoolean,
             :element/primitive? true}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueBoolean"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/string,
             :element/type-code "canonical"
             :element/json-key "valueCanonical"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueCanonical"
             :db/ident :Extension/valueCanonical,
             :element/primitive? true}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueCanonical"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "code"
             :element/json-key "valueCode"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueCode"
             :db/ident :Extension/valueCode,
             :element/primitive? true}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueCode"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/bytes,
             :element/type-code "date"
             :element/json-key "valueDate"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueDate"
             :db/ident :Extension/valueDate,
             :element/primitive? true}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueDate"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/bytes,
             :element/type-code "dateTime"
             :element/json-key "valueDateTime"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueDateTime"
             :db/ident :Extension/valueDateTime,
             :element/primitive? true}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueDateTime"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/bigdec,
             :element/type-code "decimal"
             :element/json-key "valueDecimal"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueDecimal"
             :db/ident :Extension/valueDecimal,
             :element/primitive? true}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueDecimal"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/string,
             :element/type-code "id"
             :element/json-key "valueId"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueId"
             :db/ident :Extension/valueId,
             :element/primitive? true}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueId"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/instant,
             :element/type-code "instant"
             :element/json-key "valueInstant"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueInstant"
             :db/ident :Extension/valueInstant,
             :element/primitive? true}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueInstant"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/long,
             :element/type-code "integer"
             :element/json-key "valueInteger"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueInteger"
             :db/ident :Extension/valueInteger,
             :element/primitive? true}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueInteger"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/string,
             :element/type-code "markdown"
             :element/json-key "valueMarkdown"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueMarkdown"
             :db/ident :Extension/valueMarkdown,
             :element/primitive? true}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueMarkdown"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/string,
             :element/type-code "oid"
             :element/json-key "valueOid"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueOid"
             :db/ident :Extension/valueOid,
             :element/primitive? true}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueOid"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/long,
             :element/type-code "positiveInt"
             :element/json-key "valuePositiveInt"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valuePositiveInt"
             :db/ident :Extension/valuePositiveInt,
             :element/primitive? true}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valuePositiveInt"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/string,
             :element/type-code "string"
             :element/json-key "valueString"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueString"
             :db/ident :Extension/valueString,
             :element/primitive? true}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueString"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/bytes,
             :element/type-code "time"
             :element/json-key "valueTime"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueTime"
             :db/ident :Extension/valueTime,
             :element/primitive? true}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueTime"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/long,
             :element/type-code "unsignedInt"
             :element/json-key "valueUnsignedInt"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueUnsignedInt"
             :db/ident :Extension/valueUnsignedInt,
             :element/primitive? true}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueUnsignedInt"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/string,
             :element/type-code "uri"
             :element/json-key "valueUri"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueUri"
             :db/ident :Extension/valueUri,
             :element/primitive? true}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueUri"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/string,
             :element/type-code "url"
             :element/json-key "valueUrl"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueUrl"
             :db/ident :Extension/valueUrl,
             :element/primitive? true}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueUrl"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/uuid,
             :element/type-code "uuid"
             :element/json-key "valueUuid"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueUuid"
             :db/ident :Extension/valueUuid,
             :element/primitive? true}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueUuid"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "Address"
             :element/json-key "valueAddress"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueAddress"
             :db/ident :Extension/valueAddress,
             :element/primitive? false}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueAddress"]
            [:db/add "Extension.valueAddress" :element/type "Address"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/bytes,
             :element/type-code "Age"
             :element/json-key "valueAge"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueAge"
             :db/ident :Extension/valueAge,
             :element/primitive? true}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueAge"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "Annotation"
             :element/json-key "valueAnnotation"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueAnnotation"
             :db/ident :Extension/valueAnnotation,
             :element/primitive? false}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueAnnotation"]
            [:db/add "Extension.valueAnnotation" :element/type "Annotation"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "Attachment"
             :element/json-key "valueAttachment"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueAttachment"
             :db/ident :Extension/valueAttachment,
             :element/primitive? false}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueAttachment"]
            [:db/add "Extension.valueAttachment" :element/type "Attachment"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "CodeableConcept"
             :element/json-key "valueCodeableConcept"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueCodeableConcept"
             :db/ident :Extension/valueCodeableConcept,
             :element/primitive? false}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueCodeableConcept"]
            [:db/add "Extension.valueCodeableConcept" :element/type "CodeableConcept"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "Coding"
             :element/json-key "valueCoding"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueCoding"
             :db/ident :Extension/valueCoding,
             :element/primitive? false}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueCoding"]
            [:db/add "Extension.valueCoding" :element/type "Coding"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "ContactPoint"
             :element/json-key "valueContactPoint"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueContactPoint"
             :db/ident :Extension/valueContactPoint,
             :element/primitive? false}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueContactPoint"]
            [:db/add "Extension.valueContactPoint" :element/type "ContactPoint"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/bytes,
             :element/type-code "Count"
             :element/json-key "valueCount"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueCount"
             :db/ident :Extension/valueCount,
             :element/primitive? true}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueCount"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/bytes,
             :element/type-code "Distance"
             :element/json-key "valueDistance"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueDistance"
             :db/ident :Extension/valueDistance,
             :element/primitive? true}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueDistance"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/bytes,
             :element/type-code "Duration"
             :element/json-key "valueDuration"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueDuration"
             :db/ident :Extension/valueDuration,
             :element/primitive? true}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueDuration"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "HumanName"
             :element/json-key "valueHumanName"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueHumanName"
             :db/ident :Extension/valueHumanName,
             :element/primitive? false}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueHumanName"]
            [:db/add "Extension.valueHumanName" :element/type "HumanName"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "Identifier"
             :element/json-key "valueIdentifier"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueIdentifier"
             :db/ident :Extension/valueIdentifier,
             :element/primitive? false}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueIdentifier"]
            [:db/add "Extension.valueIdentifier" :element/type "Identifier"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "Money"
             :element/json-key "valueMoney"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueMoney"
             :db/ident :Extension/valueMoney,
             :element/primitive? false}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueMoney"]
            [:db/add "Extension.valueMoney" :element/type "Money"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "Period"
             :element/json-key "valuePeriod"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valuePeriod"
             :db/ident :Extension/valuePeriod,
             :element/primitive? false}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valuePeriod"]
            [:db/add "Extension.valuePeriod" :element/type "Period"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/bytes,
             :element/type-code "Quantity"
             :element/json-key "valueQuantity"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueQuantity"
             :db/ident :Extension/valueQuantity,
             :element/primitive? true}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueQuantity"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "Range"
             :element/json-key "valueRange"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueRange"
             :db/ident :Extension/valueRange,
             :element/primitive? false}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueRange"]
            [:db/add "Extension.valueRange" :element/type "Range"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "Ratio"
             :element/json-key "valueRatio"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueRatio"
             :db/ident :Extension/valueRatio,
             :element/primitive? false}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueRatio"]
            [:db/add "Extension.valueRatio" :element/type "Ratio"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "Reference"
             :element/json-key "valueReference"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueReference"
             :db/ident :Extension/valueReference,
             :element/primitive? false}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueReference"]
            [:db/add "Extension.valueReference" :element/type "Reference"]
            {:db/ident :Reference.Extension/valueReference
             :db/valueType :db.type/ref
             :db/cardinality :db.cardinality/one}
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "SampledData"
             :element/json-key "valueSampledData"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueSampledData"
             :db/ident :Extension/valueSampledData,
             :element/primitive? false}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueSampledData"]
            [:db/add "Extension.valueSampledData" :element/type "SampledData"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "Signature"
             :element/json-key "valueSignature"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueSignature"
             :db/ident :Extension/valueSignature,
             :element/primitive? false}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueSignature"]
            [:db/add "Extension.valueSignature" :element/type "Signature"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "Timing"
             :element/json-key "valueTiming"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueTiming"
             :db/ident :Extension/valueTiming,
             :element/primitive? false}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueTiming"]
            [:db/add "Extension.valueTiming" :element/type "Timing"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "ContactDetail"
             :element/json-key "valueContactDetail"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueContactDetail"
             :db/ident :Extension/valueContactDetail,
             :element/primitive? false}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueContactDetail"]
            [:db/add "Extension.valueContactDetail" :element/type "ContactDetail"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "Contributor"
             :element/json-key "valueContributor"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueContributor"
             :db/ident :Extension/valueContributor,
             :element/primitive? false}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueContributor"]
            [:db/add "Extension.valueContributor" :element/type "Contributor"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "DataRequirement"
             :element/json-key "valueDataRequirement"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueDataRequirement"
             :db/ident :Extension/valueDataRequirement,
             :element/primitive? false}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueDataRequirement"]
            [:db/add "Extension.valueDataRequirement" :element/type "DataRequirement"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "Expression"
             :element/json-key "valueExpression"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueExpression"
             :db/ident :Extension/valueExpression,
             :element/primitive? false}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueExpression"]
            [:db/add "Extension.valueExpression" :element/type "Expression"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "ParameterDefinition"
             :element/json-key "valueParameterDefinition"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueParameterDefinition"
             :db/ident :Extension/valueParameterDefinition,
             :element/primitive? false}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueParameterDefinition"]
            [:db/add "Extension.valueParameterDefinition" :element/type "ParameterDefinition"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "RelatedArtifact"
             :element/json-key "valueRelatedArtifact"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueRelatedArtifact"
             :db/ident :Extension/valueRelatedArtifact,
             :element/primitive? false}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueRelatedArtifact"]
            [:db/add "Extension.valueRelatedArtifact" :element/type "RelatedArtifact"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "TriggerDefinition"
             :element/json-key "valueTriggerDefinition"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueTriggerDefinition"
             :db/ident :Extension/valueTriggerDefinition,
             :element/primitive? false}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueTriggerDefinition"]
            [:db/add "Extension.valueTriggerDefinition" :element/type "TriggerDefinition"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "UsageContext"
             :element/json-key "valueUsageContext"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueUsageContext"
             :db/ident :Extension/valueUsageContext,
             :element/primitive? false}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueUsageContext"]
            [:db/add "Extension.valueUsageContext" :element/type "UsageContext"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "Dosage"
             :element/json-key "valueDosage"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueDosage"
             :db/ident :Extension/valueDosage,
             :element/primitive? false}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueDosage"]
            [:db/add "Extension.valueDosage" :element/type "Dosage"]
            {:element/type-attr-ident :Extension/value,
             :db/valueType :db.type/ref,
             :element/type-code "Meta"
             :element/json-key "valueMeta"
             :element/part-of-choice-type? true,
             :db/cardinality :db.cardinality/one,
             :db/id "Extension.valueMeta"
             :db/ident :Extension/valueMeta,
             :element/primitive? false}
            [:db/add "Extension.value[x]" :element/type-choices "Extension.valueMeta"]
            [:db/add "Extension.valueMeta" :element/type "Meta"]
            [:db/add "Extension" :type/elements "Extension.value[x]"]]))))


(defn- search-parameter [id]
  (some #(when (= id (:id %)) %) search-parameters))


(deftest search-parameter-test
  (testing "Measure.title"
    (is (= (search-parameter-tx-data
             (search-parameter "Measure-title"))
           [{:db/id "SearchParameter.Measure-title"
             :db/ident :SearchParameter/Measure-title
             :db/valueType :db.type/string
             :db/cardinality :db.cardinality/one
             :db/index true
             :search-parameter/type :search-parameter.type/string
             :search-parameter/code "title"
             :search-parameter/json-key "title"}
            [:db/add "Measure" :resource/search-parameter "SearchParameter.Measure-title"]]))))

(comment
  (filter #(str/ends-with? % "address") (map :id search-parameters))
  (search-parameter "MeasureReport-measure")
  (frequencies (map :code search-parameters))
  )
