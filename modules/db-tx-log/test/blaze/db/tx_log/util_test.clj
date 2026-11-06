(ns blaze.db.tx-log.util-test
  (:require
   [blaze.db.tx-log.util :as u]
   [blaze.db.tx-log.util-spec]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.fhir.test-util]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [java.time Instant]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private tx-data
  {:t 1
   :instant Instant/EPOCH
   :tx-cmds [{:op "create" :type "Patient" :id "0"
              :hash (hash/generate {:fhir/type :fhir/Patient :id "0"})}]})

(deftest valid-tx-data-test
  (testing "valid transaction data is returned"
    (given (u/valid-tx-data tx-data)
      :t := 1
      :instant := Instant/EPOCH
      [:tx-cmds 0 :id] := "0")

    (given (u/valid-tx-data (assoc tx-data :permissions {:write-access-control #{"read-only"}}))
      :permissions := {:write-access-control #{"read-only"}}))

  (testing "invalid transaction data is skipped"
    (are [tx-data] (nil? (u/valid-tx-data tx-data))
      (dissoc tx-data :instant)
      (assoc tx-data :instant 0)
      (dissoc tx-data :tx-cmds)
      (assoc tx-data :tx-cmds [{:op "create"}])
      (assoc tx-data :permissions nil)
      (assoc tx-data :permissions {:write-access-control "read-only"})))

  (testing "a warning names the point in time"
    (let [warnings (atom [])]
      (log/with-merged-config
        {:appenders {:capture {:enabled? true :min-level :warn
                               :fn #(swap! warnings conj (force (:msg_ %)))}}}
        (u/valid-tx-data (dissoc tx-data :instant)))
      (is (= 1 (count @warnings)))
      (is (re-find #"^Skip transaction with point in time of 1 because tx-data is invalid: " (first @warnings))))))
