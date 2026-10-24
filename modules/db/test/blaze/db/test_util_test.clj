(ns blaze.db.test-util-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.db.api :as d]
   [blaze.db.api-spec]
   [blaze.db.test-util :as dtu :refer [config with-db with-system-data]]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:dynamic *value* nil)

(deftest with-db-test
  (testing "binds the database value at the current t"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:put {:fhir/type :fhir/Patient :id "1"}]]]

      (with-db [db node]
        (is (= 2 (d/t db))))))

  (testing "binds the database value at t"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:put {:fhir/type :fhir/Patient :id "1"}]]]

      (with-db [db node 1]
        (is (= 1 (d/t db))))))

  (testing "conveys the bindings to the body when it waits for t"
    (with-system [{:blaze.db/keys [node]} config]
      (let [t (atom nil)
            value (atom nil)
            result (future
                     (binding [*value* ::bound]
                       (with-db [db node 1]
                         (reset! t (d/t db))
                         (reset! value *value*))))]
        (is (dtu/wait-for (:state node) #(contains? (:waiters %) 1)))
        @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0"}]])
        @result
        (is (= 1 @t))
        (is (= ::bound @value)))))

  (testing "returns nil"
    (with-system [{:blaze.db/keys [node]} config]
      (is (nil? (with-db [_ node] :value)))))

  (testing "ends the scope after the body"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (let [db (atom nil)]
        (with-db [db' node]
          (reset! db db'))
        (given (ba/try-anomaly (d/resource-handle @db "Patient" "0"))
          ::anom/category := ::anom/fault
          ::anom/message := "The snapshot is closed.")))))
