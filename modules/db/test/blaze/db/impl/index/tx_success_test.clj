(ns blaze.db.impl.index.tx-success-test
  (:require
   [blaze.db.impl.index.tx-success :as tx-success]
   [blaze.db.impl.index.tx-success-spec]
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem]
   [blaze.db.kv.mem-spec]
   [blaze.db.tx-cache]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]])
  (:import
   [java.time Instant]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(def config
  {::kv/mem {:column-families {:tx-success-index {:reverse-comparator? true}}}
   :blaze.db/tx-cache {:kv-store (ig/ref ::kv/mem)}})

(deftest tx-test
  (testing "finds the transaction"
    (with-system [{kv-store ::kv/mem cache :blaze.db/tx-cache} config]
      (kv/put! kv-store [(tx-success/index-entry 1 Instant/EPOCH)])

      (given (tx-success/tx cache 1)
        :blaze.db/t := 1
        :blaze.db.tx/instant := Instant/EPOCH)))

  (testing "doesn't find a non-existing transaction"
    (with-system [{kv-store ::kv/mem cache :blaze.db/tx-cache} config]
      (kv/put! kv-store [(tx-success/index-entry 1 Instant/EPOCH)])

      (is (nil? (tx-success/tx cache 2)))))

  (testing "nothing is found on empty db"
    (with-system [{cache :blaze.db/tx-cache} config]
      (is (nil? (tx-success/tx cache 1))))))

(deftest last-t-test
  (testing "finds the transaction"
    (with-system [{kv-store ::kv/mem} config]
      (kv/put!
       kv-store
       [(tx-success/index-entry 1 Instant/EPOCH)
        (tx-success/index-entry 2 (.plusMillis Instant/EPOCH 1))])

      (is (= 2 (tx-success/last-t kv-store)))))

  (testing "is nil on empty db"
    (with-system [{kv-store ::kv/mem} config]
      (is (nil? (tx-success/last-t kv-store))))))
