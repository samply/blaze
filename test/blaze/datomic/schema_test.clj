(ns blaze.datomic.schema-test
  (:require
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [juxt.iota :refer [given]]
    [blaze.datomic.schema :refer :all]
    [blaze.structure-definition :refer [read-structure-definitions]]
    [clojure.string :as str]
    [datomic.api :as d]))


(st/instrument)

(def structure-definitions (read-structure-definitions "fhir/r4/structure-definitions"))


(def patient (structure-definitions "Patient"))


(defn- element-definitions [path]
  (let [[name] (str/split path #"\.")]
    (some #(when (= path (:path %)) %)
          (:element (:snapshot (structure-definitions name))))))


(deftest path->ident-test
  (are [path key] (= key (path->ident path))
    "Patient.gender" :Patient/gender
    "Patient.deceased[x]" :Patient/deceased
    "Patient.contact.name" :Patient.contact/name))


(deftest element-definition-test
  (testing "Patient"
    (is (= (element-definition-tx-data (element-definitions "Patient"))
           [{:db/id "Patient"
             :db/ident :Patient}
            {:db/id "part.Patient"
             :db/ident :part/Patient}
            [:db/add :db.part/db :db.install/partition "part.Patient"]])))

  (testing "Patient.id"
    (is (= (element-definition-tx-data (element-definitions "Patient.id"))
           [{:db/id "Patient.id"
             :db/ident :Patient/id
             :db/valueType :db.type/string
             :db/cardinality :db.cardinality/one
             :db/unique :db.unique/identity
             :element/primitive? true
             :element/choice-type? false
             :ElementDefinition/path "Patient.id"
             :ElementDefinition/isSummary true
             :element/type-code "id"
             :element/json-key "id"}
            [:db/add "Patient" :type/elements "Patient.id"]])))

  (testing "Patient.gender"
    (is (= (element-definition-tx-data (element-definitions "Patient.gender"))
           [{:db/id "Patient.gender"
             :db/ident :Patient/gender
             :db/valueType :db.type/ref
             :db/cardinality :db.cardinality/one
             :element/primitive? true
             :element/choice-type? false
             :ElementDefinition/path "Patient.gender"
             :ElementDefinition/isSummary true
             :element/type-code "code"
             :element/json-key "gender"
             :element/code-system-url "http://hl7.org/fhir/administrative-gender"}
            [:db/add "Patient" :type/elements "Patient.gender"]])))

  (testing "Patient.birthDate"
    (is (= (element-definition-tx-data (element-definitions "Patient.birthDate"))
           [{:db/id "Patient.birthDate"
             :db/ident :Patient/birthDate
             :db/valueType :db.type/bytes
             :db/cardinality :db.cardinality/one
             :element/primitive? true
             :element/choice-type? false
             :ElementDefinition/path "Patient.birthDate"
             :ElementDefinition/isSummary true
             :element/type-code "date"
             :element/json-key "birthDate"}
            [:db/add "Patient" :type/elements "Patient.birthDate"]])))

  (testing "Patient.deceased[x]"
    (is (= (element-definition-tx-data (element-definitions "Patient.deceased[x]"))
           [{:db/id "Patient.deceased[x]"
             :db/ident :Patient/deceased
             :db/valueType :db.type/ref
             :db/cardinality :db.cardinality/one
             :element/choice-type? true
             :ElementDefinition/path "Patient.deceased[x]"
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
            [:db/add "Patient" :type/elements "Patient.deceased[x]"]])))

  (testing "Patient.telecom"
    (is (= (element-definition-tx-data (element-definitions "Patient.telecom"))
           [{:db/id "Patient.telecom"
             :db/ident :Patient/telecom
             :db/valueType :db.type/ref
             :db/cardinality :db.cardinality/many
             :db/isComponent true
             :element/primitive? false
             :element/choice-type? false
             :ElementDefinition/path "Patient.telecom"
             :ElementDefinition/isSummary true
             :element/type-code "ContactPoint"
             :element/json-key "telecom"}
            [:db/add "Patient.telecom" :element/type "ContactPoint"]
            [:db/add "Patient" :type/elements "Patient.telecom"]])))

  (testing "Patient.link"
    (is (= (element-definition-tx-data (element-definitions "Patient.link"))
           [{:db/id "Patient.link"
             :db/ident :Patient/link
             :db/valueType :db.type/ref
             :db/cardinality :db.cardinality/many
             :db/isComponent true
             :element/primitive? false
             :element/choice-type? false
             :ElementDefinition/path "Patient.link"
             :ElementDefinition/isSummary true
             :element/type-code "BackboneElement"
             :element/json-key "link"}
            {:db/id "part.Patient.link"
             :db/ident :part/Patient.link}
            [:db/add :db.part/db :db.install/partition "part.Patient.link"]
            [:db/add "Patient" :type/elements "Patient.link"]])))

  (testing "Patient.link.other"
    (is (= (element-definition-tx-data (element-definitions "Patient.link.other"))
           [{:db/id "Patient.link.other"
             :db/ident :Patient.link/other
             :db/valueType :db.type/ref
             :db/cardinality :db.cardinality/one
             :element/primitive? false
             :element/choice-type? false
             :ElementDefinition/path "Patient.link.other"
             :ElementDefinition/isSummary true
             :element/type-code "Reference"
             :element/json-key "other"}
            [:db/add "Patient.link.other" :element/type "Reference"]
            [:db/add "Patient.link" :type/elements "Patient.link.other"]])))

  (testing "Patient.link.type"
    (is (= (element-definition-tx-data (element-definitions "Patient.link.type"))
           [{:db/id "Patient.link.type"
             :db/ident :Patient.link/type
             :db/valueType :db.type/ref
             :db/cardinality :db.cardinality/one
             :element/primitive? true
             :element/choice-type? false
             :ElementDefinition/path "Patient.link.type"
             :ElementDefinition/isSummary true
             :element/type-code "code"
             :element/json-key "type"
             :element/code-system-url "http://hl7.org/fhir/link-type"}
            [:db/add "Patient.link" :type/elements "Patient.link.type"]])))

  (testing "Observation.code"
    (is (= (element-definition-tx-data (element-definitions "Observation.code"))
           [{:db/id "Observation.code"
             :db/ident :Observation/code
             :db/valueType :db.type/ref
             :db/cardinality :db.cardinality/one
             :db/isComponent true
             :element/primitive? false
             :element/choice-type? false
             :ElementDefinition/path "Observation.code"
             :ElementDefinition/isSummary true
             :element/type-code "CodeableConcept"
             :element/json-key "code"}
            [:db/add "Observation.code" :element/type "CodeableConcept"]
            [:db/add "Observation" :type/elements "Observation.code"]
            {:db/ident :Observation.index/code
             :db/valueType :db.type/ref
             :db/cardinality :db.cardinality/many}]))))


(comment
  (->> (blaze.structure-definition/read-structure-definitions "fhir/r4/structure-definitions")
       (vals)
       (structure-definition-schemas)
       (filter #(and (vector? %) (= :element/type (nth % 2))))
       (map #(nth % 3))
       (set)
       (sort))
  (clojure.repl/pst)

  (element-definition-tx-data (element-definitions "Patient.communication"))
  (element-definition-tx-data (element-definitions "Patient.communication.preferred"))
  )
