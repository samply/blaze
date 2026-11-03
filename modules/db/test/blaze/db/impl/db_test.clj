(ns blaze.db.impl.db-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.api-spec]
   [blaze.db.impl.db :as db]
   [blaze.db.impl.db-spec]
   [blaze.db.test-util :refer [config with-system-data]]
   [blaze.test-util :as tu :refer [given-failed-future]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest call-with-db-test
  (with-system-data [{:blaze.db/keys [node]} config]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]]
     [[:put {:fhir/type :fhir/Patient :id "1"}]]]

    (testing "calls `f` with the database value at t and returns its result"
      (is (= [1 0] (db/call-with-db node 1 0 (juxt d/t d/since-t)))))

    (testing "includes only changes after since-t"
      (is (= ["1"] (db/call-with-db node 2 1
                     #(into [] (map :id) (d/type-list % "Patient"))))))

    (testing "closes the snapshot after `f` returns"
      (let [db (db/call-with-db node 2 0 identity)]
        (given (ba/try-anomaly (d/resource-handle db "Patient" "0"))
          ::anom/category := ::anom/fault
          ::anom/message := "The snapshot is closed.")))))

(defn- closed? [db]
  (= "The snapshot is closed."
     (::anom/message (ba/try-anomaly (d/resource-handle db "Patient" "0")))))

(deftest with-db-test
  (with-system-data [{:blaze.db/keys [node]} config]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

    (testing "completes with the result of the future returned by `f`"
      (let [db-state (atom nil)]
        (is (= 1 @(db/with-db node 1 #(do (reset! db-state %)
                                          (ac/completed-future (d/t %))))))

        (testing "and closes the snapshot"
          (is (closed? @db-state)))))

    (testing "fails with the anomaly returned by `f`"
      (let [db-state (atom nil)]
        (given-failed-future
         (db/with-db node 1 #(do (reset! db-state %) (ba/incorrect "msg-183014")))
          ::anom/category := ::anom/incorrect
          ::anom/message := "msg-183014")

        (testing "and closes the snapshot"
          (is (closed? @db-state)))))

    (testing "fails with the exception thrown by `f`"
      (let [db-state (atom nil)]
        (given-failed-future
         (db/with-db node 1 #(do (reset! db-state %) (ba/throw-anom (ba/fault "msg-183156"))))
          ::anom/category := ::anom/fault
          ::anom/message := "msg-183156")

        (testing "and closes the snapshot"
          (is (closed? @db-state)))))

    (doseq [value [nil {:status 200}]]
      (testing (str "fails if `f` returns " (pr-str value) " instead of a future")
        (let [db-state (atom nil)]
          (given-failed-future
           (db/with-db node 1 #(do (reset! db-state %) value))
            ::anom/category := ::anom/fault
            ::anom/message := "The function passed to `with-db` has to return a CompletableFuture.")

          (testing "and closes the snapshot"
            (is (closed? @db-state))))))))
