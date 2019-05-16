(ns blaze.datomic.pull-test
  (:require
    [blaze.datomic.pull :refer :all]
    [blaze.datomic.quantity :refer [quantity]]
    [blaze.datomic.test-util :refer :all]
    [blaze.datomic.schema :as schema]
    [blaze.datomic.value :as value]
    [blaze.structure-definition :refer [read-structure-definitions]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [datomic.api :as d]
    [datomic-spec.test :as dst]
    [datomic-tools.schema :as dts]
    [juxt.iota :refer [given]])
  (:import
    [java.time Year LocalDateTime]))


(st/instrument)
(dst/instrument)


(def structure-definitions (read-structure-definitions "fhir/r4/structure-definitions"))


(defn- connect []
  (d/delete-database "datomic:mem://datomic.pull-test")
  (d/create-database "datomic:mem://datomic.pull-test")
  (let [conn (d/connect "datomic:mem://datomic.pull-test")]
    @(d/transact conn (dts/schema))
    @(d/transact conn (schema/structure-definition-schemas (vals structure-definitions)))
    conn))


(def db (d/db (connect)))


(deftest pull-resource-test

  (testing "meta.versionId"
    (let [[db] (with-resource db "Patient" "0" :version 42)]
      (given (pull-resource db "Patient" "0")
        ;; this is the t of the last transaction. it could change if the
        ;; transactions before change
        ["meta" "versionId"] := "1100")))

  (testing "meta.lastUpdated"
    (let [[db] (with-resource db "Patient" "0" :version 42)]
      (string? (get-in (pull-resource db "Patient" "0") ["meta" "lastUpdated"]))))

  (testing "primitive single-valued single-typed element"
    (testing "with boolean type"
      (let [[db] (with-resource db "Patient" "0" :Patient/active true)]
        (given (pull-resource db "Patient" "0")
          "active" := true)))

    (testing "with code type"
      (let [[db id] (with-gender-code db "male")
            [db] (with-resource db "Patient" "0" :Patient/gender id)]
        (given (pull-resource db "Patient" "0")
          "gender" := "male")))

    (testing "with date type"
      (let [[db] (with-resource db "Patient" "0" :Patient/birthDate
                                (value/write (Year/of 2000)))]
        (given (pull-resource db "Patient" "0")
          "birthDate" := "2000"))))


  (testing "Coding"
    (let [[db id] (with-icd10-code db "2016" "Q14")
          [db id] (with-non-primitive db :Coding/code id)
          [db id] (with-non-primitive db :CodeableConcept/coding id)
          [db] (with-resource db "Observation" "0" :Observation/code id)]
      (given (pull-resource db "Observation" "0")
        ["code" "coding" first]
        := {"system" "http://hl7.org/fhir/sid/icd-10"
            "version" "2016"
            "code" "Q14"})))


  (testing "Quantity"
    (let [[db]
          (with-resource
            db "Observation" "0"
            :Observation/valueQuantity (value/write (quantity 1M "m"))
            :Observation/value :Observation/valueQuantity)]
      (given (pull-resource db "Observation" "0")
        "valueQuantity" := {"value" 1M "unit" "m"})))


  (testing "DateTime"
    (let [[db]
          (with-resource
            db "Observation" "0"
            :Observation/valueDateTime (value/write (LocalDateTime/of 2019 5 8 18 21 42))
            :Observation/value :Observation/valueDateTime)]
      (given (pull-resource db "Observation" "0")
        "valueDateTime" := "2019-05-08T18:21:42"))))
