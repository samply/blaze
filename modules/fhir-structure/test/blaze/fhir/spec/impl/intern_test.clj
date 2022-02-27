(ns blaze.fhir.spec.impl.intern-test
  (:require
    [blaze.executors :as ex]
    [blaze.fhir.spec.impl.intern :as intern]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]])
  (:import
    [java.util.concurrent TimeUnit CountDownLatch]))


(set! *warn-on-reflection* true)
(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defrecord TestType [x])


(def identity-intern
  (intern/intern-value ->TestType))


(deftest intern-test
  (testing "both constructions lead to the same instance"
    (is (identical? (identity-intern "a") (identity-intern "a"))))

  (testing "parallel construction"
    (dotimes [x 100]
      (let [n 100
            pool (ex/io-pool n "constructor-%d")
            atoms (repeatedly n #(atom nil))
            latch (CountDownLatch. 1)
            ready (CountDownLatch. n)]
        (doseq [atom atoms]
          (ex/execute!
            pool
            #(do (.countDown ready)
                 (.await latch)
                 (reset! atom (identity-intern x)))))
        ;; wait for threads to be created
        (.await ready)
        (.countDown latch)
        (ex/shutdown! pool)
        (ex/await-termination pool 10 TimeUnit/SECONDS)
        (let [value (identity-intern x)]
          (is (every? #(identical? value (deref %)) atoms)))))))
