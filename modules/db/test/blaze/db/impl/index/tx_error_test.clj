(ns blaze.db.impl.index.tx-error-test
  (:require
    [blaze.db.impl.index.tx-error :as tx-error]
    [blaze.db.impl.index.tx-error-spec]
    [blaze.db.kv :as kv]
    [blaze.db.kv.mem]
    [blaze.db.kv.mem-spec]
    [blaze.test-util :as tu :refer [with-system]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]))


(st/instrument)


(test/use-fixtures :each tu/fixture)


(def config
  {::kv/mem {:column-families {:tx-error-index nil}}})


(deftest tx-test
  (testing "finds the transaction error"
    (with-system [{kv-store ::kv/mem} config]
      (kv/put! kv-store [(tx-error/index-entry 1 {::anom/category ::anom/fault})])

      (given (tx-error/tx-error kv-store 1)
        ::anom/category := ::anom/fault)))

  (testing "doesn't find a non-existing transaction error"
    (with-system [{kv-store ::kv/mem} config]
      (kv/put! kv-store [(tx-error/index-entry 1 {::anom/category ::anom/fault})])

      (is (nil? (tx-error/tx-error kv-store 2)))))

  (testing "nothing is found on empty db"
    (with-system [{kv-store ::kv/mem} config]
      (is (nil? (tx-error/tx-error kv-store 1))))))
