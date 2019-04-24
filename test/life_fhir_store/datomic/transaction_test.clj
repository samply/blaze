(ns life-fhir-store.datomic.transaction-test
  (:require
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [datomic.api :as d]
    [juxt.iota :refer [given]]
    [life-fhir-store.datomic.coding :as coding]
    [life-fhir-store.datomic.schema :as schema]
    [life-fhir-store.datomic.value :as value]
    [life-fhir-store.datomic.transaction :refer [update-tx-data coerce-value]]
    [life-fhir-store.structure-definition :refer [read-structure-definitions]])
  (:import
    [java.time Year YearMonth LocalDate LocalDateTime OffsetDateTime ZoneOffset]))

(st/instrument)

(d/delete-database "datomic:mem://test")
(d/create-database "datomic:mem://test")

(def conn (d/connect "datomic:mem://test"))
(def structure-definitions (read-structure-definitions "fhir/r4"))

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
             {:resourceType "Observation" :valueQuantity {:value 1M :unit "m"}}))))))

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
