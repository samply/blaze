(ns blaze.db.tx-log.local.codec-test
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.db.tx-log.local.codec :as codec]
   [blaze.db.tx-log.local.codec-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [jsonista.core :as j]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [com.fasterxml.jackson.dataformat.cbor CBORFactory]
   [java.time Instant]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def t
  212547)

(def patient-hash
  #blaze/hash"8F0CE8F4817600F79A087CB3E7BDD0CC84EC592D489B540B9B1C74A4829A1592")

(defn- round-trip [instant tx-cmds permissions]
  (codec/decode-tx-data
   [(-> (codec/encode-key t) bb/wrap)
    (-> (codec/encode-tx-data instant tx-cmds permissions) bb/wrap)]))

(def tx-cmds
  [{:op "create"
    :type "Patient"
    :id "id-211709"
    :hash patient-hash}])

(deftest encode-tx-data-test
  (testing "without permissions"
    (given (round-trip Instant/EPOCH tx-cmds nil)
      :instant := Instant/EPOCH
      [:tx-cmds 0 :op] := "create"
      [:tx-cmds 0 :type] := "Patient"
      [:tx-cmds 0 :id] := "id-211709"
      [:tx-cmds 0 :hash] := patient-hash
      :t := t)

    (testing "the transaction data contain no permissions"
      (is (not (contains? (round-trip Instant/EPOCH tx-cmds nil) :permissions)))))

  (testing "with permissions"
    (given (round-trip Instant/EPOCH tx-cmds
                       {:write-access-control #{"internal" "read-only"}})
      :instant := Instant/EPOCH
      [:tx-cmds 0 :id] := "id-211709"
      [:tx-cmds 0 :hash] := patient-hash
      :permissions := {:write-access-control #{"internal" "read-only"}}
      :t := t))

  (testing "with empty permissions"
    (given (round-trip Instant/EPOCH tx-cmds {})
      :permissions := {}))

  (testing "with AccessControl codes"
    (given (round-trip Instant/EPOCH
                       [(assoc (first tx-cmds) :access-control ["read-only"])]
                       nil)
      [:tx-cmds 0 :access-control] := ["read-only"])))

(def ^:private cbor-object-mapper
  (j/object-mapper {:factory (CBORFactory.)}))

(defn- decode-raw
  "Decodes the transaction data `x` written as CBOR without the checks of
  `encode-tx-data`."
  [x]
  (codec/decode-tx-data
   [(-> (codec/encode-key t) bb/wrap)
    (bb/wrap (j/write-value-as-bytes x cbor-object-mapper))]))

(deftest decode-tx-data-test
  (testing "invalid permissions"
    (doseq [permissions [{:write-access-control "read-only"}
                         {:write-access-control ["read-only" 1]}
                         {:write-access-control {:code "read-only"}}
                         "read-only"
                         []]]
      (testing "the transaction data is skipped"
        (is (nil? (decode-raw {:instant 0
                               :tx-cmds [{:op "delete" :type "Patient" :id "0"}]
                               :permissions permissions}))))))

  (testing "valid permissions"
    (given (decode-raw {:instant 0
                        :tx-cmds [{:op "delete" :type "Patient" :id "0"}]
                        :permissions {:write-access-control ["read-only"]}})
      :permissions := {:write-access-control #{"read-only"}}))

  (testing "empty value buffer"
    (is (nil? (codec/decode-tx-data [(bb/allocate 8) (bb/allocate 0)]))))

  (testing "key shorter than a point in time"
    (is (nil? (codec/decode-tx-data [(bb/allocate 7) (bb/allocate 0)]))))

  (testing "invalid transaction data"
    (doseq [x [1
               {:tx-cmds [{:op "delete" :type "Patient" :id "0"}]}
               {:instant "" :tx-cmds [{:op "delete" :type "Patient" :id "0"}]}
               {:instant 0}
               {:instant 0 :tx-cmds nil}
               {:instant 0 :tx-cmds 1}
               {:instant 0 :tx-cmds [{}]}
               {:instant 0 :tx-cmds [{:op "create" :type "Patient" :id "0" :hash "foo"}]}]]
      (is (nil? (decode-raw x))))))
