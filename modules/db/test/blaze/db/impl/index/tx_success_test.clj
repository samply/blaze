(ns blaze.db.impl.index.tx-success-test
  (:require
    [blaze.db.impl.index.tx-success :as tsi]
    [blaze.db.impl.index.tx-success-spec]
    [blaze.db.kv :as kv]
    [blaze.db.kv.mem :as mem]
    [blaze.db.kv.mem-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [juxt.iota :refer [given]])
  (:import
    [com.github.benmanes.caffeine.cache Caffeine]
    [java.time Instant]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn new-mem-kv-store []
  (mem/new-mem-kv-store
    {:tx-success-index {:reverse-comparator? true}}))


(defn- new-mem-kv-store-and-cache-with [entries]
  (let [kv-store (new-mem-kv-store)
        cache (.build (Caffeine/newBuilder) (tsi/cache-loader kv-store))]
    (kv/put! kv-store entries)
    [kv-store cache]))


(deftest tx-test
  (testing "finds the transaction"
    (let [[_ cache] (new-mem-kv-store-and-cache-with
                      [(tsi/index-entry 1 Instant/EPOCH)])]
      (given (tsi/tx cache 1)
        :blaze.db/t := 1
        :blaze.db.tx/instant := Instant/EPOCH)))

  (testing "doesn't find a non-existing transaction"
    (let [[_ cache] (new-mem-kv-store-and-cache-with
                      [(tsi/index-entry 1 Instant/EPOCH)])]
      (is (nil? (tsi/tx cache 2)))))

  (testing "nothing is found on empty db"
    (let [[_ cache] (new-mem-kv-store-and-cache-with [])]
      (is (nil? (tsi/tx cache 1))))))


(deftest last-t-test
  (testing "finds the transaction"
    (let [[kv-store] (new-mem-kv-store-and-cache-with
                       [(tsi/index-entry 1 Instant/EPOCH)
                        (tsi/index-entry 2 (.plusMillis Instant/EPOCH 1))])]
      (is (= 2 (tsi/last-t kv-store)))))

  (testing "is nil on empty db"
    (let [[kv-store] (new-mem-kv-store-and-cache-with [])]
      (is (nil? (tsi/last-t kv-store))))))



