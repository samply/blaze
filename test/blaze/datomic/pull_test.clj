(ns blaze.datomic.pull-test
  (:require
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [datomic.api :as d]
    [datomic-tools.schema :as dts]
    [blaze.datomic.pull :refer :all]
    [blaze.datomic.quantity :refer [quantity]]
    [blaze.datomic.test-util :refer :all]
    [blaze.datomic.schema :as schema]
    [blaze.datomic.value :as value]
    [blaze.structure-definition :refer [read-structure-definitions]])
  (:import
    [java.time Year LocalDateTime]))


(st/instrument)


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
  (testing "primitive single-valued single-typed element"
    (testing "with boolean type"
      (let [[db] (with-resource db "Patient" "0" :Patient/active true)]
        (is
          (=
            (pull-resource db "Patient" "0")
            {"id" "0" "resourceType" "Patient" "active" true}))))

    (testing "with code type"
      (let [[db id]
            (with-code db "http://hl7.org/fhir/administrative-gender"
                       "male")
            [db] (with-resource db "Patient" "0" :Patient/gender id)]
        (is
          (=
            (pull-resource db "Patient" "0")
            {"id" "0" "resourceType" "Patient" "gender" "male"}))))

    (testing "with date type"
      (let [[db] (with-resource db "Patient" "0" :Patient/birthDate
                                (value/write (Year/of 2000)))]
        (is
          (=
            (pull-resource db "Patient" "0")
            {"id" "0" "resourceType" "Patient" "birthDate" "2000"})))))

  (testing "Coding"
    (let [[db id]
          (with-code db "http://hl7.org/fhir/sid/icd-10" "2016" "Q14")
          [db id] (with-non-primitive db :Coding/code id)
          [db id] (with-non-primitive db :CodeableConcept/coding id)
          [db] (with-resource db "Observation" "0" :Observation/code id)]
      (is
        (= (pull-resource db "Observation" "0")
           {"id" "0"
            "resourceType" "Observation"
            "code"
            {"coding"
             [{"system" "http://hl7.org/fhir/sid/icd-10"
               "version" "2016"
               "code" "Q14"}]}}))))

  (testing "Quantity"
    (let [[db]
          (with-resource
            db "Observation" "0"
            :Observation/valueQuantity (value/write (quantity 1M "m"))
            :Observation/value :Observation/valueQuantity)]
      (is
        (= (pull-resource db "Observation" "0")
           {"id" "0"
            "resourceType" "Observation"
            "valueQuantity"
            {"value" 1M
             "unit" "m"}}))))

  (testing "DateTime"
    (let [[db]
          (with-resource
            db "Observation" "0"
            :Observation/valueDateTime (value/write (LocalDateTime/of 2019 5 8 18 21 42))
            :Observation/value :Observation/valueDateTime)]
      (is
        (= (pull-resource db "Observation" "0")
           {"id" "0"
            "resourceType" "Observation"
            "valueDateTime" "2019-05-08T18:21:42"})))))


(deftest summary-pattern-test
  (is (= (summary-pattern (d/pull db type-pattern :Patient))
         [:Patient/id
          :Patient/meta
          :Patient/implicitRules
          :Patient/identifier
          :Patient/active
          :Patient/name
          :Patient/telecom
          :Patient/gender
          :Patient/birthDate
          :Patient/deceased
          :Patient/address
          :Patient/managingOrganization
          #:Patient{:link [:Patient.link/modifierExtension :Patient.link/other :Patient.link/type]}])))


(comment
  (def db (d/db (connect)))

  (d/touch (d/entity db :Patient))
  (d/touch (d/entity db :Patient/id))
  (d/touch (d/entity db :Patient/link))

  )
