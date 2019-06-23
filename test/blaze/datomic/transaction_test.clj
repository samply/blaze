(ns blaze.datomic.transaction-test
  (:require
    [blaze.datomic.quantity :refer [quantity]]
    [blaze.datomic.schema :as schema]
    [blaze.datomic.test-util :refer :all]
    [blaze.datomic.transaction
     :refer [resource-upsert resource-deletion
             coerce-value transact-async resource-codes-creation]]
    [blaze.datomic.value :as value]
    [blaze.structure-definition :refer [read-structure-definitions]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.test :as dst]
    [datomic-tools.schema :as dts]
    [juxt.iota :refer [given]]
    [manifold.deferred :as md])
  (:import
    [java.time Year YearMonth LocalDate LocalDateTime OffsetDateTime ZoneOffset]
    [java.util Base64]))


(st/instrument)
(dst/instrument)


(def structure-definitions
  (read-structure-definitions "fhir/r4/structure-definitions"))


(defn- connect []
  (d/delete-database "datomic:mem://datomic.transaction-test")
  (d/create-database "datomic:mem://datomic.transaction-test")
  (let [conn (d/connect "datomic:mem://datomic.transaction-test")]
    @(d/transact conn (dts/schema))
    @(d/transact conn (schema/structure-definition-schemas structure-definitions))
    conn))


(def db (d/db (connect)))

(defn- tempid []
  (let [v (volatile! {})]
    (fn [partition]
      [partition (partition (vswap! v update partition (fnil inc 0)))])))


