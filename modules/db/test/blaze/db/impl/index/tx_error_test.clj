(ns blaze.db.impl.index.tx-error-test
  (:require
    [blaze.db.impl.index.tx-error :as te]
    [blaze.db.impl.index.tx-error-spec]
    [blaze.db.kv :as kv]
    [blaze.db.kv.mem :as mem]
    [blaze.db.kv.mem-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn new-mem-kv-store []
  (mem/new-mem-kv-store
    {:tx-error-index nil}))


(defn- new-mem-kv-store-with [entries]
  (let [kv-store (new-mem-kv-store)]
    (kv/put! kv-store entries)
    kv-store))


(deftest tx-test
  (testing "finds the transaction error"
    (let [kv-store (new-mem-kv-store-with
                     [(te/index-entry 1 {::anom/category ::anom/fault})])]
      (given (te/tx-error kv-store 1)
        ::anom/category := ::anom/fault)))

  (testing "doesn't find a non-existing transaction error"
    (let [kv-store (new-mem-kv-store-with
                     [(te/index-entry 1 {::anom/category ::anom/fault})])]
      (is (nil? (te/tx-error kv-store 2)))))

  (testing "nothing is found on empty db"
    (let [kv-store (new-mem-kv-store)]
      (is (nil? (te/tx-error kv-store 1))))))
