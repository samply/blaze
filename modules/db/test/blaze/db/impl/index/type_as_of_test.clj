(ns blaze.db.impl.index.type-as-of-test
  (:require
   [blaze.db.api :as d]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.db :as db]
   [blaze.db.impl.db-spec]
   [blaze.db.impl.index.type-as-of :as tao]
   [blaze.db.impl.index.type-as-of-spec]
   [blaze.db.test-util :refer [config with-system-data]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [juxt.iota :refer [given]]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- with-since-db
  "Calls `f` with the database value at the current t of `node` including only
  changes after `since-t` and returns its result."
  [node since-t f]
  (db/call-with-db node (:t @(:state node)) since-t f))

(defn- list-desc [node since-t]
  (with-since-db node since-t
    (fn [db]
      (into [] (map :id) (tao/type-list-desc db (codec/tid "Patient")
                                             since-t (d/t db) nil)))))

(defn- list-asc [node since-t]
  (with-since-db node since-t
    #(into [] (map :id) (tao/type-list-asc % (codec/tid "Patient")))))

(deftest type-list-test
  (testing "three patients, each in its own transaction"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:put {:fhir/type :fhir/Patient :id "1"}]]
       [[:put {:fhir/type :fhir/Patient :id "2"}]]]

      (testing "descending returns all current resources, newest first"
        (given (list-desc node 0)
          identity := ["2" "1" "0"]))

      (testing "ascending returns all current resources, oldest first"
        (given (list-asc node 0)
          identity := ["0" "1" "2"]))

      (testing "ascending skips entries at or before since-t"
        (given (list-asc node 1)
          identity := ["1" "2"]))))

  (testing "only the current version of a resource is returned"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:put {:fhir/type :fhir/Patient :id "0"
               :active #fhir/boolean true}]]]

      (testing "descending"
        (given (list-desc node 0)
          identity := ["0"]))

      (testing "ascending"
        (given (list-asc node 0)
          identity := ["0"]))))

  (testing "a deleted resource isn't returned"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:put {:fhir/type :fhir/Patient :id "1"}]]
       [[:delete "Patient" "1"]]]

      (testing "descending"
        (given (list-desc node 0)
          identity := ["0"]))

      (testing "ascending"
        (given (list-asc node 0)
          identity := ["0"]))))

  (testing "ascending resumption fails when start-t is smaller than since-t"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:put {:fhir/type :fhir/Patient :id "1"}]]
       [[:put {:fhir/type :fhir/Patient :id "2"}]]]

      (with-since-db node 2
        (fn [db]
          (is (thrown-with-msg?
               AssertionError #"start-t 1 must not be smaller than since-t 2"
               (tao/type-list-asc db (codec/tid "Patient") 1 "0"))))))))
