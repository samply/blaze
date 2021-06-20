(ns blaze.db.impl.index.t-by-instant-test
  (:require
    [blaze.db.impl.index.t-by-instant :as ti]
    [blaze.db.kv :as kv]
    [blaze.db.kv.mem :as mem]
    [blaze.db.kv.mem-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]])
  (:import
    [java.time Instant]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn new-mem-kv-store []
  (mem/new-mem-kv-store
    {:t-by-instant-index {:reverse-comparator? true}}))


(defn- new-mem-kv-store-with [entries]
  (let [kv-store (new-mem-kv-store)]
    (kv/put! kv-store entries)
    kv-store))


(deftest t-by-instant-test
  (testing "finds t directly at instant"
    (let [kv-store (new-mem-kv-store-with
                     [(ti/index-entry Instant/EPOCH 1)])]
      (with-open [snapshot (kv/new-snapshot kv-store)]
        (is (= 1 (ti/t-by-instant snapshot Instant/EPOCH))))))

  (testing "finds t before instant"
    (let [kv-store (new-mem-kv-store-with
                     [(ti/index-entry Instant/EPOCH 1)])]
      (with-open [snapshot (kv/new-snapshot kv-store)]
        (is (= 1 (ti/t-by-instant snapshot (.plusMillis Instant/EPOCH 1)))))))

  (testing "nothing is found on empty db"
    (let [kv-store (new-mem-kv-store)]
      (with-open [snapshot (kv/new-snapshot kv-store)]
        (is (nil? (ti/t-by-instant snapshot Instant/EPOCH)))))))
