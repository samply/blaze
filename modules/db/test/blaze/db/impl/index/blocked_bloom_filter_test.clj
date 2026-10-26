(ns blaze.db.impl.index.blocked-bloom-filter-test
  (:require
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]])
  (:import
   [blaze.db.impl.index BlockedBloomFilter]
   [java.util Random]
   [java.util.concurrent Executors TimeUnit]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- random-hashes [seed n]
  (let [random (Random. seed)]
    (vec (repeatedly n #(.nextLong random)))))

(defn- put! [^BlockedBloomFilter filter hash]
  (.put filter hash))

(defn- might-contain? [^BlockedBloomFilter filter hash]
  (.mightContain filter hash))

(deftest put-test
  (testing "a new hash changes the filter and isn't new anymore afterwards"
    (let [filter (BlockedBloomFilter. 100)]
      (is (true? (put! filter 42)))
      (is (false? (put! filter 42)))
      (is (true? (might-contain? filter 42)))))

  (testing "an empty filter contains nothing"
    (let [filter (BlockedBloomFilter. 100)]
      (is (not-any? (partial might-contain? filter) (random-hashes 1 1000)))))

  (testing "counts the new hashes"
    (let [filter (BlockedBloomFilter. 100)]
      (put! filter 1)
      (put! filter 2)
      (put! filter 1)

      (is (= 2 (.count filter))))))

(deftest false-positive-test
  (let [n 100000
        bloom-filter (BlockedBloomFilter. n)
        hashes (random-hashes 1 n)]
    (run! (partial put! bloom-filter) hashes)

    (testing "has no false negatives"
      (is (every? (partial might-contain? bloom-filter) hashes)))

    (testing "has a false positive rate of about 1 % at its expected keys"
      (let [false-positives (count (filter (partial might-contain? bloom-filter)
                                           (random-hashes 2 n)))]
        (is (< (* 0.005 n) false-positives (* 0.015 n)))))))

(deftest concurrent-put-test
  (testing "concurrent puts lose no hash"
    (let [n 50000
          filter (BlockedBloomFilter. (* 4 n))
          hashes (mapv #(random-hashes % n) (range 4))
          executor (Executors/newFixedThreadPool 4)]
      (run! deref (mapv (fn [hashes]
                          (.submit executor ^Runnable
                                   #(run! (partial put! filter) hashes)))
                        hashes))
      (.shutdown executor)
      (.awaitTermination executor 10 TimeUnit/SECONDS)

      (is (every? #(every? (partial might-contain? filter) %) hashes)))))
