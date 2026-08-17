(ns blaze.db.node.waiters-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.db.node.waiters :as waiters]
   [blaze.db.node.waiters-spec]
   [blaze.test-util :as tu :refer [given-failed-future]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- waiting-on
  "Returns waiters with one waiter per t of `ts`."
  [& ts]
  (reduce waiters/add waiters/empty-waiters ts))

(deftest add-test
  (testing "registers a waiter for a t"
    (is (ac/completable-future? (get (waiters/add waiters/empty-waiters 1) 1))))

  (testing "keeps the waiter already registered for a t, so that it is shared"
    (let [waiters (waiters/add waiters/empty-waiters 1)]
      (is (identical? (get waiters 1) (get (waiters/add waiters 1) 1)))))

  (testing "keeps the waiters sorted by the t they wait for"
    (is (= [1 2 3]
           (keys (-> (waiters/add waiters/empty-waiters 3)
                     (waiters/add 1)
                     (waiters/add 2)))))))

(deftest remove-ready-test
  (testing "removes the waiters waiting for a t of at most t"
    (is (= [3] (keys (waiters/remove-ready (waiting-on 1 2 3) 2)))))

  (testing "removes nothing if the t of no waiter was reached"
    (is (= [1 2] (keys (waiters/remove-ready (waiting-on 1 2) 0)))))

  (testing "removes all waiters if the t of every waiter was reached"
    (is (empty? (waiters/remove-ready (waiting-on 1 2) 3))))

  (testing "without waiters"
    (is (empty? (waiters/remove-ready waiters/empty-waiters 1)))))

(deftest complete-ready-test
  (testing "completes the waiters waiting for a t of at most reached-t"
    (let [waiters (waiting-on 1 2 3)]
      (waiters/complete-ready! waiters 2 2)

      (is (= 2 @(get waiters 1)))
      (is (= 2 @(get waiters 2)))

      (testing "leaving the other waiters waiting"
        (is (not (ac/done? (get waiters 3)))))))

  (testing "completes with the t of the last successful transaction"
    ;; the transaction with t = 2 failed, so the waiter it releases receives the
    ;; t of the last successful transaction
    (let [waiters (waiting-on 2)]
      (waiters/complete-ready! waiters 2 1)

      (is (= 1 @(get waiters 2)))))

  (testing "without waiters"
    (is (nil? (waiters/complete-ready! waiters/empty-waiters 1 1)))))

(deftest fail-all-test
  (testing "completes all waiters exceptionally with the error of the t they wait for"
    (let [waiters (waiting-on 1 2)]
      (waiters/fail-all! waiters #(ba/ex-anom (ba/unavailable (format "msg-%d" %))))

      (given-failed-future (get waiters 1)
        ::anom/category := ::anom/unavailable
        ::anom/message := "msg-1")

      (given-failed-future (get waiters 2)
        ::anom/category := ::anom/unavailable
        ::anom/message := "msg-2")))

  (testing "without waiters"
    (is (nil? (waiters/fail-all! waiters/empty-waiters (constantly (Exception.)))))))
