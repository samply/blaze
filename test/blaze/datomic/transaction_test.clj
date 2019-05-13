(ns blaze.datomic.transaction-test
  (:require
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [datomic.api :as d]
    [datomic-tools.schema :as dts]
    [juxt.iota :refer [given]]
    [blaze.datomic.schema :as schema]
    [blaze.datomic.quantity :refer [quantity]]
    [blaze.datomic.test-util :refer :all]
    [blaze.datomic.transaction :refer [resource-update coerce-value]]
    [blaze.datomic.value :as value]
    [blaze.structure-definition :refer [read-structure-definitions]])
  (:import
    [java.time Year YearMonth LocalDate LocalDateTime OffsetDateTime ZoneOffset]))


(st/instrument)


(def structure-definitions
  (read-structure-definitions "fhir/r4/structure-definitions"))


(defn- connect []
  (d/delete-database "datomic:mem://datomic.transaction-test")
  (d/create-database "datomic:mem://datomic.transaction-test")
  (let [conn (d/connect "datomic:mem://datomic.transaction-test")]
    @(d/transact conn (dts/schema))
    @(d/transact conn (schema/structure-definition-schemas (vals structure-definitions)))
    conn))


(def db (d/db (connect)))


(comment
  (def db-1 (:db-after (with-code-system db "fhir/r4/code-systems/administrative-gender.json")))
  (d/pull db-1 '[*] [:CodeSystem/id "administrative-gender"])
  (def db (first (with-resource db "Patient" "0")))
  )


(deftest resource-update-test

  (testing "Adds"

    (testing "primitive single-valued single-typed element"
      (testing "with boolean type"
        (let [[db id] (with-resource db "Patient" "0")]
          (is
            (=
              (resource-update
                db
                {"id" "0" "resourceType" "Patient" "active" true})
              [[:db/add id :Patient/active true]]))))

      (testing "with code type"
        (let [[db id] (with-resource db "Patient" "0")]
          (is
            (=
              (with-redefs [d/tempid (fn [partition] partition)]
                (resource-update
                  db
                  {"id" "0" "resourceType" "Patient" "gender" "male"}))
              [{:db/id :part/code
                :code/id "http://hl7.org/fhir/administrative-gender|male"
                :code/system "http://hl7.org/fhir/administrative-gender"
                :code/code "male"}
               [:db/add id :Patient/gender :part/code]]))))

      (testing "with date type"
        (let [[db id] (with-resource db "Patient" "0")]
          (is
            (=
              (mapv
                (fn [[op e a v]] [op e a (value/read v)])
                (resource-update
                  db
                  {"id" "0" "resourceType" "Patient" "birthDate" "2000"}))
              [[:db/add id :Patient/birthDate (Year/of 2000)]])))))


    (testing "primitive single-valued choice-typed element"
      (testing "with boolean choice"
        (let [[db id] (with-resource db "Patient" "0")]
          (is
            (=
              (resource-update
                db
                {"id" "0" "resourceType" "Patient" "deceasedBoolean" true})
              [[:db/add id :Patient/deceasedBoolean true]
               [:db/add id :Patient/deceased :Patient/deceasedBoolean]]))))

      (testing "with dateTime choice"
        (let [[db id] (with-resource db "Patient" "0")]
          (is
            (=
              (mapv
                (fn [[op e a v]] [op e a (value/read v)])
                (resource-update
                  db
                  {"id" "0" "resourceType" "Patient" "deceasedDateTime" "2001-01"}))
              [[:db/add id :Patient/deceasedDateTime (YearMonth/of 2001 1)]
               [:db/add id :Patient/deceased :Patient/deceasedDateTime]]))))

      (testing "with string choice"
        (let [[db id] (with-resource db "Observation" "0")]
          (is
            (=
              (resource-update
                db
                {"id" "0" "resourceType" "Observation" "valueString" "foo"})
              [[:db/add id :Observation/valueString "foo"]
               [:db/add id :Observation/value :Observation/valueString]])))))


    (testing "primitive multi-valued single-typed element"
      (testing "with uri type"
        (let [[db id] (with-resource db "ServiceRequest" "0")]
          (is
            (=
              (with-redefs [d/tempid (fn [partition] partition)]
                (resource-update
                  db
                  {"id" "0"
                   "resourceType" "ServiceRequest"
                   "instantiatesUri" ["foo"]}))
              [[:db/add id :ServiceRequest/instantiatesUri "foo"]])))))


    (testing "primitive multi-valued backbone element"
      (testing "with uri type"
        (let [[db id] (with-resource db "Patient" "0")]
          (is
            (=
              (with-redefs [d/tempid (fn [partition] partition)]
                (resource-update
                  db
                  {"id" "0"
                   "resourceType" "Patient"
                   "communication"
                   [{"preferred" true}]}))
              [[:db/add :part/Patient.communication :Patient.communication/preferred true]
               [:db/add id :Patient/communication :part/Patient.communication]])))))


    (testing "non-primitive single-valued single-typed element"
      (let [[db id] (with-resource db "Patient" "0")]
        (is
          (= (with-redefs [d/tempid (fn [partition] partition)]
               (resource-update
                 db
                 {"id" "0"
                  "resourceType" "Patient"
                  "maritalStatus" {"text" "married"}}))
             [[:db/add :part/CodeableConcept :CodeableConcept/text "married"]
              [:db/add id :Patient/maritalStatus :part/CodeableConcept]]))))


    (testing "non-primitive single-valued choice-typed element"
      (testing "with CodeableConcept choice"
        (let [[db id] (with-resource db "Observation" "0")]
          (is
            (=
              (with-redefs [d/tempid (fn [partition] partition)]
                (resource-update
                  db
                  {"id" "0"
                   "resourceType" "Observation"
                   "valueCodeableConcept" {"text" "foo"}}))
              [[:db/add :part/CodeableConcept :CodeableConcept/text "foo"]
               [:db/add id :Observation/valueCodeableConcept :part/CodeableConcept]
               [:db/add id :Observation/value :Observation/valueCodeableConcept]])))))


    (testing "non-primitive multi-valued single-typed element"
      (let [[db id] (with-resource db "Patient" "0")]
        (is
          (=
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-update
                db
                {"id" "0" "resourceType" "Patient" "name" [{"family" "Doe"}]}))
            [[:db/add :part/HumanName :HumanName/family "Doe"]
             [:db/add id :Patient/name :part/HumanName]]))))


    (testing "Coding"
      (testing "without version"
        (let [[db id] (with-resource db "Encounter" "0")]
          (is
            (=
              (with-redefs [d/tempid (fn [partition] partition)]
                (resource-update
                  db
                  {"id" "0"
                   "resourceType" "Encounter"
                   "class"
                   {"system" "http://terminology.hl7.org/CodeSystem/v3-ActCode"
                    "code" "AMB"}}))
              [{:db/id :part/code
                :code/id "http://terminology.hl7.org/CodeSystem/v3-ActCode|AMB"
                :code/system "http://terminology.hl7.org/CodeSystem/v3-ActCode"
                :code/code "AMB"}
               [:db/add :part/Coding :Coding/code :part/code]
               [:db/add id :Encounter/class :part/Coding]]))))

      (testing "with version"
        (let [[db id] (with-resource db "Observation" "0")]
          (is
            (=
              (with-redefs [d/tempid (fn [partition] partition)]
                (resource-update
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
                :code/code "Q14"}
               [:db/add :part/Coding :Coding/code :part/code]
               [:db/add :part/CodeableConcept :CodeableConcept/coding :part/Coding]
               [:db/add id :Observation/code :part/CodeableConcept]
               [:db/add id :Observation.index/code :part/code]]))))

      (testing "with userSelected"
        (let [[db id] (with-resource db "Observation" "0")]
          (is
            (=
              (with-redefs [d/tempid (fn [partition] partition)]
                (resource-update
                  db
                  {"id" "0"
                   "resourceType" "Observation"
                   "code"
                   {"coding"
                    [{"system" "http://hl7.org/fhir/sid/icd-10"
                      "version" "2016"
                      "code" "Q14"
                      "userSelected" true}]}}))
              [[:db/add :part/Coding :Coding/userSelected true]
               {:db/id :part/code
                :code/id "http://hl7.org/fhir/sid/icd-10|2016|Q14"
                :code/system "http://hl7.org/fhir/sid/icd-10"
                :code/version "2016"
                :code/code "Q14"}
               [:db/add :part/Coding :Coding/code :part/code]
               [:db/add :part/CodeableConcept :CodeableConcept/coding :part/Coding]
               [:db/add id :Observation/code :part/CodeableConcept]
               [:db/add id :Observation.index/code :part/code]])))))


    (testing "special Quantity type"
      (let [[db id] (with-resource db "Observation" "0")]
        (is
          (=
            (mapv
              (fn [[op e a v]] [op e a (value/read v)])
              (resource-update
                db
                {"id" "0"
                 "resourceType" "Observation"
                 "valueQuantity" {"value" 1M "unit" "m"}}))
            [[:db/add id :Observation/valueQuantity (quantity 1M "m")]
             [:db/add id :Observation/value :Observation/valueQuantity]]))))


    (testing "single-valued special Reference type"
      (let [[db id] (with-resource db "Observation" "0")]
        (is
          (=
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-update
                db
                {"id" "0"
                 "resourceType" "Observation"
                 "subject" {"reference" "Patient/0"}}))
            [{:db/id :part/Patient
              :Patient/id "0"}
             [:db/add id :Observation/subject :part/Patient]]))))


    (testing "multi-valued special Reference type"
      (let [[db id] (with-resource db "Patient" "0")]
        (is
          (=
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-update
                db
                {"id" "0"
                 "resourceType" "Patient"
                 "generalPractitioner"
                 [{"reference" "Organization/0"}]}))
            [{:db/id :part/Organization
              :Organization/id "0"}
             [:db/add id :Patient/generalPractitioner :part/Organization]])))))



  (testing "Keeps"

    (testing "primitive single-valued single-typed element"
      (testing "with boolean type"
        (let [[db] (with-resource db "Patient" "0" :Patient/active true)]
          (is
            (=
              (resource-update
                db
                {"id" "0" "resourceType" "Patient" "active" true})
              []))))

      (testing "with code type"
        (let [[db id]
              (with-code db "http://hl7.org/fhir/administrative-gender"
                         "male")
              [db] (with-resource db "Patient" "0" :Patient/gender id)]
          (is
            (=
              (resource-update
                db
                {"id" "0" "resourceType" "Patient" "gender" "male"})
              []))))

      (testing "with date type"
        (let [[db] (with-resource db "Patient" "0" :Patient/birthDate
                                  (value/write (Year/of 2000)))]
          (is
            (=
              (mapv
                (fn [[op e a v]] [op e a (value/read v)])
                (resource-update
                  db
                  {"id" "0" "resourceType" "Patient" "birthDate" "2000"}))
              [])))))


    (testing "primitive single-valued choice-typed element"
      (testing "with string choice"
        (let [[db]
              (with-resource
                db "Observation" "0"
                :Observation/valueString "foo"
                :Observation/value :Observation/valueString)]
          (is
            (=
              (resource-update
                db
                {"id" "0" "resourceType" "Observation" "valueString" "foo"})
              [])))))


    (testing "non-primitive single-valued choice-typed element"
      (testing "with CodeableConcept choice"
        (let [[db id] (with-non-primitive db :CodeableConcept/text "foo")
              [db]
              (with-resource db "Observation" "0"
                             :Observation/valueCodeableConcept id
                             :Observation/value :Observation/valueCodeableConcept)]
          (is
            (=
              (resource-update
                db
                {"id" "0"
                 "resourceType" "Observation"
                 "valueCodeableConcept" {"text" "foo"}})
              [])))))


    (testing "primitive multi-valued single-typed element"
      (testing "with uri type"
        (let [[db]
              (with-resource
                db "ServiceRequest" "0" :ServiceRequest/instantiatesUri "foo")]
          (is
            (= (resource-update
                 db
                 {"id" "0"
                  "resourceType" "ServiceRequest"
                  "instantiatesUri" ["foo"]})
               [])))))


    (testing "non-primitive multi-valued single-typed element"
      (let [[db id] (with-non-primitive db :HumanName/family "Doe")
            [db] (with-resource db "Patient" "0" :Patient/name id)]
        (is
          (=
            (resource-update
              db
              {"id" "0" "resourceType" "Patient" "name" [{"family" "Doe"}]})
            []))))


    (testing "Coding"
      (testing "with version"
        (let [[db id]
              (with-code db "http://hl7.org/fhir/sid/icd-10" "2016" "Q14")
              [db id] (with-non-primitive db :Coding/code id)
              [db id] (with-non-primitive db :CodeableConcept/coding id)
              [db] (with-resource db "Observation" "0" :Observation/code id)]
          (is
            (=
              (with-redefs [d/tempid (fn [partition] partition)]
                (resource-update
                  db
                  {"id" "0"
                   "resourceType" "Observation"
                   "code"
                   {"coding"
                    [{"system" "http://hl7.org/fhir/sid/icd-10"
                      "version" "2016"
                      "code" "Q14"}]}}))
              []))))

      (testing "with userSelected"
        (let [[db id]
              (with-code db "http://hl7.org/fhir/sid/icd-10" "2016" "Q14")
              [db id] (with-non-primitive db :Coding/code id :Coding/userSelected true)
              [db id] (with-non-primitive db :CodeableConcept/coding id)
              [db] (with-resource db "Observation" "0" :Observation/code id)]
          (is
            (=
              (with-redefs [d/tempid (fn [partition] partition)]
                (resource-update
                  db
                  {"id" "0"
                   "resourceType" "Observation"
                   "code"
                   {"coding"
                    [{"system" "http://hl7.org/fhir/sid/icd-10"
                      "version" "2016"
                      "code" "Q14"
                      "userSelected" true}]}}))
              [])))))


    (testing "special Quantity type"
      (let [[db]
            (with-resource
              db "Observation" "0"
              :Observation/valueQuantity (value/write (quantity 1M "m"))
              :Observation/value :Observation/valueQuantity)]
        (is
          (=
            (resource-update
              db
              {"id" "0"
               "resourceType" "Observation"
               "valueQuantity" {"value" 1M "unit" "m"}})
            []))))


    (testing "single-valued special Reference type"
      (let [[db id] (with-resource db "Patient" "0")
            [db] (with-resource db "Observation" "0" :Observation/subject id)]
        (is
          (=
            (resource-update
              db
              {"id" "0"
               "resourceType" "Observation"
               "subject" {"reference" "Patient/0"}})
            [])))))



  (testing "Updates"

    (testing "primitive single-valued single-typed element"
      (testing "with boolean type"
        (let [[db id] (with-resource db "Patient" "0" :Patient/active false)]
          (is
            (=
              (resource-update
                db
                {"id" "0" "resourceType" "Patient" "active" true})
              [[:db/add id :Patient/active true]]))))

      (testing "with code type"
        (let [[db id]
              (with-code db "http://hl7.org/fhir/administrative-gender" "male")
              [db id] (with-resource db "Patient" "0" :Patient/gender id)]
          (is
            (=
              (with-redefs [d/tempid (fn [partition] partition)]
                (resource-update
                  db
                  {"id" "0" "resourceType" "Patient" "gender" "female"}))
              [{:db/id :part/code
                :code/id "http://hl7.org/fhir/administrative-gender|female"
                :code/system "http://hl7.org/fhir/administrative-gender"
                :code/code "female"}
               [:db/add id :Patient/gender :part/code]]))))

      (testing "with date type"
        (let [[db id] (with-resource db "Patient" "0" :Patient/birthDate
                                     (value/write (Year/of 2000)))]
          (is
            (=
              (mapv
                (fn [[op e a v]] [op e a (value/read v)])
                (resource-update
                  db
                  {"id" "0" "resourceType" "Patient" "birthDate" "2001"}))
              [[:db/add id :Patient/birthDate (Year/of 2001)]])))))


    (testing "primitive multi-valued single-typed element"
      (testing "with one value"
        (let [[db id]
              (with-resource
                db "ServiceRequest" "0" :ServiceRequest/instantiatesUri "foo")]
          (is
            (= (resource-update
                 db
                 {"id" "0"
                  "resourceType" "ServiceRequest"
                  "instantiatesUri" ["bar"]})
               [[:db/retract id :ServiceRequest/instantiatesUri "foo"]
                [:db/add id :ServiceRequest/instantiatesUri "bar"]]))))

      (testing "with multiple values"
        (let [[db id]
              (with-resource
                db "ServiceRequest" "0"
                :ServiceRequest/instantiatesUri #{"one" "two" "three"})]
          (is
            (= (resource-update
                 db
                 {"id" "0"
                  "resourceType" "ServiceRequest"
                  "instantiatesUri" ["one" "TWO" "three"]})
               [[:db/retract id :ServiceRequest/instantiatesUri "two"]
                [:db/add id :ServiceRequest/instantiatesUri "TWO"]])))))


    (testing "single-valued choice-typed element"
      (testing "with string choice"
        (let [[db id]
              (with-resource db "Observation" "0"
                             :Observation/valueString "foo"
                             :Observation/value :Observation/valueString)]
          (is
            (=
              (resource-update
                db
                {"id" "0" "resourceType" "Observation" "valueString" "bar"})
              [[:db/add id :Observation/valueString "bar"]
               [:db/add id :Observation/value :Observation/valueString]]))))

      (testing "switch from string choice to boolean choice"
        (let [[db id]
              (with-resource db "Observation" "0"
                             :Observation/valueString "foo"
                             :Observation/value :Observation/valueString)]
          (is
            (=
              (resource-update
                db
                {"id" "0" "resourceType" "Observation" "valueBoolean" true})
              [[:db/retract id :Observation/valueString "foo"]
               [:db/add id :Observation/valueBoolean true]
               [:db/add id :Observation/value :Observation/valueBoolean]]))))

      (testing "switch from string choice to CodeableConcept choice"
        (let [[db id]
              (with-resource db "Observation" "0"
                             :Observation/valueString "foo"
                             :Observation/value :Observation/valueString)]
          (is
            (=
              (with-redefs [d/tempid (fn [partition] partition)]
                (resource-update
                  db
                  {"id" "0" "resourceType"
                   "Observation" "valueCodeableConcept" {"text" "bar"}}))
              [[:db/retract id :Observation/valueString "foo"]
               [:db/add :part/CodeableConcept :CodeableConcept/text "bar"]
               [:db/add id :Observation/valueCodeableConcept :part/CodeableConcept]
               [:db/add id :Observation/value :Observation/valueCodeableConcept]])))))


    (testing "non-primitive single-valued single-typed element"
      (let [[db status-id]
            (with-non-primitive db :CodeableConcept/text "married")
            [db]
            (with-resource db "Patient" "0" :Patient/maritalStatus status-id)]
        (is
          (=
            (resource-update
              db
              {"id" "0"
               "resourceType" "Patient"
               "maritalStatus" {"text" "unmarried"}})
            [[:db/add status-id :CodeableConcept/text "unmarried"]]))))


    (testing "non-primitive multi-valued single-typed element"
      (testing "with primitive single-valued single-typed child element"
        (let [[db name-id] (with-non-primitive db :HumanName/family "foo")
              [db patient-id]
              (with-resource db "Patient" "0" :Patient/name name-id)]
          (is
            (=
              (with-redefs [d/tempid (fn [partition] partition)]
                (resource-update
                  db
                  {"id" "0" "resourceType" "Patient" "name" [{"family" "bar"}]}))
              [[:db/retract name-id :HumanName/family "foo"]
               [:db/retract patient-id :Patient/name name-id]
               [:db/add :part/HumanName :HumanName/family "bar"]
               [:db/add patient-id :Patient/name :part/HumanName]]))))

      (testing "with primitive multi-valued single-typed child element"
        (let [[db name-id] (with-non-primitive db :HumanName/given "foo")
              [db patient-id]
              (with-resource db "Patient" "0" :Patient/name name-id)]
          (is
            (=
              (with-redefs [d/tempid (fn [partition] partition)]
                (resource-update
                  db
                  {"id" "0" "resourceType" "Patient" "name" [{"given" ["bar"]}]}))
              [[:db/retract name-id :HumanName/given "foo"]
               [:db/retract patient-id :Patient/name name-id]
               [:db/add :part/HumanName :HumanName/given "bar"]
               [:db/add patient-id :Patient/name :part/HumanName]])))

        (let [[db name-id] (with-non-primitive db :HumanName/given "foo")
              [db patient-id]
              (with-resource db "Patient" "0" :Patient/name name-id)]
          (is
            (=
              (with-redefs [d/tempid (fn [partition] partition)]
                (resource-update
                  db
                  {"id" "0" "resourceType" "Patient" "name" [{"given" ["foo" "bar"]}]}))
              [[:db/retract name-id :HumanName/given "foo"]
               [:db/retract patient-id :Patient/name name-id]
               [:db/add :part/HumanName :HumanName/given "foo"]
               [:db/add :part/HumanName :HumanName/given "bar"]
               [:db/add patient-id :Patient/name :part/HumanName]])))))

    (testing "Coding"
      (let [[db code-id]
            (with-code db "http://terminology.hl7.org/CodeSystem/v3-ActCode"
                       "AMB")
            [db coding-id] (with-non-primitive db :Coding/code code-id)
            [db]
            (with-resource db "Encounter" "0" :Encounter/class coding-id)]
        (is
          (=
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-update
                db
                {"id" "0"
                 "resourceType" "Encounter"
                 "class"
                 {"system" "http://terminology.hl7.org/CodeSystem/v3-ActCode"
                  "code" "EMER"}}))
            [{:db/id :part/code
              :code/id "http://terminology.hl7.org/CodeSystem/v3-ActCode|EMER"
              :code/system "http://terminology.hl7.org/CodeSystem/v3-ActCode"
              :code/code "EMER"}
             [:db/add coding-id :Coding/code :part/code]]))))


    (testing "single-valued special Reference type"
      (let [[db id] (with-resource db "Patient" "0")
            [db id] (with-resource db "Observation" "0" :Observation/subject id)]
        (is
          (=
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-update
                db
                {"id" "0"
                 "resourceType" "Observation"
                 "subject" {"reference" "Patient/1"}}))
            [{:db/id :part/Patient
              :Patient/id "1"}
             [:db/add id :Observation/subject :part/Patient]])))))



  (testing "Retracts"

    (testing "primitive single-valued single-typed element"
      (testing "with boolean type"
        (let [[db id] (with-resource db "Patient" "0" :Patient/active false)]
          (is
            (=
              (resource-update
                db
                {"id" "0" "resourceType" "Patient"})
              [[:db/retract id :Patient/active false]]))))

      (testing "with code type"
        (let [[db gender-id]
              (with-code db "http://hl7.org/fhir/administrative-gender" "male")
              [db patient-id]
              (with-resource db "Patient" "0" :Patient/gender gender-id)]
          (is
            (=
              (resource-update
                db
                {"id" "0" "resourceType" "Patient"})
              [[:db/retract patient-id :Patient/gender gender-id]]))))

      (testing "with date type"
        (let [[db id] (with-resource db "Patient" "0" :Patient/birthDate
                                     (value/write (Year/of 2000)))]
          (is
            (=
              (mapv
                (fn [[op e a v]] [op e a (value/read v)])
                (resource-update
                  db
                  {"id" "0" "resourceType" "Patient"}))
              [[:db/retract id :Patient/birthDate (Year/of 2000)]])))))

    (testing "non-primitive single-valued single-typed element"
      (let [[db status-id]
            (with-non-primitive db :CodeableConcept/text "married")
            [db patient-id]
            (with-resource db "Patient" "0" :Patient/maritalStatus status-id)]
        (is
          (=
            (resource-update
              db
              {"id" "0" "resourceType" "Patient"})
            [[:db/retract status-id :CodeableConcept/text "married"]
             [:db/retract patient-id :Patient/maritalStatus status-id]]))))


    (testing "non-primitive multi-valued element"
      (let [{db :db-after :keys [tempids]}
            (d/with db [{:db/id "patient" :Patient/id "0" :Patient/name #{"name"}}
                        {:db/id "name" :HumanName/family "Doe"}])
            patient-id (d/resolve-tempid db tempids "patient")
            name-id (d/resolve-tempid db tempids "name")]
        (is
          (=
            (resource-update
              db
              {"id" "0" "resourceType" "Patient" "name" []})
            [[:db/retract name-id :HumanName/family "Doe"]
             [:db/retract patient-id :Patient/name name-id]])))
      (let [{db :db-after :keys [tempids]}
            (d/with db [{:db/id "patient" :Patient/id "0" :Patient/name #{"name"}}
                        {:db/id "name" :HumanName/family "Doe"}])
            patient-id (d/resolve-tempid db tempids "patient")
            name-id (d/resolve-tempid db tempids "name")]
        (is
          (=
            (resource-update
              db
              {"id" "0" "resourceType" "Patient"})
            [[:db/retract name-id :HumanName/family "Doe"]
             [:db/retract patient-id :Patient/name name-id]]))))


    (testing "single-valued choice-typed element"
      (let [[db id]
            (with-resource db "Observation" "0"
                           :Observation/valueString "foo"
                           :Observation/value :Observation/valueString)]
        (is
          (=
            (resource-update
              db
              {"id" "0" "resourceType" "Observation"})
            [[:db/retract id :Observation/valueString "foo"]
             [:db/retract id :Observation/value :Observation/valueString]]))))


    (testing "Coding"
      (let [[db code-id]
            (with-code db "http://terminology.hl7.org/CodeSystem/v3-ActCode"
                       "AMB")
            [db coding-id] (with-non-primitive db :Coding/code code-id)
            [db encounter-id]
            (with-resource db "Encounter" "0" :Encounter/class coding-id)]
        (is
          (=
            (resource-update
              db
              {"id" "0" "resourceType" "Encounter"})
            [[:db/retract coding-id :Coding/code code-id]
             [:db/retract encounter-id :Encounter/class coding-id]]))))))


(deftest coerce-value-test
  (testing "date with year precision"
    (is (= (Year/of 2019) (value/read (coerce-value "date" "2019")))))

  (testing "date with year-month precision"
    (is (= (YearMonth/of 2019 1) (value/read (coerce-value "date" "2019-01")))))

  (testing "date"
    (is (= (LocalDate/of 2019 2 3) (value/read (coerce-value "date" "2019-02-03")))))

  (testing "dateTime with year precision"
    (is (= (Year/of 2019) (value/read (coerce-value "dateTime" "2019")))))

  (testing "dateTime with year-month precision"
    (is (= (YearMonth/of 2019 1) (value/read (coerce-value "dateTime" "2019-01")))))

  (testing "dateTime with date precision"
    (is (= (LocalDate/of 2019 2 3) (value/read (coerce-value "dateTime" "2019-02-03")))))

  (testing "dateTime without timezone"
    (is (= (LocalDateTime/of 2019 2 3 12 13 14) (value/read (coerce-value "dateTime" "2019-02-03T12:13:14")))))

  (testing "dateTime with timezone"
    (is (= (OffsetDateTime/of 2019 2 3 12 13 14 0 (ZoneOffset/ofHours 1))
           (value/read (coerce-value "dateTime" "2019-02-03T12:13:14+01:00"))))))