(defn- read-value [[op e a v :as tx-data]]
  (if (#{:db/add :db/retract} op)
    (let [value (value/read v)]
      (if (bytes? value)
        [op e a (.encodeToString (Base64/getEncoder) ^bytes value)]
        [op e a value]))
    tx-data))


(deftest resource-upsert-test

  (testing "Version handling"
    (testing "Starts with initial version -3 at creation mode :server-assigned-id"
      (let [tempid (d/tempid :part/Patient)]
        (is
          (=
            (resource-upsert
              db {"Patient" {"0" tempid}} :server-assigned-id
              {"id" "0" "resourceType" "Patient"})
            [[:db/add tempid :Patient/id "0"]
             [:db.fn/cas tempid :version nil -3]]))))

    (testing "Starts with initial version -4 at creation mode :client-assigned-id"
      (let [tempid (d/tempid :part/Patient)]
        (is
          (=
            (resource-upsert
              db {"Patient" {"0" tempid}} :client-assigned-id
              {"id" "0" "resourceType" "Patient"})
            [[:db/add tempid :Patient/id "0"]
             [:db.fn/cas tempid :version nil -4]]))))

    (testing "Doesn't increment version on empty update"
      (let [[db] (with-resource db "Patient" "0")]
        (is
          (empty?
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0" "resourceType" "Patient"})))))

    (testing "Clear deletion bit on upsert"
      (let [[db id] (with-deleted-resource db "Patient" "0")]
        (is
          (=
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0" "resourceType" "Patient"})
            [[:db.fn/cas id :version -2 -8]]))))

    (testing "Ignores versionId in meta"
      (let [tempid (d/tempid :part/Patient)]
        (is
          (=
            (resource-upsert
              db {"Patient" {"0" tempid}} :server-assigned-id
              {"id" "0" "resourceType" "Patient"
               "meta" {"versionId" "42"}})
            [[:db/add tempid :Patient/id "0"]
             [:db.fn/cas tempid :version nil -3]])))))



  (testing "Adds"

    (testing "primitive single-valued single-typed element"
      (testing "with boolean type"
        (let [[db id] (with-resource db "Patient" "0")]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Patient" "active" true})
              [[:db/add id :Patient/active true]
               [:db.fn/cas id :version -3 -7]]))))

      (testing "with code type"
        (let [[db code-id] (with-code db "male")
              [db id] (with-resource db "Patient" "0")]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Patient" "gender" "male"})
              [[:db/add id :Patient/gender code-id]
               [:db.fn/cas id :version -3 -7]]))))

      (testing "with date type"
        (let [[db id] (with-resource db "Patient" "0")]
          (is
            (=
              (mapv
                read-value
                (resource-upsert
                  db nil :server-assigned-id
                  {"id" "0" "resourceType" "Patient" "birthDate" "2000"}))
              [[:db/add id :Patient/birthDate (Year/of 2000)]
               [:db.fn/cas id :version -3 -7]]))))

      (testing "with unsignedInt type"
        (let [[db id] (with-resource db "CodeSystem" "0")]
          (is
            (=
              (mapv
                read-value
                (resource-upsert
                  db nil :server-assigned-id
                  {"id" "0" "resourceType" "CodeSystem" "count" 1}))
              [[:db/add id :CodeSystem/count 1]
               [:db.fn/cas id :version -3 -7]]))))

      (testing "with base64Binary type"
        (let [[db id] (with-resource db "Patient" "0")]
          (is
            (=
              (mapv
                read-value
                (with-redefs [d/tempid (fn [partition] partition)]
                  (resource-upsert
                    db nil :server-assigned-id
                    {"id" "0" "resourceType" "Patient"
                     "photo" [{"data" "aGFsbG8="}]})))
              [[:db/add :part/Attachment :Attachment/data "aGFsbG8="]
               [:db/add id :Patient/photo :part/Attachment]
               [:db.fn/cas id :version -3 -7]])))))


    (testing "primitive single-valued choice-typed element"
      (testing "with boolean choice"
        (let [[db id] (with-resource db "Patient" "0")]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Patient" "deceasedBoolean" true})
              [[:db/add id :Patient/deceasedBoolean true]
               [:db/add id :Patient/deceased :Patient/deceasedBoolean]
               [:db.fn/cas id :version -3 -7]]))))

      (testing "with dateTime choice"
        (let [[db id] (with-resource db "Patient" "0")]
          (is
            (=
              (mapv
                read-value
                (resource-upsert
                  db nil :server-assigned-id
                  {"id" "0" "resourceType" "Patient" "deceasedDateTime" "2001-01"}))
              [[:db/add id :Patient/deceasedDateTime (YearMonth/of 2001 1)]
               [:db/add id :Patient/deceased :Patient/deceasedDateTime]
               [:db.fn/cas id :version -3 -7]]))))

      (testing "with string choice"
        (let [[db id] (with-resource db "Observation" "0")]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Observation" "valueString" "foo"})
              [[:db/add id :Observation/valueString "foo"]
               [:db/add id :Observation/value :Observation/valueString]
               [:db.fn/cas id :version -3 -7]])))))


    (testing "primitive multi-valued single-typed element"
      (testing "with uri type"
        (let [[db id] (with-resource db "ServiceRequest" "0")]
          (is
            (=
              (with-redefs [d/tempid (fn [partition] partition)]
                (resource-upsert
                  db nil :server-assigned-id
                  {"id" "0"
                   "resourceType" "ServiceRequest"
                   "instantiatesUri" ["foo"]}))
              [[:db/add id :ServiceRequest/instantiatesUri "foo"]
               [:db.fn/cas id :version -3 -7]]))))

      (testing "with code type"
        (let [[db code-id] (with-code db "=")
              [db id] (with-resource db "CodeSystem" "0")]
          (is
            (= (with-redefs [d/tempid (fn [partition] partition)]
                 (resource-upsert
                   db nil :server-assigned-id
                   {"id" "0"
                    "resourceType" "CodeSystem"
                    "filter"
                    [{"operator" ["="]}]}))
               [[:db/add :part/CodeSystem.filter :CodeSystem.filter/operator code-id]
                [:db/add id :CodeSystem/filter :part/CodeSystem.filter]
                [:db.fn/cas id :version -3 -7]])))

        (let [[db code-id] (with-code db "medication")
              [db id] (with-resource db "AllergyIntolerance" "0")]
          (is
            (= (resource-upsert
                 db nil :server-assigned-id
                 {"id" "0"
                  "resourceType" "AllergyIntolerance"
                  "category"
                  ["medication"]})
               [[:db/add id :AllergyIntolerance/category code-id]
                [:db.fn/cas id :version -3 -7]])))))


    (testing "primitive single-valued element in multi-valued backbone element"
      (testing "with boolean type"
        (let [[db id] (with-resource db "Patient" "0")]
          (is
            (=
              (with-redefs [d/tempid (fn [partition] partition)]
                (resource-upsert
                  db nil :server-assigned-id
                  {"id" "0"
                   "resourceType" "Patient"
                   "communication"
                   [{"preferred" true}]}))
              [[:db/add :part/Patient.communication :Patient.communication/preferred true]
               [:db/add id :Patient/communication :part/Patient.communication]
               [:db.fn/cas id :version -3 -7]])))))


    (testing "non-primitive single-valued single-typed element"
      (let [[db id] (with-resource db "Patient" "0")]
        (is
          (= (with-redefs [d/tempid (fn [partition] partition)]
               (resource-upsert
                 db nil :server-assigned-id
                 {"id" "0"
                  "resourceType" "Patient"
                  "maritalStatus" {"text" "married"}}))
             [[:db/add :part/CodeableConcept :CodeableConcept/text "married"]
              [:db/add id :Patient/maritalStatus :part/CodeableConcept]
              [:db.fn/cas id :version -3 -7]]))))


    (testing "non-primitive single-valued choice-typed element"
      (testing "with CodeableConcept choice"
        (let [[db id] (with-resource db "Observation" "0")]
          (is
            (=
              (with-redefs [d/tempid (fn [partition] partition)]
                (resource-upsert
                  db nil :server-assigned-id
                  {"id" "0"
                   "resourceType" "Observation"
                   "valueCodeableConcept" {"text" "foo"}}))
              [[:db/add :part/CodeableConcept :CodeableConcept/text "foo"]
               [:db/add id :Observation/valueCodeableConcept :part/CodeableConcept]
               [:db/add id :Observation/value :Observation/valueCodeableConcept]
               [:db.fn/cas id :version -3 -7]]))))

      (testing "with Period choice"
        (let [[db id] (with-resource db "Observation" "0")]
          (is
            (=
              (with-redefs [d/tempid (fn [partition] partition)]
                (mapv
                  read-value
                  (resource-upsert
                    db nil :server-assigned-id
                    {"id" "0"
                     "resourceType" "Observation"
                     "valuePeriod" {"start" "2019"}})))
              [[:db/add :part/Period :Period/start (Year/of 2019)]
               [:db/add id :Observation/valuePeriod :part/Period]
               [:db/add id :Observation/value :Observation/valuePeriod]
               [:db.fn/cas id :version -3 -7]])))))


    (testing "non-primitive multi-valued single-typed element"
      (let [[db id] (with-resource db "Patient" "0")]
        (is
          (=
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Patient" "name" [{"family" "Doe"}]}))
            [[:db/add :part/HumanName :HumanName/family "Doe"]
             [:db/add id :Patient/name :part/HumanName]
             [:db.fn/cas id :version -3 -7]]))))


    (testing "Coding"
      (testing "without version"
        (let [[db code-id]
              (with-code db "http://terminology.hl7.org/CodeSystem/v3-ActCode" "AMB")
              [db id] (with-resource db "Encounter" "0")]
          (is
            (=
              (with-redefs [d/tempid (fn [partition] partition)]
                (resource-upsert
                  db nil :server-assigned-id
                  {"id" "0"
                   "resourceType" "Encounter"
                   "class"
                   {"system" "http://terminology.hl7.org/CodeSystem/v3-ActCode"
                    "code" "AMB"}}))
              [[:db/add :part/Coding :Coding/code code-id]
               [:db/add id :Encounter/class :part/Coding]
               [:db.fn/cas id :version -3 -7]]))))

      (testing "with version"
        (let [[db code-id]
              (with-code db "http://hl7.org/fhir/sid/icd-10" "2016" "Q14")
              [db id] (with-resource db "Observation" "0")]
          (is
            (=
              (with-redefs [d/tempid (fn [partition] partition)]
                (resource-upsert
                  db nil :server-assigned-id
                  {"id" "0"
                   "resourceType" "Observation"
                   "code"
                   {"coding"
                    [{"system" "http://hl7.org/fhir/sid/icd-10"
                      "version" "2016"
                      "code" "Q14"}]}}))
              [[:db/add :part/Coding :Coding/code code-id]
               [:db/add :part/CodeableConcept :CodeableConcept/coding :part/Coding]
               [:db/add id :Observation/code :part/CodeableConcept]
               [:db/add id :Observation.index/code code-id]
               [:db.fn/cas id :version -3 -7]]))))

      (testing "with userSelected"
        (let [[db code-id]
              (with-code db "http://hl7.org/fhir/sid/icd-10" "2016" "Q14")
              [db id] (with-resource db "Observation" "0")]
          (is
            (=
              (with-redefs [d/tempid (fn [partition] partition)]
                (resource-upsert
                  db nil :server-assigned-id
                  {"id" "0"
                   "resourceType" "Observation"
                   "code"
                   {"coding"
                    [{"system" "http://hl7.org/fhir/sid/icd-10"
                      "version" "2016"
                      "code" "Q14"
                      "userSelected" true}]}}))
              [[:db/add :part/Coding :Coding/userSelected true]
               [:db/add :part/Coding :Coding/code code-id]
               [:db/add :part/CodeableConcept :CodeableConcept/coding :part/Coding]
               [:db/add id :Observation/code :part/CodeableConcept]
               [:db/add id :Observation.index/code code-id]
               [:db.fn/cas id :version -3 -7]])))))

    (testing "CodeSystem with code in concept"
      (let [[db code-id]
            (with-code
              db "http://hl7.org/fhir/administrative-gender" "male")
            [db id] (with-resource db "CodeSystem" "0")]
        (is
          (=
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "CodeSystem"
                 "url" "http://hl7.org/fhir/administrative-gender"
                 "concept"
                 [{"code" "male"}]}))
            [[:db/add id :CodeSystem/url "http://hl7.org/fhir/administrative-gender"]
             [:db/add :part/CodeSystem.concept :CodeSystem.concept/code code-id]
             [:db/add id :CodeSystem/concept :part/CodeSystem.concept]
             [:db.fn/cas id :version -3 -7]]))))


    (testing "CodeSystem with version and code in concept"
      (let [[db code-id]
            (with-code
              db "http://hl7.org/fhir/administrative-gender" "4.0.0" "male")
            [db id] (with-resource db "CodeSystem" "0")]
        (is
          (=
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "CodeSystem"
                 "url" "http://hl7.org/fhir/administrative-gender"
                 "version" "4.0.0"
                 "concept"
                 [{"code" "male"}]}))
            [[:db/add id :CodeSystem/version "4.0.0"]
             [:db/add id :CodeSystem/url "http://hl7.org/fhir/administrative-gender"]
             [:db/add :part/CodeSystem.concept :CodeSystem.concept/code code-id]
             [:db/add id :CodeSystem/concept :part/CodeSystem.concept]
             [:db.fn/cas id :version -3 -7]]))))


    (testing "CodeSystem with code in content uses http://hl7.org/fhir/codesystem-content-mode"
      (let [[db code-id] (with-code db "complete")
            [db id] (with-resource db "CodeSystem" "0")]
        (is
          (=
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0"
               "resourceType" "CodeSystem"
               "url" "http://hl7.org/fhir/administrative-gender"
               "version" "4.0.0"
               "content" "complete"})
            [[:db/add id :CodeSystem/content code-id]
             [:db/add id :CodeSystem/version "4.0.0"]
             [:db/add id :CodeSystem/url "http://hl7.org/fhir/administrative-gender"]
             [:db.fn/cas id :version -3 -7]]))))


    (testing "CodeSystem with sub-concept"
      (let [[db foo-id] (with-code db "http://something" "foo")
            [db bar-id] (with-code db "http://something" "bar")
            [db id] (with-resource db "CodeSystem" "0")]
        (is
          (=
            (with-redefs [d/tempid (tempid)]
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "CodeSystem"
                 "url" "http://something"
                 "concept"
                 [{"code" "foo"
                   "concept"
                   [{"code" "bar"}]}]}))
            [[:db/add id :CodeSystem/url "http://something"]
             [:db/add [:part/CodeSystem.concept 1] :CodeSystem.concept/code foo-id]
             [:db/add [:part/CodeSystem.concept 2] :CodeSystem.concept/code bar-id]
             [:db/add [:part/CodeSystem.concept 1] :CodeSystem/concept [:part/CodeSystem.concept 2]]
             [:db/add id :CodeSystem/concept [:part/CodeSystem.concept 1]]
             [:db.fn/cas id :version -3 -7]]))))


    (testing "ValueSet with code in compose include"
      (let [[db code-id] (with-code db "http://loinc.org" "2.36" "14647-2")
            [db include-id]
            (with-non-primitive
              db :ValueSet.compose.include/system "http://loinc.org"
              :ValueSet.compose.include/version "2.36")
            [db compose-id] (with-non-primitive db :ValueSet.compose/include include-id)
            [db id] (with-resource db "ValueSet" "0" :ValueSet/compose compose-id)]
        (is
          (=
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "ValueSet"
                 "compose"
                 {"include"
                  [{"system" "http://loinc.org"
                    "version" "2.36"
                    "concept"
                    [{"code" "14647-2"}]}]}}))
            [[:db/add :part/ValueSet.compose.include.concept :ValueSet.compose.include.concept/code code-id]
             [:db/add include-id :ValueSet.compose.include/concept :part/ValueSet.compose.include.concept]
             [:db.fn/cas id :version -3 -7]]))))


    (testing "ValueSet with code in expansion contains"
      (let [[db code-id] (with-code db "http://loinc.org" "2.50" "14647-2")
            [db contains-id]
            (with-non-primitive
              db :ValueSet.expansion.contains/system "http://loinc.org"
              :ValueSet.expansion.contains/version "2.50")
            [db expansion-id] (with-non-primitive db :ValueSet.expansion/contains contains-id)
            [db id] (with-resource db "ValueSet" "0" :ValueSet/expansion expansion-id)]
        (is
          (=
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0"
               "resourceType" "ValueSet"
               "expansion"
               {"contains"
                [{"system" "http://loinc.org"
                  "version" "2.50"
                  "code" "14647-2"}]}})
            [[:db/add contains-id :ValueSet.expansion.contains/code code-id]
             [:db.fn/cas id :version -3 -7]]))))


    (testing "special Quantity type"
      (let [[db id] (with-resource db "Observation" "0")]
        (is
          (=
            (mapv
              read-value
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "Observation"
                 "valueQuantity"
                 {"value" 1M
                  "system" "http://unitsofmeasure.org"
                  "code" "m"}}))
            [[:db/add id :Observation/valueQuantity (quantity 1M "m")]
             [:db/add id :Observation/value :Observation/valueQuantity]
             [:db.fn/cas id :version -3 -7]]))))


    (testing "special Quantity type with integer decimal value"
      (let [[db id] (with-resource db "Observation" "0")]
        (is
          (=
            (mapv
              read-value
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "Observation"
                 "valueQuantity"
                 {"value" 1
                  "system" "http://unitsofmeasure.org"
                  "code" "m"}}))
            [[:db/add id :Observation/valueQuantity (quantity 1M "m")]
             [:db/add id :Observation/value :Observation/valueQuantity]
             [:db.fn/cas id :version -3 -7]]))))


    (testing "special Quantity type with unit in unit"
      (let [[db id] (with-resource db "Observation" "0")]
        (is
          (=
            (mapv
              read-value
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "Observation"
                 "valueQuantity"
                 {"value" 1 "unit" "a"}}))
            [[:db/add id :Observation/valueQuantity (quantity 1M "a")]
             [:db/add id :Observation/value :Observation/valueQuantity]
             [:db.fn/cas id :version -3 -7]]))))


    (testing "single-valued special Reference type"
      (testing "with resource resolvable in db"
        (let [[db patient-id] (with-resource db "Patient" "0")
              [db observation-id] (with-resource db "Observation" "0")]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "Observation"
                 "subject" {"reference" "Patient/0"}})
              [[:db/add observation-id :Observation/subject patient-id]
               [:db.fn/cas observation-id :version -3 -7]]))))

      (testing "with resource resolvable in tempids"
        (let [patient-id (d/tempid :part/Patient)
              [db observation-id] (with-resource db "Observation" "0")]
          (is
            (=
              (resource-upsert
                db {"Patient" {"0" patient-id}} :server-assigned-id
                {"id" "0"
                 "resourceType" "Observation"
                 "subject" {"reference" "Patient/0"}})
              [[:db/add observation-id :Observation/subject patient-id]
               [:db.fn/cas observation-id :version -3 -7]])))))


    (testing "multi-valued special Reference type"
      (let [[db organization-id] (with-resource db "Organization" "0")
            [db patient-id] (with-resource db "Patient" "0")]
        (is
          (=
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0"
               "resourceType" "Patient"
               "generalPractitioner"
               [{"reference" "Organization/0"}]})
            [[:db/add patient-id :Patient/generalPractitioner organization-id]
             [:db.fn/cas patient-id :version -3 -7]]))))


    (testing "Contact"
      (let [[db id] (with-resource db "Patient" "0")]
        (is
          (=
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "Patient"
                 "contact"
                 [{"name" {"family" "Doe"}}]}))
            [[:db/add :part/HumanName :HumanName/family "Doe"]
             [:db/add :part/Patient.contact :Patient.contact/name :part/HumanName]
             [:db/add id :Patient/contact :part/Patient.contact]
             [:db.fn/cas id :version -3 -7]]))))


    (testing "Contained resources"
      (let [[db id] (with-resource db "Observation" "0")]
        (is
          (=
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "Observation"
                 "subject" {"reference" "#0"}
                 "contained"
                 [{"id" "0"
                   "resourceType" "Patient"
                   "active" true}]}))
            [[:db/add :part/Patient :Patient/active true]
             [:db/add :part/Patient :local-id "0"]
             [:db/add id :Observation/contained :part/Patient]
             [:db/add id :Observation/subject :part/Patient]
             [:db.fn/cas id :version -3 -7]]))))


    (testing "ConceptMap with source code"
      (let [[db code-id] (with-code db "http://foo" "bar")
            [db group-id] (with-non-primitive db :ConceptMap.group/source "http://foo")
            [db id] (with-resource db "ConceptMap" "0" :ConceptMap/group group-id)]
        (is
          (=
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "ConceptMap"
                 "group"
                 [{"source" "http://foo"
                   "element"
                   [{"code" "bar"}]}]}))
            [[:db/add :part/ConceptMap.group.element :ConceptMap.group.element/code code-id]
             [:db/add group-id :ConceptMap.group/element :part/ConceptMap.group.element]
             [:db.fn/cas id :version -3 -7]]))))


    (testing "ConceptMap with target code"
      (let [[db code-id] (with-code db "http://foo" "bar")
            [db id] (with-resource db "ConceptMap" "0")]
        (is
          (=
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "ConceptMap"
                 "group"
                 [{"target" "http://foo"
                   "element"
                   [{"target"
                     [{"code" "bar"}]}]}]}))
            [[:db/add :part/ConceptMap.group.element.target :ConceptMap.group.element.target/code code-id]
             [:db/add :part/ConceptMap.group.element :ConceptMap.group.element/target :part/ConceptMap.group.element.target]
             [:db/add :part/ConceptMap.group :ConceptMap.group/element :part/ConceptMap.group.element]
             [:db/add :part/ConceptMap.group :ConceptMap.group/target "http://foo"]
             [:db/add id :ConceptMap/group :part/ConceptMap.group]
             [:db.fn/cas id :version -3 -7]]))))

    (testing "Code typed extension"
      ;; TODO: resolve the value set binding here
      (let [[db draft-id] (with-code db "draft")
            [db extension-id] (with-non-primitive db :Extension/url "http://foo")
            [db id] (with-resource db "CodeSystem" "0" :CodeSystem/extension extension-id)]
        (is
          (=
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0"
               "resourceType" "CodeSystem"
               "extension"
               [{"url" "http://foo"
                 "valueCode" "draft"}]})
            [[:db/add extension-id :Extension/valueCode draft-id]
             [:db/add extension-id :Extension/value :Extension/valueCode]
             [:db.fn/cas id :version -3 -7]]))))

    (testing "ValueSet compose include system"
      (let [[db id] (with-resource db "ValueSet" "0")]
        (is
          (=
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "ValueSet"
                 "compose"
                 {"include"
                  [{"system" "http://loinc.org"}]}}))
            [[:db/add :part/ValueSet.compose.include :ValueSet.compose.include/system "http://loinc.org"]
             [:db/add :part/ValueSet.compose :ValueSet.compose/include :part/ValueSet.compose.include]
             [:db/add id :ValueSet/compose :part/ValueSet.compose]
             [:db.fn/cas id :version -3 -7]])))))



  (testing "Keeps"

    (testing "primitive single-valued single-typed element"
      (testing "with boolean type"
        (let [[db] (with-resource db "Patient" "0" :Patient/active true)]
          (is
            (empty?
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Patient" "active" true})))))

      (testing "with code type"
        (let [[db id] (with-gender-code db "male")
              [db] (with-resource db "Patient" "0" :Patient/gender id)]
          (is
            (empty?
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Patient" "gender" "male"})))))

      (testing "with date type"
        (let [[db] (with-resource db "Patient" "0" :Patient/birthDate
                                  (value/write (Year/of 2000)))]
          (is
            (empty?
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Patient" "birthDate" "2000"})))))

      (testing "with dateTime type"
        (let [[db]
              (with-resource
                db "CodeSystem" "0"
                :CodeSystem/date (value/write (LocalDate/of 2016 1 28)))]
          (is
            (empty?
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "CodeSystem" "date" "2016-01-28"}))))

        (let [[db]
              (with-resource
                db "CodeSystem" "0"
                :CodeSystem/date (value/write (OffsetDateTime/of 2018 12 27 22 37 54 0 (ZoneOffset/ofHours 11))))]
          (is
            (empty?
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "CodeSystem"
                 "date" "2018-12-27T22:37:54+11:00"}))))

        ;; TODO: doesn't work because of Zulu printing or the other way around
        (comment
          (let [[db id]
                (with-resource
                  db "CodeSystem" "0"
                  :CodeSystem/date (value/write (OffsetDateTime/of 2018 06 05 14 06 2 0 (ZoneOffset/ofHours 0))))]
            (is
              (empty?
                (resource-upsert
                  db nil :server-assigned-id
                  {"id" "0" "resourceType" "CodeSystem"
                   "date" "2018-06-05T14:06:02+00:00"})))))))


    (testing "primitive single-valued choice-typed element"
      (testing "with string choice"
        (let [[db]
              (with-resource
                db "Observation" "0"
                :Observation/valueString "foo"
                :Observation/value :Observation/valueString)]
          (is
            (empty?
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Observation" "valueString" "foo"}))))))


    (testing "non-primitive single-valued choice-typed element"
      (testing "with CodeableConcept choice"
        (let [[db id] (with-non-primitive db :CodeableConcept/text "foo")
              [db]
              (with-resource db "Observation" "0"
                             :Observation/valueCodeableConcept id
                             :Observation/value :Observation/valueCodeableConcept)]
          (is
            (empty?
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "Observation"
                 "valueCodeableConcept" {"text" "foo"}}))))))


    (testing "primitive multi-valued single-typed element"
      (testing "with uri type"
        (let [[db]
              (with-resource
                db "ServiceRequest" "0" :ServiceRequest/instantiatesUri "foo")]
          (is
            (empty?
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "ServiceRequest"
                 "instantiatesUri" ["foo"]})))))

      (testing "with code type"
        (let [[db id] (with-code db "medication")
              [db] (with-resource db "AllergyIntolerance" "0"
                                  :AllergyIntolerance/category id)]
          (is
            (empty?
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "AllergyIntolerance"
                 "category"
                 ["medication"]}))))))


    (testing "non-primitive multi-valued single-typed element"
      (let [[db id] (with-non-primitive db :HumanName/family "Doe")
            [db] (with-resource db "Patient" "0" :Patient/name id)]
        (is
          (empty?
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0" "resourceType" "Patient" "name" [{"family" "Doe"}]})))))


    (testing "Coding"
      (testing "with version"
        (let [[db id] (with-icd10-code db "2016" "Q14")
              [db id] (with-non-primitive db :Coding/code id)
              [db id] (with-non-primitive db :CodeableConcept/coding id)
              [db] (with-resource db "Observation" "0" :Observation/code id)]
          (is
            (empty?
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "Observation"
                 "code"
                 {"coding"
                  [{"system" "http://hl7.org/fhir/sid/icd-10"
                    "version" "2016"
                    "code" "Q14"}]}})))))

      (testing "with userSelected"
        (let [[db id] (with-icd10-code db "2016" "Q14")
              [db id] (with-non-primitive db :Coding/code id :Coding/userSelected true)
              [db id] (with-non-primitive db :CodeableConcept/coding id)
              [db] (with-resource db "Observation" "0" :Observation/code id)]
          (is
            (empty?
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "Observation"
                 "code"
                 {"coding"
                  [{"system" "http://hl7.org/fhir/sid/icd-10"
                    "version" "2016"
                    "code" "Q14"
                    "userSelected" true}]}}))))))


    (testing "special Quantity type"
      (let [[db]
            (with-resource
              db "Observation" "0"
              :Observation/valueQuantity (value/write (quantity 1M "m"))
              :Observation/value :Observation/valueQuantity)]
        (is
          (empty?
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0"
               "resourceType" "Observation"
               "valueQuantity" {"value" 1M "system" "http://unitsofmeasure.org" "code" "m"}})))))


    (testing "single-valued special Reference type"
      (let [[db id] (with-resource db "Patient" "0")
            [db] (with-resource db "Observation" "0" :Observation/subject id)]
        (is
          (empty?
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0"
               "resourceType" "Observation"
               "subject" {"reference" "Patient/0"}})))))


    (testing "CodeSystem with contact"
      (let [[db id] (with-code db "http://hl7.org/fhir/contact-point-system" "url")
            [db id] (with-non-primitive db :ContactPoint/system id)
            [db id] (with-non-primitive db :ContactDetail/telecom id)
            [db] (with-resource db "CodeSystem" "0" :CodeSystem/contact id)]
        (is
          (empty?
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0"
               "resourceType" "CodeSystem"
               "contact"
               [{"telecom"
                 [{"system" "url"}]}]})))))


    (testing "Contained resources"
      (let [[db id] (with-non-primitive db :Patient/active true :local-id "0")
            [db] (with-resource db "Patient" "0" :Patient/contained id)]
        (is
          (empty?
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0"
               "resourceType" "Patient"
               "contained"
               [{"id" "0"
                 "resourceType" "Patient"
                 "active" true}]})))))


    (testing "ignores display on Reference"
      (let [[db actor-id] (with-resource db "Location" "0")
            [db] (with-resource db "Schedule" "0" :Schedule/actor actor-id)]
        (is
          (empty?
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "Schedule"
                 "actor"
                 [{"reference" "Location/0"
                   "display" "foo"}]})))))))



  (testing "Updates"

    (testing "primitive single-valued single-typed element"
      (testing "with boolean type"
        (let [[db id] (with-resource db "Patient" "0" :Patient/active false)]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Patient" "active" true})
              [[:db/add id :Patient/active true]
               [:db.fn/cas id :version -3 -7]]))))

      (testing "with code type"
        (let [[db male-id] (with-code db "male")
              [db female-id] (with-code db "female")
              [db id] (with-resource db "Patient" "0" :Patient/gender male-id)]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Patient" "gender" "female"})
              [[:db/add id :Patient/gender female-id]
               [:db.fn/cas id :version -3 -7]]))))

      (testing "with date type"
        (let [[db id] (with-resource db "Patient" "0" :Patient/birthDate
                                     (value/write (Year/of 2000)))]
          (is
            (=
              (mapv
                read-value
                (resource-upsert
                  db nil :server-assigned-id
                  {"id" "0" "resourceType" "Patient" "birthDate" "2001"}))
              [[:db/add id :Patient/birthDate (Year/of 2001)]
               [:db.fn/cas id :version -3 -7]])))))


    (testing "primitive multi-valued single-typed element"
      (testing "with one value"
        (let [[db id]
              (with-resource
                db "ServiceRequest" "0" :ServiceRequest/instantiatesUri "foo")]
          (is
            (= (resource-upsert
                 db nil :server-assigned-id
                 {"id" "0"
                  "resourceType" "ServiceRequest"
                  "instantiatesUri" ["bar"]})
               [[:db/retract id :ServiceRequest/instantiatesUri "foo"]
                [:db/add id :ServiceRequest/instantiatesUri "bar"]
                [:db.fn/cas id :version -3 -7]]))))

      (testing "with multiple values"
        (let [[db id]
              (with-resource
                db "ServiceRequest" "0"
                :ServiceRequest/instantiatesUri #{"one" "two" "three"})]
          (is
            (= (resource-upsert
                 db nil :server-assigned-id
                 {"id" "0"
                  "resourceType" "ServiceRequest"
                  "instantiatesUri" ["one" "TWO" "three"]})
               [[:db/retract id :ServiceRequest/instantiatesUri "two"]
                [:db/add id :ServiceRequest/instantiatesUri "TWO"]
                [:db.fn/cas id :version -3 -7]]))))

      (testing "with code type"
        (let [[db medication-id] (with-code db "medication")
              [db food-id] (with-code db "food")
              [db id] (with-resource db "AllergyIntolerance" "0"
                                     :AllergyIntolerance/category medication-id)]
          (is
            (= (with-redefs [d/tempid (fn [partition] partition)]
                 (resource-upsert
                   db nil :server-assigned-id
                   {"id" "0"
                    "resourceType" "AllergyIntolerance"
                    "category"
                    ["medication" "food"]}))
               [[:db/add id :AllergyIntolerance/category food-id]
                [:db.fn/cas id :version -3 -7]])))))


    (testing "single-valued choice-typed element"
      (testing "with string choice"
        (let [[db id]
              (with-resource db "Observation" "0"
                             :Observation/valueString "foo"
                             :Observation/value :Observation/valueString)]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Observation" "valueString" "bar"})
              [[:db/add id :Observation/valueString "bar"]
               [:db/add id :Observation/value :Observation/valueString]
               [:db.fn/cas id :version -3 -7]]))))

      (testing "switch from string choice to boolean choice"
        (let [[db id]
              (with-resource db "Observation" "0"
                             :Observation/valueString "foo"
                             :Observation/value :Observation/valueString)]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Observation" "valueBoolean" true})
              [[:db/retract id :Observation/valueString "foo"]
               [:db/add id :Observation/valueBoolean true]
               [:db/add id :Observation/value :Observation/valueBoolean]
               [:db.fn/cas id :version -3 -7]]))))

      (testing "switch from string choice to CodeableConcept choice"
        (let [[db id]
              (with-resource db "Observation" "0"
                             :Observation/valueString "foo"
                             :Observation/value :Observation/valueString)]
          (is
            (=
              (with-redefs [d/tempid (fn [partition] partition)]
                (resource-upsert
                  db nil :server-assigned-id
                  {"id" "0" "resourceType"
                   "Observation" "valueCodeableConcept" {"text" "bar"}}))
              [[:db/retract id :Observation/valueString "foo"]
               [:db/add :part/CodeableConcept :CodeableConcept/text "bar"]
               [:db/add id :Observation/valueCodeableConcept :part/CodeableConcept]
               [:db/add id :Observation/value :Observation/valueCodeableConcept]
               [:db.fn/cas id :version -3 -7]])))))


    (testing "non-primitive single-valued single-typed element"
      (let [[db status-id]
            (with-non-primitive db :CodeableConcept/text "married")
            [db id]
            (with-resource db "Patient" "0" :Patient/maritalStatus status-id)]
        (is
          (=
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0"
               "resourceType" "Patient"
               "maritalStatus" {"text" "unmarried"}})
            [[:db/add status-id :CodeableConcept/text "unmarried"]
             [:db.fn/cas id :version -3 -7]]))))


    (testing "non-primitive multi-valued single-typed element"
      (testing "with primitive single-valued single-typed child element"
        (let [[db name-id] (with-non-primitive db :HumanName/family "foo")
              [db patient-id]
              (with-resource db "Patient" "0" :Patient/name name-id)]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Patient" "name" [{"family" "bar"}]})
              [[:db/add name-id :HumanName/family "bar"]
               [:db.fn/cas patient-id :version -3 -7]]))))

      (testing "with primitive multi-valued single-typed child element"
        (let [[db name-id] (with-non-primitive db :HumanName/given "foo")
              [db patient-id]
              (with-resource db "Patient" "0" :Patient/name name-id)]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Patient" "name" [{"given" ["bar"]}]})
              [[:db/retract name-id :HumanName/given "foo"]
               [:db/add name-id :HumanName/given "bar"]
               [:db.fn/cas patient-id :version -3 -7]])))

        (let [[db name-id] (with-non-primitive db :HumanName/given "foo")
              [db patient-id]
              (with-resource db "Patient" "0" :Patient/name name-id)]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Patient" "name" [{"given" ["foo" "bar"]}]})
              [[:db/add name-id :HumanName/given "bar"]
               [:db.fn/cas patient-id :version -3 -7]])))))

    (testing "Coding"
      (let [[db amb-id]
            (with-code
              db "http://terminology.hl7.org/CodeSystem/v3-ActCode" "AMB")
            [db emer-id]
            (with-code
              db "http://terminology.hl7.org/CodeSystem/v3-ActCode" "EMER")
            [db coding-id] (with-non-primitive db :Coding/code amb-id)
            [db encounter-id]
            (with-resource db "Encounter" "0" :Encounter/class coding-id)]
        (is
          (=
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "Encounter"
                 "class"
                 {"system" "http://terminology.hl7.org/CodeSystem/v3-ActCode"
                  "code" "EMER"}}))
            [[:db/add coding-id :Coding/code emer-id]
             [:db.fn/cas encounter-id :version -3 -7]]))))


    (testing "single-valued special Reference type"
      (let [[db patient-0-id] (with-resource db "Patient" "0")
            [db patient-1-id] (with-resource db "Patient" "1")
            [db observation-id] (with-resource db "Observation" "0" :Observation/subject patient-0-id)]
        (is
          (=
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0"
               "resourceType" "Observation"
               "subject" {"reference" "Patient/1"}})
            [[:db/add observation-id :Observation/subject patient-1-id]
             [:db.fn/cas observation-id :version -3 -7]]))))


    (testing "Contained resources"
      (testing "with changes inside the contained resource"
        (let [[db contained-id] (with-non-primitive db :Patient/active false
                                                    :local-id "0")
              [db id] (with-resource db "Observation" "0"
                                     :Observation/contained contained-id
                                     :Observation/subject contained-id)]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "Observation"
                 "subject" {"reference" "#0"}
                 "contained"
                 [{"id" "0"
                   "resourceType" "Patient"
                   "active" true}]})
              [[:db/add contained-id :Patient/active true]
               [:db.fn/cas id :version -3 -7]]))))

      (testing "with changes inside the container resource"
        (let [[db contained-id] (with-non-primitive db :Patient/active true
                                                    :local-id "0")
              [db preliminary-id] (with-code db "preliminary")
              [db final-id] (with-code db "final")
              [db id] (with-resource db "Observation" "0"
                                     :Observation/status preliminary-id
                                     :Observation/contained contained-id
                                     :Observation/subject contained-id)]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "Observation"
                 "subject" {"reference" "#0"}
                 "status" "final"
                 "contained"
                 [{"id" "0"
                   "resourceType" "Patient"
                   "active" true}]})
              [[:db/add id :Observation/status final-id]
               [:db.fn/cas id :version -3 -7]])))))


    (testing "Don't reuse old entities or new entities more than once"
      (let [[db component-1-id]
            (with-non-primitive
              db
              :Observation.component/valueQuantity (value/write (quantity 1M ""))
              :Observation.component/value :Observation.component/valueQuantity)
            [db component-2-id]
            (with-non-primitive
              db
              :Observation.component/valueQuantity (value/write (quantity 2M ""))
              :Observation.component/value :Observation.component/valueQuantity)
            [db observation-id]
            (with-resource db "Observation" "0" :Observation/component
                           #{component-1-id component-2-id})]
        (is
          (=
            (mapv
              read-value
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "Observation"
                 "component"
                 [{"valueQuantity"
                   {"value" 1M
                    "unit" "m"}}
                  {"valueQuantity"
                   {"value" 2M
                    "unit" "m"}}]}))
            [[:db/add
              component-2-id
              :Observation.component/valueQuantity
              (quantity 2M "m")]
             [:db/add component-2-id :Observation.component/value :Observation.component/valueQuantity]
             [:db/add
              component-1-id
              :Observation.component/valueQuantity
              (quantity 1M "m")]
             [:db/add component-1-id :Observation.component/value :Observation.component/valueQuantity]
             [:db.fn/cas observation-id :version -3 -7]]))))


    (testing "multi-valued code element"
      (let [[db medication-id] (with-code db "medication")
            [db food-id] (with-code db "food")
            [db id] (with-resource db "AllergyIntolerance" "0"
                                   :AllergyIntolerance/category medication-id)]
        (is
          (= (with-redefs [d/tempid (fn [partition] partition)]
               (resource-upsert
                 db nil :server-assigned-id
                 {"id" "0"
                  "resourceType" "AllergyIntolerance"
                  "category"
                  ["food"]}))
             [[:db/retract id :AllergyIntolerance/category medication-id]
              [:db/add id :AllergyIntolerance/category food-id]
              [:db.fn/cas id :version -3 -7]])))))



  (testing "Retracts"

    (testing "primitive single-valued single-typed element"
      (testing "with boolean type"
        (let [[db id] (with-resource db "Patient" "0" :Patient/active false)]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Patient"})
              [[:db/retract id :Patient/active false]
               [:db.fn/cas id :version -3 -7]]))))

      (testing "with code type"
        (let [[db gender-id] (with-gender-code db "male")
              [db patient-id]
              (with-resource db "Patient" "0" :Patient/gender gender-id)]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Patient"})
              [[:db/retract patient-id :Patient/gender gender-id]
               [:db.fn/cas patient-id :version -3 -7]]))))

      (testing "with date type"
        (let [[db id] (with-resource db "Patient" "0" :Patient/birthDate
                                     (value/write (Year/of 2000)))]
          (is
            (=
              (mapv
                read-value
                (resource-upsert
                  db nil :server-assigned-id
                  {"id" "0" "resourceType" "Patient"}))
              [[:db/retract id :Patient/birthDate (Year/of 2000)]
               [:db.fn/cas id :version -3 -7]])))))

    (testing "non-primitive single-valued single-typed element"
      (let [[db status-id]
            (with-non-primitive db :CodeableConcept/text "married")
            [db patient-id]
            (with-resource db "Patient" "0" :Patient/maritalStatus status-id)]
        (is
          (=
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0" "resourceType" "Patient"})
            [[:db/retract status-id :CodeableConcept/text "married"]
             [:db/retract patient-id :Patient/maritalStatus status-id]
             [:db.fn/cas patient-id :version -3 -7]]))))


    (testing "non-primitive multi-valued element"
      (let [[db name-id] (with-non-primitive db :HumanName/family "Doe")
            [db patient-id] (with-resource db "Patient" "0" :Patient/name name-id)]
        (is
          (=
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0" "resourceType" "Patient" "name" []})
            [[:db/retract name-id :HumanName/family "Doe"]
             [:db/retract patient-id :Patient/name name-id]
             [:db.fn/cas patient-id :version -3 -7]])))

      (let [[db name-id] (with-non-primitive db :HumanName/family "Doe")
            [db patient-id] (with-resource db "Patient" "0" :Patient/name name-id)]
        (is
          (=
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0" "resourceType" "Patient"})
            [[:db/retract name-id :HumanName/family "Doe"]
             [:db/retract patient-id :Patient/name name-id]
             [:db.fn/cas patient-id :version -3 -7]]))))


    (testing "single-valued choice-typed element"
      (let [[db id]
            (with-resource db "Observation" "0"
                           :Observation/valueString "foo"
                           :Observation/value :Observation/valueString)]
        (is
          (=
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0" "resourceType" "Observation"})
            [[:db/retract id :Observation/valueString "foo"]
             [:db/retract id :Observation/value :Observation/valueString]
             [:db.fn/cas id :version -3 -7]]))))


    (testing "primitive single-valued element in single-valued backbone element"
      (let [[db software-id]
            (with-non-primitive
              db :TerminologyCapabilities.software/name "foo")
            [db capabilities-id]
            (with-resource
              db "TerminologyCapabilities" "0"
              :TerminologyCapabilities/software software-id)]
        (is
          (=
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0"
               "resourceType" "TerminologyCapabilities"})
            [[:db/retract software-id :TerminologyCapabilities.software/name "foo"]
             [:db/retract capabilities-id :TerminologyCapabilities/software software-id]
             [:db.fn/cas capabilities-id :version -3 -7]]))))


    (testing "primitive single-valued element in multi-valued backbone element"
      (let [[db communication-id]
            (with-non-primitive db :Patient.communication/preferred true)
            [db patient-id]
            (with-resource
              db "Patient" "0" :Patient/communication communication-id)]
        (is
          (=
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0"
               "resourceType" "Patient"})
            [[:db/retract communication-id :Patient.communication/preferred true]
             [:db/retract patient-id :Patient/communication communication-id]
             [:db.fn/cas patient-id :version -3 -7]]))))


    (testing "Coding"
      (let [[db code-id]
            (with-code db "http://terminology.hl7.org/CodeSystem/v3-ActCode"
                       "AMB")
            [db coding-id] (with-non-primitive db :Coding/code code-id)
            [db encounter-id]
            (with-resource db "Encounter" "0" :Encounter/class coding-id)]
        (is
          (=
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0" "resourceType" "Encounter"})
            [[:db/retract coding-id :Coding/code code-id]
             [:db/retract encounter-id :Encounter/class coding-id]
             [:db.fn/cas encounter-id :version -3 -7]]))))


    (testing "Contained resources"
      (testing "retracts all contained resources"
        (let [[db contained-1-id] (with-non-primitive db :Patient/active true
                                                      :local-id "1")
              [db contained-2-id] (with-non-primitive db :Patient/active false
                                                      :local-id "2")
              [db id] (with-resource db "Patient" "0" :Patient/contained
                                     #{contained-1-id contained-2-id})]
          (is
            (=
              (set
                (resource-upsert
                  db nil :server-assigned-id
                  {"id" "0"
                   "resourceType" "Patient"}))
              #{[:db/retract contained-1-id :Patient/active true]
                [:db/retract contained-1-id :local-id "1"]
                [:db/retract id :Patient/contained contained-1-id]
                [:db/retract contained-2-id :Patient/active false]
                [:db/retract contained-2-id :local-id "2"]
                [:db/retract id :Patient/contained contained-2-id]
                [:db.fn/cas id :version -3 -7]}))))))



  (testing "Fails"

    (testing "on non-existing reference target"
      (let [[db] (with-resource db "Observation" "0")]
        (try
          (resource-upsert
            db nil :server-assigned-id
            {"id" "0"
             "resourceType" "Observation"
             "subject" {"reference" "Patient/0"}})
          (catch Exception e
            (given (ex-data e)
              ::anom/category := ::anom/incorrect)))))))


