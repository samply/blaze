(ns blaze.datomic.transaction-test
  (:require
    [blaze.datomic.quantity :refer [quantity]]
    [blaze.datomic.test-util :as test-util]
    [blaze.datomic.transaction
     :refer [annotate-codes resource-upsert resource-deletion
             coerce-value transact-async resource-codes-creation]]
    [blaze.datomic.value :as value]
    [blaze.terminology-service :as ts]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.test :as dst]
    [juxt.iota :refer [given]]
    [manifold.deferred :as md])
  (:import
    [java.time Year YearMonth LocalDate LocalDateTime OffsetDateTime ZoneOffset]
    [java.util Base64]))


(defonce db (d/db (st/with-instrument-disabled (test-util/connect))))


(defn fixture [f]
  (st/instrument)
  (dst/instrument)
  (f)
  (st/unstrument))


(use-fixtures :each fixture)


(def term-service
  (reify ts/TermService
    (-expand-value-set [_ {:keys [url valueSetVersion]}]
      (case url
        "http://hl7.org/fhir/ValueSet/administrative-gender"
        (case valueSetVersion
          "4.0.0"
          (md/success-deferred
            {:expansion
             {:contains
              [{:system "http://hl7.org/fhir/administrative-gender"
                :code "male"}
               {:system "http://hl7.org/fhir/administrative-gender"
                :code "female"}]}}))
        "http://hl7.org/fhir/ValueSet/allergy-intolerance-category"
        (case valueSetVersion
          "4.0.0"
          (md/success-deferred
            {:expansion
             {:contains
              [{:system "http://hl7.org/fhir/allergy-intolerance-category"
                :code "medication"}
               {:system "http://hl7.org/fhir/allergy-intolerance-category"
                :code "food"}]}}))
        "http://hl7.org/fhir/ValueSet/filter-operator"
        (case valueSetVersion
          "4.0.0"
          (md/success-deferred
            {:expansion
             {:contains
              [{:system "http://hl7.org/fhir/filter-operator"
                :code "="}]}}))
        "http://hl7.org/fhir/ValueSet/narrative-status"
        (case valueSetVersion
          "4.0.0"
          (md/success-deferred
            {:expansion
             {:contains
              [{:system "http://hl7.org/fhir/narrative-status"
                :code "generated"}]}}))))))


(def failing-term-service
  (reify ts/TermService
    (-expand-value-set [_ _]
      (md/error-deferred {::anom/category ::anom/fault}))))


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


(defn- annotate-codes-system [resource path]
  (:system
    (meta
      (get-in
        @(annotate-codes
           term-service db
           resource)
        path))))


