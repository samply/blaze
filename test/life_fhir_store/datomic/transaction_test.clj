(ns life-fhir-store.datomic.transaction-test
  (:require
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [datomic.api :as d]
    [juxt.iota :refer [given]]
    [life-fhir-store.datomic.coding :as coding]
    [life-fhir-store.datomic.schema :as schema]
    [life-fhir-store.datomic.transaction :refer [update-tx-data]]
    [life-fhir-store.util :as u]))

(st/instrument)

(d/create-database "datomic:mem://test")

(def conn (d/connect "datomic:mem://test"))
(def structure-definitions (u/read-structure-definitions "fhir/r4"))

@(d/transact conn (schema/all-schema (vals structure-definitions)))

(defn count-resources [db type]
  (d/q '[:find (count ?e) . :in $ ?id :where [?e ?id]] db (keyword type "id")))

(defn part= [db part-ident ident]
  (= (d/entid db part-ident) (d/part (d/entid db ident))))

(deftest update-tx-data-test
  (testing "Adds non-existing facts"
    (is
      (=
        (update-tx-data
          structure-definitions
          {:db/id 1}
          {:resourceType "Coding" :code "foo"})
        [[:db/add 1 :Coding/code "foo"]])))

  (testing "Changes nothing"
    (is
      (empty?
        (update-tx-data
          structure-definitions
          {:db/id 1 :Coding/code "foo"}
          {:resourceType "Coding" :code "foo"}))))

  (testing "Updates already existing fact"
    (is
      (=
        (update-tx-data
          structure-definitions
          {:db/id 1 :Coding/code "foo"}
          {:resourceType "Coding" :code "bar"})
        [[:db/add 1 :Coding/code "bar"]])))

  (testing "Adds non-existing fact in multi-valued element"
    ;; TODO: test for specific tx-data
    (is
      (update-tx-data
        structure-definitions
        {:db/id 1}
        {:resourceType "CodeableConcept" :coding [{:code "foo"}]})))

  (testing "Changes nothing in multi-valued element"
    (is
      (empty?
        (update-tx-data
          structure-definitions
          {:db/id 1 :coding [{:db/id 2 :Coding/code "foo"}]}
          {:resourceType "CodeableConcept" :coding [{:code "foo"}]}))))

  (testing "Updates already existing fact in multi-valued element"
    (is
      (=
        (update-tx-data
          structure-definitions
          {:db/id 1 :Observation/identifier [{:db/id 2 :Identifier/value "foo"}]}
          {:resourceType "Observation" :identifier [{:value "bar"}]})
        [[:db/add 2 :Identifier/value "bar"]])))

  (testing "Inserts a single coding"
    (let [coding {:system "foo"
                  :code "bar"}
          {db :db-after}
          (d/with
            (d/db conn)
            (update-tx-data
              structure-definitions
              {:db/id 1}
              {:resourceType "Observation"
               :code {:coding [coding]}}))]

      (testing "has a DB ID in the right partition"
        (is (part= db :life.part/Coding [:Coding/id (coding/id coding)])))

      (testing "with the right attributes"
        (given (d/pull db '[*] [:Coding/id (coding/id coding)])
          :Coding/system := "foo"
          :Coding/code := "bar"))))

  (testing "Inserts two identical codings only once"
    (let [coding {:system "foo"
                  :code "bar"}
          {db :db-after}
          (d/with
            (d/db conn)
            (update-tx-data
              structure-definitions
              {:db/id 1}
              {:resourceType "Observation"
               :code {:coding [coding coding]}}))]

      (is (= 1 (count-resources db "Coding")))))

  (testing "Adds valueQuantity"
    (is
      (= 1
         (count
           (update-tx-data
             structure-definitions
             {:db/id 1}
             {:resourceType "Observation" :valueQuantity {:value 1.0}}))))))
