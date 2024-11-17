(ns blaze.db.tx-log.local.codec-test
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.db.tx-log.local.codec :as codec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [java.time Instant]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def t
  212547)

(def patient-hash
  #blaze/hash"8F0CE8F4817600F79A087CB3E7BDD0CC84EC592D489B540B9B1C74A4829A1592")

(deftest encode-tx-data-test
  (given (codec/decode-tx-data
          [(-> (codec/encode-key t) bb/wrap)
           (-> (codec/encode-tx-data
                Instant/EPOCH
                [{:op "create"
                  :type "Patient"
                  :id "id-211709"
                  :hash patient-hash}])
               bb/wrap)])
    :instant := Instant/EPOCH
    [:tx-cmds 0 :op] := "create"
    [:tx-cmds 0 :type] := "Patient"
    [:tx-cmds 0 :id] := "id-211709"
    [:tx-cmds 0 :hash] := patient-hash
    :t := t))

(deftest decode-tx-data-test
  (testing "empty value buffer"
    (given (codec/decode-tx-data [(bb/allocate 8) (bb/allocate 0)])
      :instant := nil
      :tx-cmds := []
      :t 0)))