(deftest annotate-codes-test
  (testing "single-valued"
    (is
      (=
        (annotate-codes-system
          {"id" "0"
           "resourceType" "Patient"
           "gender" "male"}
          ["gender"])
        "http://hl7.org/fhir/administrative-gender")))

  (testing "multi-valued two-values"
    (is
      (=
        (annotate-codes-system
          {"id" "0"
           "resourceType" "AllergyIntolerance"
           "category"
           ["medication" "food"]}
          ["category" 0])
        "http://hl7.org/fhir/allergy-intolerance-category")))

  (testing "multi-valued nested"
    (is
      (=
        (annotate-codes-system
          {"id" "0"
           "resourceType" "CodeSystem"
           "filter"
           [{"operator" ["="]}]}
          ["filter" 0 "operator" 0])
        "http://hl7.org/fhir/filter-operator")))

  (testing "within contained resource"
    (is
      (=
        (annotate-codes-system
          {"id" "0"
           "resourceType" "MedicationKnowledge"
           "contained"
           [{"resourceType" "Organization"
             "text"
             {"status" "generated"}}]}
          ["contained" 0 "text" "status"])
        "http://hl7.org/fhir/narrative-status")))

  (testing "mime-type"
    (is
      (=
        (annotate-codes-system
          {"id" "0"
           "resourceType" "Library"
           "content" [{"contentType" "text/cql"}]}
          ["content" 0 "contentType"])
        "urn:ietf:bcp:13")))

  (testing "languages"
    (is
      (=
        (annotate-codes-system
          {"id" "0"
           "resourceType" "Library"
           "language" "en"}
          ["language"])
        "urn:ietf:bcp:47")))

  (testing "failing term-service"
    (try
      @(annotate-codes
         failing-term-service db
         {"id" "0"
          "resourceType" "Patient"
          "gender" "male"})
      (catch Exception e
        (is (= ::anom/fault (::anom/category (:error (ex-data e)))))))))


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
             [:db.fn/cas tempid :instance/version nil -3]]))))

    (testing "Starts with initial version -4 at creation mode :client-assigned-id"
      (let [tempid (d/tempid :part/Patient)]
        (is
          (=
            (resource-upsert
              db {"Patient" {"0" tempid}} :client-assigned-id
              {"id" "0" "resourceType" "Patient"})
            [[:db/add tempid :Patient/id "0"]
             [:db.fn/cas tempid :instance/version nil -4]]))))

    (testing "Doesn't increment version on empty update"
      (let [[db] (test-util/with-resource db "Patient" "0")]
        (is
          (empty?
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0" "resourceType" "Patient"})))))

    (testing "Clear deletion bit on upsert"
      (let [[db id] (test-util/with-deleted-resource db "Patient" "0")]
        (is
          (=
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0" "resourceType" "Patient"})
            [[:db.fn/cas id :instance/version -2 -8]]))))

    (testing "Ignores versionId in meta"
      (let [tempid (d/tempid :part/Patient)]
        (is
          (=
            (resource-upsert
              db {"Patient" {"0" tempid}} :server-assigned-id
              {"id" "0" "resourceType" "Patient"
               "meta" {"versionId" "42"}})
            [[:db/add tempid :Patient/id "0"]
             [:db.fn/cas tempid :instance/version nil -3]])))))



  (testing "Adds"

    (testing "primitive single-valued single-typed element"
      (testing "with boolean type"
        (let [[db id] (test-util/with-resource db "Patient" "0")]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Patient" "active" true})
              [[:db/add id :Patient/active true]
               [:db.fn/cas id :instance/version -3 -7]]))))

      (testing "with code type"
        (let [[db code-id] (test-util/with-gender-code db "male")
              [db id] (test-util/with-resource db "Patient" "0")]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "Patient"
                 "gender"
                 (with-meta 'male {:system "http://hl7.org/fhir/administrative-gender"})})
              [[:db/add id :Patient/gender code-id]
               [:db.fn/cas id :instance/version -3 -7]]))))

      (testing "with date type"
        (let [[db id] (test-util/with-resource db "Patient" "0")]
          (is
            (=
              (mapv
                read-value
                (resource-upsert
                  db nil :server-assigned-id
                  {"id" "0" "resourceType" "Patient" "birthDate" "2000"}))
              [[:db/add id :Patient/birthDate (Year/of 2000)]
               [:db.fn/cas id :instance/version -3 -7]]))))

      (testing "with unsignedInt type"
        (let [[db id] (test-util/with-resource db "CodeSystem" "0")]
          (is
            (=
              (mapv
                read-value
                (resource-upsert
                  db nil :server-assigned-id
                  {"id" "0" "resourceType" "CodeSystem" "count" 1}))
              [[:db/add id :CodeSystem/count 1]
               [:db.fn/cas id :instance/version -3 -7]]))))

      (testing "with base64Binary type"
        (let [[db id] (test-util/with-resource db "Patient" "0")]
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
               [:db.fn/cas id :instance/version -3 -7]])))))


    (testing "primitive single-valued choice-typed element"
      (testing "with boolean choice"
        (let [[db id] (test-util/with-resource db "Patient" "0")]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Patient" "deceasedBoolean" true})
              [[:db/add id :Patient/deceasedBoolean true]
               [:db/add id :Patient/deceased :Patient/deceasedBoolean]
               [:db.fn/cas id :instance/version -3 -7]]))))

      (testing "with dateTime choice"
        (let [[db id] (test-util/with-resource db "Patient" "0")]
          (is
            (=
              (mapv
                read-value
                (resource-upsert
                  db nil :server-assigned-id
                  {"id" "0" "resourceType" "Patient" "deceasedDateTime" "2001-01"}))
              [[:db/add id :Patient/deceasedDateTime (YearMonth/of 2001 1)]
               [:db/add id :Patient/deceased :Patient/deceasedDateTime]
               [:db.fn/cas id :instance/version -3 -7]]))))

      (testing "with string choice"
        (let [[db id] (test-util/with-resource db "Observation" "0")]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Observation" "valueString" "foo"})
              [[:db/add id :Observation/valueString "foo"]
               [:db/add id :Observation/value :Observation/valueString]
               [:db.fn/cas id :instance/version -3 -7]])))))


    (testing "primitive multi-valued single-typed element"
      (testing "with uri type"
        (let [[db id] (test-util/with-resource db "ServiceRequest" "0")]
          (is
            (=
              (with-redefs [d/tempid (fn [partition] partition)]
                (resource-upsert
                  db nil :server-assigned-id
                  {"id" "0"
                   "resourceType" "ServiceRequest"
                   "instantiatesUri" ["foo"]}))
              [[:db/add id :ServiceRequest/instantiatesUri "foo"]
               [:db.fn/cas id :instance/version -3 -7]]))))

      (testing "with code type"
        (let [[db code-id] (test-util/with-code db "http://hl7.org/fhir/filter-operator" "=")
              [db id] (test-util/with-resource db "CodeSystem" "0")]
          (is
            (= (with-redefs [d/tempid (fn [partition] partition)]
                 (resource-upsert
                   db nil :server-assigned-id
                   {"id" "0"
                    "resourceType" "CodeSystem"
                    "filter"
                    [{"operator"
                      [(with-meta '= {:system "http://hl7.org/fhir/filter-operator"})]}]}))
               [[:db/add :part/CodeSystem.filter :CodeSystem.filter/operator code-id]
                [:db/add id :CodeSystem/filter :part/CodeSystem.filter]
                [:db.fn/cas id :instance/version -3 -7]])))

        (let [[db code-id] (test-util/with-code db "http://hl7.org/fhir/allergy-intolerance-category" "medication")
              [db id] (test-util/with-resource db "AllergyIntolerance" "0")]
          (is
            (= (resource-upsert
                 db nil :server-assigned-id
                 {"id" "0"
                  "resourceType" "AllergyIntolerance"
                  "category"
                  [(with-meta 'medication {:system "http://hl7.org/fhir/allergy-intolerance-category"})]})
               [[:db/add id :AllergyIntolerance/category code-id]
                [:db.fn/cas id :instance/version -3 -7]])))))


    (testing "primitive single-valued element in multi-valued backbone element"
      (testing "with boolean type"
        (let [[db id] (test-util/with-resource db "Patient" "0")]
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
               [:db.fn/cas id :instance/version -3 -7]])))))


    (testing "non-primitive single-valued single-typed element"
      (let [[db id] (test-util/with-resource db "Patient" "0")]
        (is
          (= (with-redefs [d/tempid (fn [partition] partition)]
               (resource-upsert
                 db nil :server-assigned-id
                 {"id" "0"
                  "resourceType" "Patient"
                  "maritalStatus" {"text" "married"}}))
             [[:db/add :part/CodeableConcept :CodeableConcept/text "married"]
              [:db/add id :Patient/maritalStatus :part/CodeableConcept]
              [:db.fn/cas id :instance/version -3 -7]]))))


    (testing "non-primitive single-valued choice-typed element"
      (testing "with CodeableConcept choice"
        (let [[db id] (test-util/with-resource db "Observation" "0")]
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
               [:db.fn/cas id :instance/version -3 -7]]))))

      (testing "with Period choice"
        (let [[db id] (test-util/with-resource db "Observation" "0")]
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
               [:db.fn/cas id :instance/version -3 -7]])))))


    (testing "non-primitive multi-valued single-typed element"
      (let [[db id] (test-util/with-resource db "Patient" "0")]
        (is
          (=
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Patient" "name" [{"family" "Doe"}]}))
            [[:db/add :part/HumanName :HumanName/family "Doe"]
             [:db/add id :Patient/name :part/HumanName]
             [:db.fn/cas id :instance/version -3 -7]]))))


    (testing "Coding"
      (testing "without version"
        (let [[db code-id]
              (test-util/with-code db "http://terminology.hl7.org/CodeSystem/v3-ActCode" "AMB")
              [db id] (test-util/with-resource db "Encounter" "0")]
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
               [:db.fn/cas id :instance/version -3 -7]]))))

      (testing "with version"
        (let [[db code-id]
              (test-util/with-code db "http://hl7.org/fhir/sid/icd-10" "2016" "Q14")
              [db id] (test-util/with-resource db "Observation" "0")]
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
               [:db.fn/cas id :instance/version -3 -7]]))))

      (testing "with userSelected"
        (let [[db code-id]
              (test-util/with-code db "http://hl7.org/fhir/sid/icd-10" "2016" "Q14")
              [db id] (test-util/with-resource db "Observation" "0")]
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
               [:db.fn/cas id :instance/version -3 -7]]))))

      (testing "with subject-code index"
        (let [[db code-id] (test-util/with-code db "http://loinc.org" "39156-5")
              [db obs-id] (test-util/with-resource db "Observation" "0")
              [db pat-id] (test-util/with-resource db "Patient" "0")]
          (is
            (=
              (with-redefs [d/tempid (fn [partition] partition)]
                (resource-upsert
                  db nil :server-assigned-id
                  {"id" "0"
                   "resourceType" "Observation"
                   "code"
                   {"coding"
                    [{"system" "http://loinc.org"
                      "code" "39156-5"}]}
                   "subject"
                   {"reference" "Patient/0"}}))
              [[:db/add obs-id :Observation/subject pat-id]
               [:db/add :part/Coding :Coding/code code-id]
               [:db/add pat-id (keyword "Patient.Observation.code" "http://loinc.org||39156-5") obs-id]
               [:db/add :part/CodeableConcept :CodeableConcept/coding :part/Coding]
               [:db/add obs-id :Observation/code :part/CodeableConcept]
               [:db/add obs-id :Observation.index/code code-id]
               [:db.fn/cas obs-id :instance/version -3 -7]])))))

    (testing "CodeSystem with code in concept"
      (let [[db code-id] (test-util/with-code db "http://hl7.org/fhir/administrative-gender" "male")
            [db id] (test-util/with-resource db "CodeSystem" "0")]
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
             [:db.fn/cas id :instance/version -3 -7]]))))


    (testing "CodeSystem with version and code in concept"
      (let [[db code-id] (test-util/with-code db "http://hl7.org/fhir/administrative-gender" "4.0.0" "male")
            [db id] (test-util/with-resource db "CodeSystem" "0")]
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
             [:db.fn/cas id :instance/version -3 -7]]))))


    (testing "CodeSystem with code in content uses http://hl7.org/fhir/codesystem-content-mode"
      (let [[db code-id] (test-util/with-code db "http://hl7.org/fhir/codesystem-content-mode" "complete")
            [db id] (test-util/with-resource db "CodeSystem" "0")]
        (is
          (=
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0"
               "resourceType" "CodeSystem"
               "url" "http://hl7.org/fhir/administrative-gender"
               "version" "4.0.0"
               "content"
               (with-meta 'complete {:system "http://hl7.org/fhir/codesystem-content-mode"})})
            [[:db/add id :CodeSystem/content code-id]
             [:db/add id :CodeSystem/version "4.0.0"]
             [:db/add id :CodeSystem/url "http://hl7.org/fhir/administrative-gender"]
             [:db.fn/cas id :instance/version -3 -7]]))))


    (testing "CodeSystem with sub-concept"
      (let [[db foo-id] (test-util/with-code db "http://something" "foo")
            [db bar-id] (test-util/with-code db "http://something" "bar")
            [db id] (test-util/with-resource db "CodeSystem" "0")]
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
             [:db/add [:part/CodeSystem.concept 2] :CodeSystem.concept/code bar-id]
             [:db/add [:part/CodeSystem.concept 1] :CodeSystem.concept/concept [:part/CodeSystem.concept 2]]
             [:db/add [:part/CodeSystem.concept 1] :CodeSystem.concept/code foo-id]
             [:db/add id :CodeSystem/concept [:part/CodeSystem.concept 1]]
             [:db.fn/cas id :instance/version -3 -7]]))))


    (testing "ValueSet with code in compose include"
      (let [[db code-id] (test-util/with-code db "http://loinc.org" "2.36" "14647-2")
            [db include-id]
            (test-util/with-non-primitive
              db :ValueSet.compose.include/system "http://loinc.org"
              :ValueSet.compose.include/version "2.36")
            [db compose-id] (test-util/with-non-primitive db :ValueSet.compose/include include-id)
            [db id] (test-util/with-resource db "ValueSet" "0" :ValueSet/compose compose-id)]
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
             [:db.fn/cas id :instance/version -3 -7]]))))


    (testing "ValueSet with code in expansion contains"
      (let [[db code-id] (test-util/with-code db "http://loinc.org" "2.50" "14647-2")
            [db contains-id]
            (test-util/with-non-primitive
              db :ValueSet.expansion.contains/system "http://loinc.org"
              :ValueSet.expansion.contains/version "2.50")
            [db expansion-id] (test-util/with-non-primitive db :ValueSet.expansion/contains contains-id)
            [db id] (test-util/with-resource db "ValueSet" "0" :ValueSet/expansion expansion-id)]
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
             [:db.fn/cas id :instance/version -3 -7]]))))


    (testing "special Quantity type"
      (let [[db id] (test-util/with-resource db "Observation" "0")]
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
             [:db.fn/cas id :instance/version -3 -7]]))))


    (testing "special Quantity type with integer decimal value"
      (let [[db id] (test-util/with-resource db "Observation" "0")]
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
             [:db.fn/cas id :instance/version -3 -7]]))))


    (testing "special Quantity type with unit in unit"
      (let [[db id] (test-util/with-resource db "Observation" "0")]
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
             [:db.fn/cas id :instance/version -3 -7]]))))


    (testing "single-valued special Reference type"
      (testing "with resource resolvable in db"
        (let [[db patient-id] (test-util/with-resource db "Patient" "0")
              [db observation-id] (test-util/with-resource db "Observation" "0")]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "Observation"
                 "subject" {"reference" "Patient/0"}})
              [[:db/add observation-id :Observation/subject patient-id]
               [:db.fn/cas observation-id :instance/version -3 -7]]))))

      (testing "with resource resolvable in tempids"
        (let [patient-id (d/tempid :part/Patient)
              [db observation-id] (test-util/with-resource db "Observation" "0")]
          (is
            (=
              (resource-upsert
                db {"Patient" {"0" patient-id}} :server-assigned-id
                {"id" "0"
                 "resourceType" "Observation"
                 "subject" {"reference" "Patient/0"}})
              [[:db/add observation-id :Observation/subject patient-id]
               [:db.fn/cas observation-id :instance/version -3 -7]])))))


    (testing "multi-valued special Reference type"
      (let [[db organization-id] (test-util/with-resource db "Organization" "0")
            [db patient-id] (test-util/with-resource db "Patient" "0")]
        (is
          (=
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0"
               "resourceType" "Patient"
               "generalPractitioner"
               [{"reference" "Organization/0"}]})
            [[:db/add patient-id :Patient/generalPractitioner organization-id]
             [:db.fn/cas patient-id :instance/version -3 -7]]))))


    (testing "Contact"
      (let [[db id] (test-util/with-resource db "Patient" "0")]
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
             [:db.fn/cas id :instance/version -3 -7]]))))


    (testing "Contained resources"
      (let [[db id] (test-util/with-resource db "Observation" "0")]
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
             [:db.fn/cas id :instance/version -3 -7]]))))


    (testing "ConceptMap with source code"
      (let [[db code-id] (test-util/with-code db "http://foo" "bar")
            [db group-id] (test-util/with-non-primitive db :ConceptMap.group/source "http://foo")
            [db id] (test-util/with-resource db "ConceptMap" "0" :ConceptMap/group group-id)]
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
             [:db.fn/cas id :instance/version -3 -7]]))))


    (testing "ConceptMap with target code"
      (let [[db code-id] (test-util/with-code db "http://foo" "bar")
            [db id] (test-util/with-resource db "ConceptMap" "0")]
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
             [:db.fn/cas id :instance/version -3 -7]]))))


    (testing "String typed extension"
      (let [[db id] (test-util/with-resource db "Patient" "0")]
        (is
          (=
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "Patient"
                 "extension"
                 [{"url" "http://foo"
                   "valueString" "bar"}]}))
            [[:db/add :part/Extension :Extension/valueString "bar"]
             [:db/add :part/Extension :Extension/value :Extension/valueString]
             [:db/add :part/Extension :Extension/url "http://foo"]
             [:db/add id :Patient/extension :part/Extension]
             [:db.fn/cas id :instance/version -3 -7]]))))


    (testing "Code typed extension"
      ;; TODO: resolve the value set binding here
      (let [[db draft-id] (test-util/with-code db "draft")
            [db extension-id] (test-util/with-non-primitive db :Extension/url "http://foo")
            [db id] (test-util/with-resource db "CodeSystem" "0" :CodeSystem/extension extension-id)]
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
             [:db.fn/cas id :instance/version -3 -7]]))))


    (testing "ValueSet compose include system"
      (let [[db id] (test-util/with-resource db "ValueSet" "0")]
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
             [:db.fn/cas id :instance/version -3 -7]])))))



  (testing "Keeps"

    (testing "primitive single-valued single-typed element"
      (testing "with boolean type"
        (let [[db] (test-util/with-resource db "Patient" "0" :Patient/active true)]
          (is
            (empty?
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Patient" "active" true})))))

      (testing "with code type"
        (let [[db id] (test-util/with-gender-code db "male")
              [db] (test-util/with-resource db "Patient" "0" :Patient/gender id)]
          (is
            (empty?
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "Patient"
                 "gender" (with-meta 'male {:system "http://hl7.org/fhir/administrative-gender"})})))))

      (testing "with date type"
        (let [[db] (test-util/with-resource db "Patient" "0" :Patient/birthDate
                                  (value/write (Year/of 2000)))]
          (is
            (empty?
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Patient" "birthDate" "2000"})))))

      (testing "with dateTime type"
        (let [[db]
              (test-util/with-resource
                db "CodeSystem" "0"
                :CodeSystem/date (value/write (LocalDate/of 2016 1 28)))]
          (is
            (empty?
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "CodeSystem" "date" "2016-01-28"}))))

        (let [[db]
              (test-util/with-resource
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
          (let [[db]
                (test-util/with-resource
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
              (test-util/with-resource
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
        (let [[db id] (test-util/with-non-primitive db :CodeableConcept/text "foo")
              [db]
              (test-util/with-resource db "Observation" "0"
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
              (test-util/with-resource
                db "ServiceRequest" "0" :ServiceRequest/instantiatesUri "foo")]
          (is
            (empty?
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "ServiceRequest"
                 "instantiatesUri" ["foo"]})))))

      (testing "with code type"
        (let [[db id] (test-util/with-code db "http://hl7.org/fhir/allergy-intolerance-category" "medication")
              [db] (test-util/with-resource db "AllergyIntolerance" "0"
                                  :AllergyIntolerance/category id)]
          (is
            (empty?
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "AllergyIntolerance"
                 "category"
                 [(with-meta 'medication {:system "http://hl7.org/fhir/allergy-intolerance-category"})]}))))))


    (testing "non-primitive multi-valued single-typed element"
      (let [[db id] (test-util/with-non-primitive db :HumanName/family "Doe")
            [db] (test-util/with-resource db "Patient" "0" :Patient/name id)]
        (is
          (empty?
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0" "resourceType" "Patient" "name" [{"family" "Doe"}]})))))


    (testing "Coding"
      (testing "with version"
        (let [[db id] (test-util/with-icd10-code db "2016" "Q14")
              [db id] (test-util/with-non-primitive db :Coding/code id)
              [db id] (test-util/with-non-primitive db :CodeableConcept/coding id)
              [db] (test-util/with-resource db "Observation" "0" :Observation/code id)]
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
        (let [[db id] (test-util/with-icd10-code db "2016" "Q14")
              [db id] (test-util/with-non-primitive db :Coding/code id :Coding/userSelected true)
              [db id] (test-util/with-non-primitive db :CodeableConcept/coding id)
              [db] (test-util/with-resource db "Observation" "0" :Observation/code id)]
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
            (test-util/with-resource
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
      (let [[db id] (test-util/with-resource db "Patient" "0")
            [db] (test-util/with-resource db "Observation" "0" :Observation/subject id)]
        (is
          (empty?
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0"
               "resourceType" "Observation"
               "subject" {"reference" "Patient/0"}})))))


    (testing "CodeSystem with contact"
      (let [[db id] (test-util/with-code db "http://hl7.org/fhir/contact-point-system" "url")
            [db id] (test-util/with-non-primitive db :ContactPoint/system id)
            [db id] (test-util/with-non-primitive db :ContactDetail/telecom id)
            [db] (test-util/with-resource db "CodeSystem" "0" :CodeSystem/contact id)]
        (is
          (empty?
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0"
               "resourceType" "CodeSystem"
               "contact"
               [{"telecom"
                 [{"system" (with-meta 'url {:system "http://hl7.org/fhir/contact-point-system"})}]}]})))))


    (testing "Contained resources"
      (let [[db id] (test-util/with-non-primitive db :Patient/active true :local-id "0")
            [db] (test-util/with-resource db "Patient" "0" :Patient/contained id)]
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
      (let [[db actor-id] (test-util/with-resource db "Location" "0")
            [db] (test-util/with-resource db "Schedule" "0" :Schedule/actor actor-id)]
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
        (let [[db id] (test-util/with-resource db "Patient" "0" :Patient/active false)]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Patient" "active" true})
              [[:db/add id :Patient/active true]
               [:db.fn/cas id :instance/version -3 -7]]))))

      (testing "with code type"
        (let [[db male-id] (test-util/with-gender-code db "male")
              [db female-id] (test-util/with-gender-code db "female")
              [db id] (test-util/with-resource db "Patient" "0" :Patient/gender male-id)]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0"
                 "resourceType" "Patient"
                 "gender"
                 (with-meta 'female {:system "http://hl7.org/fhir/administrative-gender"})})
              [[:db/add id :Patient/gender female-id]
               [:db.fn/cas id :instance/version -3 -7]]))))

      (testing "with date type"
        (let [[db id] (test-util/with-resource db "Patient" "0" :Patient/birthDate
                                     (value/write (Year/of 2000)))]
          (is
            (=
              (mapv
                read-value
                (resource-upsert
                  db nil :server-assigned-id
                  {"id" "0" "resourceType" "Patient" "birthDate" "2001"}))
              [[:db/add id :Patient/birthDate (Year/of 2001)]
               [:db.fn/cas id :instance/version -3 -7]])))))


    (testing "primitive multi-valued single-typed element"
      (testing "with one value"
        (let [[db id]
              (test-util/with-resource
                db "ServiceRequest" "0" :ServiceRequest/instantiatesUri "foo")]
          (is
            (= (resource-upsert
                 db nil :server-assigned-id
                 {"id" "0"
                  "resourceType" "ServiceRequest"
                  "instantiatesUri" ["bar"]})
               [[:db/retract id :ServiceRequest/instantiatesUri "foo"]
                [:db/add id :ServiceRequest/instantiatesUri "bar"]
                [:db.fn/cas id :instance/version -3 -7]]))))

      (testing "with multiple values"
        (let [[db id]
              (test-util/with-resource
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
                [:db.fn/cas id :instance/version -3 -7]]))))

      (testing "with code type"
        (let [[db medication-id] (test-util/with-code db "http://hl7.org/fhir/allergy-intolerance-category" "medication")
              [db food-id] (test-util/with-code db "http://hl7.org/fhir/allergy-intolerance-category" "food")
              [db id] (test-util/with-resource db "AllergyIntolerance" "0"
                                     :AllergyIntolerance/category medication-id)]
          (is
            (= (with-redefs [d/tempid (fn [partition] partition)]
                 (resource-upsert
                   db nil :server-assigned-id
                   {"id" "0"
                    "resourceType" "AllergyIntolerance"
                    "category"
                    [(with-meta 'medication {:system "http://hl7.org/fhir/allergy-intolerance-category"})
                     (with-meta 'food {:system "http://hl7.org/fhir/allergy-intolerance-category"})]}))
               [[:db/add id :AllergyIntolerance/category food-id]
                [:db.fn/cas id :instance/version -3 -7]])))))


    (testing "single-valued choice-typed element"
      (testing "with string choice"
        (let [[db id]
              (test-util/with-resource db "Observation" "0"
                             :Observation/valueString "foo"
                             :Observation/value :Observation/valueString)]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Observation" "valueString" "bar"})
              [[:db/add id :Observation/valueString "bar"]
               [:db/add id :Observation/value :Observation/valueString]
               [:db.fn/cas id :instance/version -3 -7]]))))

      (testing "switch from string choice to boolean choice"
        (let [[db id]
              (test-util/with-resource db "Observation" "0"
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
               [:db.fn/cas id :instance/version -3 -7]]))))

      (testing "switch from string choice to CodeableConcept choice"
        (let [[db id]
              (test-util/with-resource db "Observation" "0"
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
               [:db.fn/cas id :instance/version -3 -7]])))))


    (testing "non-primitive single-valued single-typed element"
      (let [[db status-id]
            (test-util/with-non-primitive db :CodeableConcept/text "married")
            [db id]
            (test-util/with-resource db "Patient" "0" :Patient/maritalStatus status-id)]
        (is
          (=
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0"
               "resourceType" "Patient"
               "maritalStatus" {"text" "unmarried"}})
            [[:db/add status-id :CodeableConcept/text "unmarried"]
             [:db.fn/cas id :instance/version -3 -7]]))))


    (testing "non-primitive multi-valued single-typed element"
      (testing "with primitive single-valued single-typed child element"
        (let [[db name-id] (test-util/with-non-primitive db :HumanName/family "foo")
              [db patient-id]
              (test-util/with-resource db "Patient" "0" :Patient/name name-id)]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Patient" "name" [{"family" "bar"}]})
              [[:db/add name-id :HumanName/family "bar"]
               [:db.fn/cas patient-id :instance/version -3 -7]]))))

      (testing "with primitive multi-valued single-typed child element"
        (let [[db name-id] (test-util/with-non-primitive db :HumanName/given "foo")
              [db patient-id]
              (test-util/with-resource db "Patient" "0" :Patient/name name-id)]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Patient" "name" [{"given" ["bar"]}]})
              [[:db/retract name-id :HumanName/given "foo"]
               [:db/add name-id :HumanName/given "bar"]
               [:db.fn/cas patient-id :instance/version -3 -7]])))

        (let [[db name-id] (test-util/with-non-primitive db :HumanName/given "foo")
              [db patient-id]
              (test-util/with-resource db "Patient" "0" :Patient/name name-id)]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Patient" "name" [{"given" ["foo" "bar"]}]})
              [[:db/add name-id :HumanName/given "bar"]
               [:db.fn/cas patient-id :instance/version -3 -7]])))))

    (testing "Coding"
      (let [[db amb-id] (test-util/with-code db "http://terminology.hl7.org/CodeSystem/v3-ActCode" "AMB")
            [db emer-id] (test-util/with-code db "http://terminology.hl7.org/CodeSystem/v3-ActCode" "EMER")
            [db coding-id] (test-util/with-non-primitive db :Coding/code amb-id)
            [db encounter-id]
            (test-util/with-resource db "Encounter" "0" :Encounter/class coding-id)]
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
             [:db.fn/cas encounter-id :instance/version -3 -7]]))))


    (testing "single-valued special Reference type"
      (let [[db patient-0-id] (test-util/with-resource db "Patient" "0")
            [db patient-1-id] (test-util/with-resource db "Patient" "1")
            [db observation-id] (test-util/with-resource db "Observation" "0" :Observation/subject patient-0-id)]
        (is
          (=
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0"
               "resourceType" "Observation"
               "subject" {"reference" "Patient/1"}})
            [[:db/add observation-id :Observation/subject patient-1-id]
             [:db.fn/cas observation-id :instance/version -3 -7]]))))


    (testing "Contained resources"
      (testing "with changes inside the contained resource"
        (let [[db contained-id] (test-util/with-non-primitive db :Patient/active false
                                                    :local-id "0")
              [db id] (test-util/with-resource db "Observation" "0"
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
               [:db.fn/cas id :instance/version -3 -7]]))))

      (testing "with changes inside the container resource"
        (let [[db contained-id] (test-util/with-non-primitive db :Patient/active true
                                                    :local-id "0")
              [db preliminary-id] (test-util/with-code db "http://hl7.org/fhir/observation-status" "preliminary")
              [db final-id] (test-util/with-code db "http://hl7.org/fhir/observation-status" "final")
              [db id] (test-util/with-resource db "Observation" "0"
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
                 "status"
                 (with-meta 'final {:system "http://hl7.org/fhir/observation-status"})
                 "contained"
                 [{"id" "0"
                   "resourceType" "Patient"
                   "active" true}]})
              [[:db/add id :Observation/status final-id]
               [:db.fn/cas id :instance/version -3 -7]])))))


    (testing "Don't reuse old entities or new entities more than once"
      (let [[db component-1-id]
            (test-util/with-non-primitive
              db
              :Observation.component/valueQuantity (value/write (quantity 1M ""))
              :Observation.component/value :Observation.component/valueQuantity)
            [db component-2-id]
            (test-util/with-non-primitive
              db
              :Observation.component/valueQuantity (value/write (quantity 2M ""))
              :Observation.component/value :Observation.component/valueQuantity)
            [db observation-id]
            (test-util/with-resource db "Observation" "0" :Observation/component
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
             [:db.fn/cas observation-id :instance/version -3 -7]]))))


    (testing "multi-valued code element"
      (let [[db medication-id] (test-util/with-code db "http://hl7.org/fhir/allergy-intolerance-category" "medication")
            [db food-id] (test-util/with-code db "http://hl7.org/fhir/allergy-intolerance-category" "food")
            [db id] (test-util/with-resource db "AllergyIntolerance" "0"
                                   :AllergyIntolerance/category medication-id)]
        (is
          (= (with-redefs [d/tempid (fn [partition] partition)]
               (resource-upsert
                 db nil :server-assigned-id
                 {"id" "0"
                  "resourceType" "AllergyIntolerance"
                  "category"
                  [(with-meta 'food {:system "http://hl7.org/fhir/allergy-intolerance-category"})]}))
             [[:db/retract id :AllergyIntolerance/category medication-id]
              [:db/add id :AllergyIntolerance/category food-id]
              [:db.fn/cas id :instance/version -3 -7]])))))


  (testing "Retracts"

    (testing "primitive single-valued single-typed element"
      (testing "with boolean type"
        (let [[db id] (test-util/with-resource db "Patient" "0" :Patient/active false)]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Patient"})
              [[:db/retract id :Patient/active false]
               [:db.fn/cas id :instance/version -3 -7]]))))

      (testing "with code type"
        (let [[db gender-id] (test-util/with-gender-code db "male")
              [db patient-id]
              (test-util/with-resource db "Patient" "0" :Patient/gender gender-id)]
          (is
            (=
              (resource-upsert
                db nil :server-assigned-id
                {"id" "0" "resourceType" "Patient"})
              [[:db/retract patient-id :Patient/gender gender-id]
               [:db.fn/cas patient-id :instance/version -3 -7]]))))

      (testing "with date type"
        (let [[db id] (test-util/with-resource db "Patient" "0" :Patient/birthDate
                                     (value/write (Year/of 2000)))]
          (is
            (=
              (mapv
                read-value
                (resource-upsert
                  db nil :server-assigned-id
                  {"id" "0" "resourceType" "Patient"}))
              [[:db/retract id :Patient/birthDate (Year/of 2000)]
               [:db.fn/cas id :instance/version -3 -7]])))))

    (testing "non-primitive single-valued single-typed element"
      (let [[db status-id]
            (test-util/with-non-primitive db :CodeableConcept/text "married")
            [db patient-id]
            (test-util/with-resource db "Patient" "0" :Patient/maritalStatus status-id)]
        (is
          (=
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0" "resourceType" "Patient"})
            [[:db/retract status-id :CodeableConcept/text "married"]
             [:db/retract patient-id :Patient/maritalStatus status-id]
             [:db.fn/cas patient-id :instance/version -3 -7]]))))


    (testing "non-primitive multi-valued element"
      (let [[db name-id] (test-util/with-non-primitive db :HumanName/family "Doe")
            [db patient-id] (test-util/with-resource db "Patient" "0" :Patient/name name-id)]
        (is
          (=
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0" "resourceType" "Patient" "name" []})
            [[:db/retract name-id :HumanName/family "Doe"]
             [:db/retract patient-id :Patient/name name-id]
             [:db.fn/cas patient-id :instance/version -3 -7]])))

      (let [[db name-id] (test-util/with-non-primitive db :HumanName/family "Doe")
            [db patient-id] (test-util/with-resource db "Patient" "0" :Patient/name name-id)]
        (is
          (=
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0" "resourceType" "Patient"})
            [[:db/retract name-id :HumanName/family "Doe"]
             [:db/retract patient-id :Patient/name name-id]
             [:db.fn/cas patient-id :instance/version -3 -7]]))))


    (testing "single-valued choice-typed element"
      (let [[db id]
            (test-util/with-resource db "Observation" "0"
                           :Observation/valueString "foo"
                           :Observation/value :Observation/valueString)]
        (is
          (=
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0" "resourceType" "Observation"})
            [[:db/retract id :Observation/valueString "foo"]
             [:db/retract id :Observation/value :Observation/valueString]
             [:db.fn/cas id :instance/version -3 -7]]))))


    (testing "primitive single-valued element in single-valued backbone element"
      (let [[db software-id]
            (test-util/with-non-primitive
              db :TerminologyCapabilities.software/name "foo")
            [db capabilities-id]
            (test-util/with-resource
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
             [:db.fn/cas capabilities-id :instance/version -3 -7]]))))


    (testing "primitive single-valued element in multi-valued backbone element"
      (let [[db communication-id]
            (test-util/with-non-primitive db :Patient.communication/preferred true)
            [db patient-id]
            (test-util/with-resource
              db "Patient" "0" :Patient/communication communication-id)]
        (is
          (=
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0"
               "resourceType" "Patient"})
            [[:db/retract communication-id :Patient.communication/preferred true]
             [:db/retract patient-id :Patient/communication communication-id]
             [:db.fn/cas patient-id :instance/version -3 -7]]))))


    (testing "Coding"
      (let [[db code-id]
            (test-util/with-code db "http://terminology.hl7.org/CodeSystem/v3-ActCode"
                       "AMB")
            [db coding-id] (test-util/with-non-primitive db :Coding/code code-id)
            [db encounter-id]
            (test-util/with-resource db "Encounter" "0" :Encounter/class coding-id)]
        (is
          (=
            (resource-upsert
              db nil :server-assigned-id
              {"id" "0" "resourceType" "Encounter"})
            [[:db/retract coding-id :Coding/code code-id]
             [:db/retract encounter-id :Encounter/class coding-id]
             [:db.fn/cas encounter-id :instance/version -3 -7]]))))


    (testing "Contained resources"
      (testing "retracts all contained resources"
        (let [[db contained-1-id] (test-util/with-non-primitive db :Patient/active true
                                                      :local-id "1")
              [db contained-2-id] (test-util/with-non-primitive db :Patient/active false
                                                      :local-id "2")
              [db id] (test-util/with-resource db "Patient" "0" :Patient/contained
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
                [:db.fn/cas id :instance/version -3 -7]}))))))



  (testing "Fails"

    (testing "on non-existing reference target"
      (let [[db] (test-util/with-resource db "Observation" "0")]
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
    (let [[db id] (test-util/with-resource db "Patient" "0")]
      (is
        (=
          (resource-deletion db "Patient" "0")
          [[:db.fn/cas id :instance/version -3 -5]]))))

  (testing "Does nothing on subsequent delete"
    (let [[db] (test-util/with-deleted-resource db "Patient" "0")]
      (is (empty? (resource-deletion db "Patient" "0")))))


  (testing "Retracts"

    (testing "primitive single-valued single-typed element"
      (testing "with boolean type"
        (let [[db id] (test-util/with-resource db "Patient" "0" :Patient/active true)]
          (is
            (=
              (resource-deletion db "Patient" "0")
              [[:db.fn/cas id :instance/version -3 -5]
               [:db/retract id :Patient/active true]]))))

      (testing "with code type"
        (let [[db gender-id] (test-util/with-gender-code db "male")
              [db patient-id]
              (test-util/with-resource db "Patient" "0" :Patient/gender gender-id)]
          (is
            (=
              (resource-deletion db "Patient" "0")
              [[:db.fn/cas patient-id :instance/version -3 -5]
               [:db/retract patient-id :Patient/gender gender-id]])))))

    (testing "primitive multi-valued single-typed element"
      (testing "with one value"
        (let [[db id]
              (test-util/with-resource
                db "ServiceRequest" "0" :ServiceRequest/instantiatesUri "foo")]
          (is
            (= (resource-deletion db "ServiceRequest" "0")
               [[:db.fn/cas id :instance/version -3 -5]
                [:db/retract id :ServiceRequest/instantiatesUri "foo"]]))))

      (testing "with multiple values"
        (let [[db id]
              (test-util/with-resource
                db "ServiceRequest" "0"
                :ServiceRequest/instantiatesUri #{"one" "two"})]
          (is
            (= (set (resource-deletion db "ServiceRequest" "0"))
               #{[:db.fn/cas id :instance/version -3 -5]
                 [:db/retract id :ServiceRequest/instantiatesUri "one"]
                 [:db/retract id :ServiceRequest/instantiatesUri "two"]}))))

      (testing "with code type"
        (let [[db medication-id] (test-util/with-code db "http://hl7.org/fhir/allergy-intolerance-category" "medication")
              [db id]
              (test-util/with-resource
                db "AllergyIntolerance" "0"
                :AllergyIntolerance/category medication-id)]
          (is
            (= (resource-deletion db "AllergyIntolerance" "0")
               [[:db.fn/cas id :instance/version -3 -5]
                [:db/retract id :AllergyIntolerance/category medication-id]])))))

    (testing "non-primitive single-valued single-typed element"
      (let [[db status-id]
            (test-util/with-non-primitive db :CodeableConcept/text "married")
            [db patient-id]
            (test-util/with-resource db "Patient" "0" :Patient/maritalStatus status-id)]
        (is
          (=
            (resource-deletion db "Patient" "0")
            [[:db.fn/cas patient-id :instance/version -3 -5]
             [:db/retract status-id :CodeableConcept/text "married"]
             [:db/retract patient-id :Patient/maritalStatus status-id]]))))

    (testing "non-primitive multi-valued element"
      (let [[db name-id] (test-util/with-non-primitive db :HumanName/family "Doe")
            [db patient-id] (test-util/with-resource db "Patient" "0" :Patient/name name-id)]
        (is
          (=
            (resource-deletion db "Patient" "0")
            [[:db.fn/cas patient-id :instance/version -3 -5]
             [:db/retract name-id :HumanName/family "Doe"]
             [:db/retract patient-id :Patient/name name-id]]))))))


(deftest resource-codes-creation-test
  (testing "single-valued code element"
    (let [tx-data
          (set
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-codes-creation
                db
                {"id" "0"
                 "resourceType" "Patient"
                 "gender" (with-meta 'male {:system "http://hl7.org/fhir/administrative-gender"})})))]
      (is
        (contains?
          tx-data
          {:db/id :part/code
           :code/id "http://hl7.org/fhir/administrative-gender||male"
           :code/system "http://hl7.org/fhir/administrative-gender"
           :code/code "male"}))
      (is
        (contains?
          tx-data
          {:db/ident (keyword "Patient.Observation.code" "http://hl7.org/fhir/administrative-gender||male")
           :db/valueType :db.type/ref
           :db/cardinality :db.cardinality/many}))
      (is
        (contains?
          tx-data
          {:db/ident (keyword "Patient.Condition.code" "http://hl7.org/fhir/administrative-gender||male")
           :db/valueType :db.type/ref
           :db/cardinality :db.cardinality/many}))))

  (testing "multi-valued code element"
    (let [tx-data
          (set
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-codes-creation
                db
                {"id" "0"
                 "resourceType" "AllergyIntolerance"
                 "category"
                 [(with-meta 'medication {:system "http://hl7.org/fhir/allergy-intolerance-category"})]})))]
      (is
        (contains?
          tx-data
          {:db/id :part/code
           :code/id "http://hl7.org/fhir/allergy-intolerance-category||medication"
           :code/system "http://hl7.org/fhir/allergy-intolerance-category"
           :code/code "medication"}))
      (is
        (contains?
          tx-data
          {:db/ident (keyword "Patient.Observation.code" "http://hl7.org/fhir/allergy-intolerance-category||medication")
           :db/valueType :db.type/ref
           :db/cardinality :db.cardinality/many}))
      (is
        (contains?
          tx-data
          {:db/ident (keyword "Patient.Condition.code" "http://hl7.org/fhir/allergy-intolerance-category||medication")
           :db/valueType :db.type/ref
           :db/cardinality :db.cardinality/many}))))

  (testing "single-valued Coding element"
    (let [tx-data
          (set
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-codes-creation
                db
                {"id" "0"
                 "resourceType" "Encounter"
                 "class"
                 {"system" "http://terminology.hl7.org/CodeSystem/v3-ActCode"
                  "code" "AMB"}})))]
      (is
        (contains?
          tx-data
          {:db/id :part/code
           :code/id "http://terminology.hl7.org/CodeSystem/v3-ActCode||AMB"
           :code/system "http://terminology.hl7.org/CodeSystem/v3-ActCode"
           :code/code "AMB"}))
      (is
        (contains?
          tx-data
          {:db/ident (keyword "Patient.Observation.code" "http://terminology.hl7.org/CodeSystem/v3-ActCode||AMB")
           :db/valueType :db.type/ref
           :db/cardinality :db.cardinality/many}))))

  (testing "multi-valued code element in multi-valued Backbone element"
    (let [tx-data
          (set
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-codes-creation
                db
                {"id" "0"
                 "resourceType" "CodeSystem"
                 "filter"
                 [{"operator"
                   [(with-meta '= {:system "http://hl7.org/fhir/filter-operator"})]}]})))]
      (is
        (contains?
          tx-data
          {:db/id :part/code
           :code/id "http://hl7.org/fhir/filter-operator||="
           :code/system "http://hl7.org/fhir/filter-operator"
           :code/code "="}))
      (is
        (contains?
          tx-data
          {:db/ident (keyword "Patient.Observation.code" "http://hl7.org/fhir/filter-operator||=")
           :db/valueType :db.type/ref
           :db/cardinality :db.cardinality/many}))))

  (testing "Coding in CodeableConcept"
    (let [tx-data
          (set
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-codes-creation
                db
                {"id" "0"
                 "resourceType" "Observation"
                 "code"
                 {"coding"
                  [{"system" "http://hl7.org/fhir/sid/icd-10"
                    "version" "2016"
                    "code" "Q14"}]}})))]
      (is
        (contains?
          tx-data
          {:db/id :part/code
           :code/id "http://hl7.org/fhir/sid/icd-10|2016|Q14"
           :code/system "http://hl7.org/fhir/sid/icd-10"
           :code/version "2016"
           :code/code "Q14"}))
      (is
        (contains?
          tx-data
          {:db/ident (keyword "Patient.Observation.code" "http://hl7.org/fhir/sid/icd-10|2016|Q14")
           :db/valueType :db.type/ref
           :db/cardinality :db.cardinality/many}))))

  (testing "CodeSystem with code in concept"
    (let [tx-data
          (set
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-codes-creation
                db
                {"id" "0"
                 "resourceType" "CodeSystem"
                 "url" "http://hl7.org/fhir/administrative-gender"
                 "concept"
                 [{"code" "male"}]})))]
      (is
        (contains?
          tx-data
          {:db/id :part/code
           :code/id "http://hl7.org/fhir/administrative-gender||male"
           :code/system "http://hl7.org/fhir/administrative-gender"
           :code/code "male"}))
      (is
        (contains?
          tx-data
          {:db/ident (keyword "Patient.Observation.code" "http://hl7.org/fhir/administrative-gender||male")
           :db/valueType :db.type/ref
           :db/cardinality :db.cardinality/many}))))

  (testing "CodeSystem with version and code in concept"
    (let [tx-data
          (set
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-codes-creation
                db
                {"id" "0"
                 "resourceType" "CodeSystem"
                 "url" "http://hl7.org/fhir/administrative-gender"
                 "version" "4.0.0"
                 "concept"
                 [{"code" "male"}]})))]
      (is
        (contains?
          tx-data
          {:db/id :part/code
           :code/id "http://hl7.org/fhir/administrative-gender|4.0.0|male"
           :code/system "http://hl7.org/fhir/administrative-gender"
           :code/version "4.0.0"
           :code/code "male"}))
      (is
        (contains?
          tx-data
          {:db/ident (keyword "Patient.Observation.code" "http://hl7.org/fhir/administrative-gender|4.0.0|male")
           :db/valueType :db.type/ref
           :db/cardinality :db.cardinality/many}))))

  (testing "ConceptMap with source code"
    (let [tx-data
          (set
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-codes-creation
                db
                {"id" "0"
                 "resourceType" "ConceptMap"
                 "group"
                 [{"source" "http://foo"
                   "element"
                   [{"code" "bar"}]}]})))]
      (is
        (contains?
          tx-data
          {:db/id :part/code
           :code/id "http://foo||bar"
           :code/system "http://foo"
           :code/code "bar"}))
      (is
        (contains?
          tx-data
          {:db/ident (keyword "Patient.Observation.code" "http://foo||bar")
           :db/valueType :db.type/ref
           :db/cardinality :db.cardinality/many}))))

  (testing "ConceptMap with target code"
    (let [tx-data
          (set
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-codes-creation
                db
                {"id" "0"
                 "resourceType" "ConceptMap"
                 "group"
                 [{"target" "http://foo"
                   "element"
                   [{"target"
                     [{"code" "bar"}]}]}]})))]
      (is
        (contains?
          tx-data
          {:db/id :part/code
           :code/id "http://foo||bar"
           :code/system "http://foo"
           :code/code "bar"}))
      (is
        (contains?
          tx-data
          {:db/ident (keyword "Patient.Observation.code" "http://foo||bar")
           :db/valueType :db.type/ref
           :db/cardinality :db.cardinality/many}))))

  (testing "Code typed extension"
    ;; TODO: resolve the value set binding here
    (let [tx-data
          (set
            (with-redefs [d/tempid (fn [partition] partition)]
              (resource-codes-creation
                db
                {"id" "0"
                 "resourceType" "CodeSystem"
                 "extension"
                 [{"url" "http://foo"
                   "valueCode" "draft"}]})))]
      (is
        (contains?
          tx-data
          {:db/id :part/code
           :code/id "||draft"
           :code/code "draft"}))
      (is
        (contains?
          tx-data
          {:db/ident (keyword "Patient.Observation.code" "||draft")
           :db/valueType :db.type/ref
           :db/cardinality :db.cardinality/many})))))


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
    (let [conn (test-util/connect)]
      @(d/transact-async conn [{:Patient/id "0" :instance/version 0}])
      @(d/transact-async conn [[:db.fn/cas [:Patient/id "0"] :instance/version 0 1]])

      @(-> (transact-async conn [[:db.fn/cas [:Patient/id "0"] :instance/version 0 1]])
           (md/catch'
             (fn [{::anom/keys [category]}]
               (is (= ::anom/conflict category))))))))
