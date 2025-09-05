(ns blaze.db.impl.index.tx-error-test
  (:require
   [blaze.db.impl.index.tx-error :as tx-error]
   [blaze.db.impl.index.tx-error-spec]
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem]
   [blaze.db.kv.mem-spec]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
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
      (is (nil? (tx-error/tx-error kv-store 1)))))

  (testing "HTTP status can be stored"
    (with-system [{kv-store ::kv/mem} config]
      (kv/put! kv-store
               [(tx-error/index-entry
                 1 {::anom/category ::anom/conflict
                    :http/status 412})])

      (given (tx-error/tx-error kv-store 1)
        ::anom/category := ::anom/conflict
        :http/status := 412)))

  (testing "Transaction command can be stored"
    (let [tx-cmd {:op "keep" :type "Patient" :id "0"
                  :hash #blaze/hash"5EE37C94FB1626111B5C2D37F7C2ECAF21B50B9D0FB45FA189889F38D0F9A470"}]
      (with-system [{kv-store ::kv/mem} config]
        (kv/put! kv-store
                 [(tx-error/index-entry
                   1 {::anom/category ::anom/fault
                      :blaze.db/tx-cmd tx-cmd})])

        (given (tx-error/tx-error kv-store 1)
          ::anom/category := ::anom/fault
          :blaze.db/tx-cmd tx-cmd)))))
