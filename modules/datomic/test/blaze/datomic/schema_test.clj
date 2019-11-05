(ns blaze.datomic.schema-test
  (:require
    [blaze.datomic.schema :refer [element-definition-tx-data path->ident]]
    [blaze.structure-definition :refer [read-structure-definitions]]
    [clojure.spec.test.alpha :as st]
    [clojure.string :as str]
    [clojure.test :refer [are deftest is testing]]))


(st/instrument)


(defonce structure-definitions (read-structure-definitions))


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
             :element/type-code "id"
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
             :element/value-set-binding "http://hl7.org/fhir/ValueSet/administrative-gender|4.0.0"}
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
             :element/primitive? false
             :element/choice-type? false
             :ElementDefinition/isSummary true
             :element/type-code "Reference"
             :element/json-key "other"}
            [:db/add "Patient.link.other" :element/type "Reference"]
            [:db/add "Patient.link" :type/elements "Patient.link.other"]])))

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
             :element/value-set-binding "http://hl7.org/fhir/ValueSet/link-type|4.0.0"}
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
             :element/type-code "id"
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
             :element/type-code "string"
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
             :element/type-code "string"
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
            [:db/add :db.part/db :db.install/partition "part.ContactPoint"]]))))
