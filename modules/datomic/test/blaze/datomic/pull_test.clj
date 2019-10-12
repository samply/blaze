(ns blaze.datomic.pull-test
  (:require
    [blaze.datomic.pull :refer [pull-resource]]
    [blaze.datomic.quantity :refer [quantity]]
    [blaze.datomic.test-util :as test-util]
    [blaze.datomic.value :as value]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [datomic.api :as d]
    [datomic-spec.test :as dst]
    [juxt.iota :refer [given]])
  (:import
    [java.time Year LocalDateTime]
    [java.util Base64]))


(defn fixture [f]
  (st/instrument)
  (dst/instrument)
  (f)
  (st/unstrument))


(use-fixtures :each fixture)


(defonce db (d/db (st/with-instrument-disabled (test-util/connect))))


(defn- b64-decode [s]
  (.decode (Base64/getDecoder) ^String s))


(deftest pull-resource-test

  (testing "meta.versionId"
    (let [[db] (test-util/with-resource db "Patient" "0")]
      (given (pull-resource db "Patient" "0")
        ;; this is the t of the last transaction. it could change if the
        ;; transactions before change
        ["meta" "versionId"] := "9838")))

  (testing "meta.lastUpdated"
    (let [[db] (test-util/with-resource db "Patient" "0")]
      (is (string? (get-in (pull-resource db "Patient" "0") ["meta" "lastUpdated"])))))

  (testing "deleted"
    (let [[db] (test-util/with-deleted-resource db "Patient" "0")]
      (given (meta (pull-resource db "Patient" "0"))
        :deleted := true)))

  (testing "primitive single-valued single-typed element"
    (testing "with boolean type"
      (let [[db] (test-util/with-resource db "Patient" "0" :Patient/active true)]
        (given (pull-resource db "Patient" "0")
          "active" := true)))

    (testing "with code type"
      (let [[db id] (test-util/with-gender-code db "male")
            [db] (test-util/with-resource db "Patient" "0" :Patient/gender id)]
        (given (pull-resource db "Patient" "0")
          "gender" := 'male)))

    (testing "with date type"
      (let [[db] (test-util/with-resource db "Patient" "0" :Patient/birthDate
                                (value/write (Year/of 2000)))]
        (given (pull-resource db "Patient" "0")
          "birthDate" := "2000")))

    (testing "with base64Binary type"
      (let [[db id] (test-util/with-non-primitive db :Attachment/data (value/write (b64-decode "aGFsbG8=")))
            [db] (test-util/with-resource db "Patient" "0" :Patient/photo id)]
        (given (pull-resource db "Patient" "0")
          ["photo" first "data"] := "aGFsbG8="))))


  (testing "Coding"
    (let [[db id] (test-util/with-icd10-code db "2016" "Q14")
          [db id] (test-util/with-non-primitive db :Coding/code id)
          [db id] (test-util/with-non-primitive db :CodeableConcept/coding id)
          [db] (test-util/with-resource db "Observation" "0" :Observation/code id)]
      (given (pull-resource db "Observation" "0")
        ["code" "coding" first]
        := {"system" "http://hl7.org/fhir/sid/icd-10"
            "version" "2016"
            "code" "Q14"})))


  (testing "Quantity"
    (let [[db]
          (test-util/with-resource
            db "Observation" "0"
            :Observation/valueQuantity (value/write (quantity 1M "m"))
            :Observation/value :Observation/valueQuantity)]
      (given (pull-resource db "Observation" "0")
        "valueQuantity" := {"value" 1M "system" "http://unitsofmeasure.org" "code" "m"})))


  (testing "DateTime"
    (let [[db]
          (test-util/with-resource
            db "Observation" "0"
            :Observation/valueDateTime (value/write (LocalDateTime/of 2019 5 8 18 21 42))
            :Observation/value :Observation/valueDateTime)]
      (given (pull-resource db "Observation" "0")
        "valueDateTime" := "2019-05-08T18:21:42")))


  (testing "Contact"
    (let [[db id] (test-util/with-non-primitive db :HumanName/family "Doe")
          [db id] (test-util/with-non-primitive db :Patient.contact/name id)
          [db] (test-util/with-resource db "Patient" "0" :Patient/contact id)]
      (given (pull-resource db "Patient" "0")
        ["contact" first "name" "family"] := "Doe")))


  (testing "Reference"
    (let [[db id] (test-util/with-resource db "Organization" "0")
          [db] (test-util/with-resource db "Patient" "0" :Patient/managingOrganization id)]
      (given (pull-resource db "Patient" "0")
        ["managingOrganization" "reference"] := "Organization/0")))


  (testing "Contained resource"
    (let [[db id] (test-util/with-non-primitive db :Patient/active true :local-id "1")
          [db] (test-util/with-resource db "Observation" "0"
                              :Observation/contained id
                              :Observation/subject id)]
      (given (pull-resource db "Observation" "0")
        ["contained" 0 "id"] := "1"
        ["subject" "reference"] := "#1"))))