(deftest resource-deletion-test
  (testing "Decrements version and sets deletion bit"
    (let [[db id] (with-resource db "Patient" "0")]
      (is
        (=
          (resource-deletion db "Patient" "0")
          [[:db.fn/cas id :version -3 -5]]))))

  (testing "Does nothing on subsequent delete"
    (let [[db] (with-deleted-resource db "Patient" "0")]
      (is (empty? (resource-deletion db "Patient" "0")))))


  (testing "Retracts"

    (testing "primitive single-valued single-typed element"
      (testing "with boolean type"
        (let [[db id] (with-resource db "Patient" "0" :Patient/active true)]
          (is
            (=
              (resource-deletion db "Patient" "0")
              [[:db.fn/cas id :version -3 -5]
               [:db/retract id :Patient/active true]]))))

      (testing "with code type"
        (let [[db gender-id] (with-gender-code db "male")
              [db patient-id]
              (with-resource db "Patient" "0" :Patient/gender gender-id)]
          (is
            (=
              (resource-deletion db "Patient" "0")
              [[:db.fn/cas patient-id :version -3 -5]
               [:db/retract patient-id :Patient/gender gender-id]])))))

    (testing "primitive multi-valued single-typed element"
      (testing "with one value"
        (let [[db id]
              (with-resource
                db "ServiceRequest" "0" :ServiceRequest/instantiatesUri "foo")]
          (is
            (= (resource-deletion db "ServiceRequest" "0")
               [[:db.fn/cas id :version -3 -5]
                [:db/retract id :ServiceRequest/instantiatesUri "foo"]]))))

      (testing "with multiple values"
        (let [[db id]
              (with-resource
                db "ServiceRequest" "0"
                :ServiceRequest/instantiatesUri #{"one" "two"})]
          (is
            (= (set (resource-deletion db "ServiceRequest" "0"))
               #{[:db.fn/cas id :version -3 -5]
                 [:db/retract id :ServiceRequest/instantiatesUri "one"]
                 [:db/retract id :ServiceRequest/instantiatesUri "two"]}))))

      (testing "with code type"
        (let [[db medication-id] (with-code db "medication")
              [db id]
              (with-resource
                db "AllergyIntolerance" "0"
                :AllergyIntolerance/category medication-id)]
          (is
            (= (resource-deletion db "AllergyIntolerance" "0")
               [[:db.fn/cas id :version -3 -5]
                [:db/retract id :AllergyIntolerance/category medication-id]])))))

    (testing "non-primitive single-valued single-typed element"
      (let [[db status-id]
            (with-non-primitive db :CodeableConcept/text "married")
            [db patient-id]
            (with-resource db "Patient" "0" :Patient/maritalStatus status-id)]
        (is
          (=
            (resource-deletion db "Patient" "0")
            [[:db.fn/cas patient-id :version -3 -5]
             [:db/retract status-id :CodeableConcept/text "married"]
             [:db/retract patient-id :Patient/maritalStatus status-id]]))))

    (testing "non-primitive multi-valued element"
      (let [[db name-id] (with-non-primitive db :HumanName/family "Doe")
            [db patient-id] (with-resource db "Patient" "0" :Patient/name name-id)]
        (is
          (=
            (resource-deletion db "Patient" "0")
            [[:db.fn/cas patient-id :version -3 -5]
             [:db/retract name-id :HumanName/family "Doe"]
             [:db/retract patient-id :Patient/name name-id]]))))))


(deftest resource-codes-creation-test
  (testing "single-valued code element"
    (is
      (=
        (with-redefs [d/tempid (fn [partition] partition)]
          (resource-codes-creation
            db
            {"id" "0" "resourceType" "Patient" "gender" "male"}))
        [{:db/id :part/code
          :code/id "||male"
          :code/code "male"}])))

  (testing "multi-valued code element"
    (is
      (= (with-redefs [d/tempid (fn [partition] partition)]
           (resource-codes-creation
             db
             {"id" "0"
              "resourceType" "AllergyIntolerance"
              "category"
              ["medication"]}))
         [{:db/id :part/code
           :code/id "||medication"
           :code/code "medication"}])))

  (testing "single-valued Coding element"
    (is
      (=
        (with-redefs [d/tempid (fn [partition] partition)]
          (resource-codes-creation
            db
            {"id" "0"
             "resourceType" "Encounter"
             "class"
             {"system" "http://terminology.hl7.org/CodeSystem/v3-ActCode"
              "code" "AMB"}}))
        [{:db/id :part/code
          :code/id "http://terminology.hl7.org/CodeSystem/v3-ActCode||AMB"
          :code/system "http://terminology.hl7.org/CodeSystem/v3-ActCode"
          :code/code "AMB"}])))

  (testing "multi-valued code element in multi-valued Backbone element"
    (is
      (= (with-redefs [d/tempid (fn [partition] partition)]
           (resource-codes-creation
             db
             {"id" "0"
              "resourceType" "CodeSystem"
              "filter"
              [{"operator" ["="]}]}))
         [{:db/id :part/code
           :code/id "||="
           :code/code "="}])))

  (testing "Coding in CodeableConcept"
    (is
      (=
        (with-redefs [d/tempid (fn [partition] partition)]
          (resource-codes-creation
            db
            {"id" "0"
             "resourceType" "Observation"
             "code"
             {"coding"
              [{"system" "http://hl7.org/fhir/sid/icd-10"
                "version" "2016"
                "code" "Q14"}]}}))
        [{:db/id :part/code
          :code/id "http://hl7.org/fhir/sid/icd-10|2016|Q14"
          :code/system "http://hl7.org/fhir/sid/icd-10"
          :code/version "2016"
          :code/code "Q14"}])))

  (testing "CodeSystem with code in concept"
    (is
      (=
        (with-redefs [d/tempid (fn [partition] partition)]
          (resource-codes-creation
            db
            {"id" "0"
             "resourceType" "CodeSystem"
             "url" "http://hl7.org/fhir/administrative-gender"
             "concept"
             [{"code" "male"}]}))
        [{:db/id :part/code
          :code/id "http://hl7.org/fhir/administrative-gender||male"
          :code/system "http://hl7.org/fhir/administrative-gender"
          :code/code "male"}])))

  (testing "CodeSystem with version and code in concept"
    (is
      (=
        (with-redefs [d/tempid (fn [partition] partition)]
          (resource-codes-creation
            db
            {"id" "0"
             "resourceType" "CodeSystem"
             "url" "http://hl7.org/fhir/administrative-gender"
             "version" "4.0.0"
             "concept"
             [{"code" "male"}]}))
        [{:db/id :part/code
          :code/id "http://hl7.org/fhir/administrative-gender|4.0.0|male"
          :code/system "http://hl7.org/fhir/administrative-gender"
          :code/version "4.0.0"
          :code/code "male"}])))

  (testing "ConceptMap with source code"
    (is
      (=
        (with-redefs [d/tempid (fn [partition] partition)]
          (resource-codes-creation
            db
            {"id" "0"
             "resourceType" "ConceptMap"
             "group"
             [{"source" "http://foo"
               "element"
               [{"code" "bar"}]}]}))
        [{:db/id :part/code
          :code/id "http://foo||bar"
          :code/system "http://foo"
          :code/code "bar"}])))

  (testing "ConceptMap with target code"
    (is
      (=
        (with-redefs [d/tempid (fn [partition] partition)]
          (resource-codes-creation
            db
            {"id" "0"
             "resourceType" "ConceptMap"
             "group"
             [{"target" "http://foo"
               "element"
               [{"target"
                 [{"code" "bar"}]}]}]}))
        [{:db/id :part/code
          :code/id "http://foo||bar"
          :code/system "http://foo"
          :code/code "bar"}])))

  (testing "Code typed extension"
    ;; TODO: resolve the value set binding here
    (is
      (=
        (with-redefs [d/tempid (fn [partition] partition)]
          (resource-codes-creation
            db
            {"id" "0"
             "resourceType" "CodeSystem"
             "extension"
             [{"url" "http://foo"
               "valueCode" "draft"}]}))
        [{:db/id :part/code
          :code/id "||draft"
          :code/code "draft"}]))))


(deftest coerce-value-test
  (testing "date with year precision"
    (is (= (Year/of 2019) (value/read (coerce-value {:element/type-code "date"} "2019")))))

  (testing "date with year-month precision"
    (is (= (YearMonth/of 2019 1) (value/read (coerce-value {:element/type-code "date"} "2019-01")))))

  (testing "date"
    (is (= (LocalDate/of 2019 2 3) (value/read (coerce-value {:element/type-code "date"} "2019-02-03")))))

  (testing "dateTime with year precision"
    (is (= (Year/of 2019) (value/read (coerce-value {:element/type-code "dateTime"} "2019")))))

  (testing "dateTime with year-month precision"
    (is (= (YearMonth/of 2019 1) (value/read (coerce-value {:element/type-code "dateTime"} "2019-01")))))

  (testing "dateTime with date precision"
    (is (= (LocalDate/of 2019 2 3) (value/read (coerce-value {:element/type-code "dateTime"} "2019-02-03")))))

  (testing "dateTime without timezone"
    (is (= (LocalDateTime/of 2019 2 3 12 13 14) (value/read (coerce-value {:element/type-code "dateTime"} "2019-02-03T12:13:14")))))

  (testing "dateTime with timezone"
    (is (= (OffsetDateTime/of 2019 2 3 12 13 14 0 (ZoneOffset/ofHours 1))
           (value/read (coerce-value {:element/type-code "dateTime"} "2019-02-03T12:13:14+01:00"))))))


(deftest transact-async-test
  (testing "Returns error deferred with anomaly on CAS Failed"
    (let [conn (connect)]
      @(d/transact-async conn [{:Patient/id "0" :version 0}])
      @(d/transact-async conn [[:db.fn/cas [:Patient/id "0"] :version 0 1]])

      @(-> (transact-async conn [[:db.fn/cas [:Patient/id "0"] :version 0 1]])
           (md/catch'
             (fn [{::anom/keys [category]}]
               (is (= ::anom/conflict category))))))))
