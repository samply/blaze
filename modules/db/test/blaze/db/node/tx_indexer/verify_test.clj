(ns blaze.db.node.tx-indexer.verify-test
  (:require
    [blaze.byte-string :as bs]
    [blaze.db.api :as d]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-as-of :as rao]
    [blaze.db.impl.index.resource-as-of-test-util :as rao-tu]
    [blaze.db.impl.index.rts-as-of :as rts]
    [blaze.db.impl.index.system-as-of :as sao]
    [blaze.db.impl.index.system-as-of-test-util :as sao-tu]
    [blaze.db.impl.index.system-stats :as system-stats]
    [blaze.db.impl.index.system-stats-test-util :as ss-tu]
    [blaze.db.impl.index.type-as-of :as tao]
    [blaze.db.impl.index.type-as-of-test-util :as tao-tu]
    [blaze.db.impl.index.type-stats :as type-stats]
    [blaze.db.impl.index.type-stats-test-util :as ts-tu]
    [blaze.db.kv.mem]
    [blaze.db.kv.mem-spec]
    [blaze.db.node]
    [blaze.db.node.tx-indexer.verify :as verify]
    [blaze.db.node.tx-indexer.verify-spec]
    [blaze.db.resource-handle-cache]
    [blaze.db.search-param-registry]
    [blaze.db.test-util :refer [system]]
    [blaze.db.tx-cache]
    [blaze.db.tx-log.local]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [blaze.fhir.spec.type]
    [blaze.fhir.structure-definition-repo]
    [blaze.log]
    [blaze.test-util :refer [with-system]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [clojure.walk :as walk]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def tid-patient (codec/tid "Patient"))

(def patient-0 {:fhir/type :fhir/Patient :id "0"})
(def patient-0-v2 {:fhir/type :fhir/Patient :id "0" :gender #fhir/code"male"})
(def patient-1 {:fhir/type :fhir/Patient :id "1"})
(def patient-2 {:fhir/type :fhir/Patient :id "2"})
(def patient-3 {:fhir/type :fhir/Patient :id "3"
                :identifier [#fhir/Identifier{:value "120426"}]})


(defn bytes->vec [x]
  (if (bytes? x) (vec x) x))


(defmacro is-entries= [a b]
  `(is (= (walk/postwalk bytes->vec ~a) (walk/postwalk bytes->vec ~b))))


(def ^:private deleted-hash
  "The hash of a deleted version of a resource."
  (bs/from-byte-array (byte-array 32)))


(deftest verify-tx-cmds-test
  (testing "adding one patient to an empty store"
    (with-system [{:blaze.db/keys [node]} system]
      (is-entries=
        (verify/verify-tx-cmds
          (d/db node) 1
          [{:op "put" :type "Patient" :id "0" :hash (hash/generate patient-0)}])
        (let [value (rts/encode-value (hash/generate patient-0) 1 :put)]
          [[:resource-as-of-index
            (rao/encode-key tid-patient (codec/id-byte-string "0") 1)
            value]
           [:type-as-of-index
            (tao/encode-key tid-patient 1 (codec/id-byte-string "0"))
            value]
           [:system-as-of-index
            (sao/encode-key 1 tid-patient (codec/id-byte-string "0"))
            value]
           (type-stats/index-entry tid-patient 1 {:total 1 :num-changes 1})
           (system-stats/index-entry 1 {:total 1 :num-changes 1})]))))

  (testing "adding a second version of a patient to a store containing it already"
    (with-system [{:blaze.db/keys [node]} system]
      @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0"}]])

      (is-entries=
        (verify/verify-tx-cmds
          (d/db node) 2
          [{:op "put" :type "Patient" :id "0" :hash (hash/generate patient-0-v2)}])
        (let [value (rts/encode-value (hash/generate patient-0-v2) 2 :put)]
          [[:resource-as-of-index
            (rao/encode-key tid-patient (codec/id-byte-string "0") 2)
            value]
           [:type-as-of-index
            (tao/encode-key tid-patient 2 (codec/id-byte-string "0"))
            value]
           [:system-as-of-index
            (sao/encode-key 2 tid-patient (codec/id-byte-string "0"))
            value]
           (type-stats/index-entry tid-patient 2 {:total 1 :num-changes 2})
           (system-stats/index-entry 2 {:total 1 :num-changes 2})]))))

  (testing "adding a second version of a patient to a store containing it already incl. matcher"
    (with-system [{:blaze.db/keys [node]} system]
      @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0"}]])

      (is-entries=
        (verify/verify-tx-cmds
          (d/db node) 2
          [{:op "put" :type "Patient" :id "0" :hash (hash/generate patient-0-v2)
            :if-match 1}])
        (let [value (rts/encode-value (hash/generate patient-0-v2) 2 :put)]
          [[:resource-as-of-index
            (rao/encode-key tid-patient (codec/id-byte-string "0") 2)
            value]
           [:type-as-of-index
            (tao/encode-key tid-patient 2 (codec/id-byte-string "0"))
            value]
           [:system-as-of-index
            (sao/encode-key 2 tid-patient (codec/id-byte-string "0"))
            value]
           (type-stats/index-entry tid-patient 2 {:total 1 :num-changes 2})
           (system-stats/index-entry 2 {:total 1 :num-changes 2})]))))

  (testing "deleting a patient from an empty store"
    (with-system [{:blaze.db/keys [node]} system]
      (given (verify/verify-tx-cmds
               (d/db node) 1
               [{:op "delete" :type "Patient" :id "0"}])
        [0 #(drop 1 %) rao-tu/decode-index-entry] :=
        [{:type "Patient" :id "0" :t 1}
         {:hash deleted-hash :num-changes 1 :op :delete}]

        [1 #(drop 1 %) tao-tu/decode-index-entry] :=
        [{:type "Patient" :t 1 :id "0"}
         {:hash deleted-hash :num-changes 1 :op :delete}]

        [2 #(drop 1 %) sao-tu/decode-index-entry] :=
        [{:t 1 :type "Patient" :id "0"}
         {:hash deleted-hash :num-changes 1 :op :delete}]

        [3 #(drop 1 %) ts-tu/decode-index-entry] :=
        [{:type "Patient" :t 1}
         {:total 0 :num-changes 1}]

        [4 #(drop 1 %) ss-tu/decode-index-entry] :=
        [{:t 1}
         {:total 0 :num-changes 1}])))

  (testing "deleting an already deleted patient"
    (with-system [{:blaze.db/keys [node]} system]
      @(d/transact node [[:delete "Patient" "0"]])

      (given
        (verify/verify-tx-cmds
          (d/db node) 2
          [{:op "delete" :type "Patient" :id "0"}])

        [0 #(drop 1 %) rao-tu/decode-index-entry] :=
        [{:type "Patient" :id "0" :t 2}
         {:hash deleted-hash :num-changes 2 :op :delete}]

        [1 #(drop 1 %) tao-tu/decode-index-entry] :=
        [{:type "Patient" :t 2 :id "0"}
         {:hash deleted-hash :num-changes 2 :op :delete}]

        [2 #(drop 1 %) sao-tu/decode-index-entry] :=
        [{:t 2 :type "Patient" :id "0"}
         {:hash deleted-hash :num-changes 2 :op :delete}]

        [3 #(drop 1 %) ts-tu/decode-index-entry] :=
        [{:type "Patient" :t 2}
         {:total 0 :num-changes 2}]

        [4 #(drop 1 %) ss-tu/decode-index-entry] :=
        [{:t 2}
         {:total 0 :num-changes 2}])))

  (testing "deleting an existing patient"
    (with-system [{:blaze.db/keys [node]} system]
      @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0"}]])

      (given
        (verify/verify-tx-cmds
          (d/db node) 2
          [{:op "delete" :type "Patient" :id "0"}])

        [0 #(drop 1 %) rao-tu/decode-index-entry] :=
        [{:type "Patient" :id "0" :t 2}
         {:hash deleted-hash :num-changes 2 :op :delete}]

        [1 #(drop 1 %) tao-tu/decode-index-entry] :=
        [{:type "Patient" :t 2 :id "0"}
         {:hash deleted-hash :num-changes 2 :op :delete}]

        [2 #(drop 1 %) sao-tu/decode-index-entry] :=
        [{:t 2 :type "Patient" :id "0"}
         {:hash deleted-hash :num-changes 2 :op :delete}]

        [3 #(drop 1 %) ts-tu/decode-index-entry] :=
        [{:type "Patient" :t 2}
         {:total 0 :num-changes 2}]

        [4 #(drop 1 %) ss-tu/decode-index-entry] :=
        [{:t 2}
         {:total 0 :num-changes 2}])))

  (testing "adding a second patient to a store containing already one"
    (with-system [{:blaze.db/keys [node]} system]
      @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0"}]])

      (is-entries=
        (verify/verify-tx-cmds
          (d/db node) 2
          [{:op "put" :type "Patient" :id "1" :hash (hash/generate patient-1)}])
        (let [value (rts/encode-value (hash/generate patient-1) 1 :put)]
          [[:resource-as-of-index
            (rao/encode-key tid-patient (codec/id-byte-string "1") 2)
            value]
           [:type-as-of-index
            (tao/encode-key tid-patient 2 (codec/id-byte-string "1"))
            value]
           [:system-as-of-index
            (sao/encode-key 2 tid-patient (codec/id-byte-string "1"))
            value]
           (type-stats/index-entry tid-patient 2 {:total 2 :num-changes 2})
           (system-stats/index-entry 2 {:total 2 :num-changes 2})]))))

  (testing "update conflict"
    (with-system [{:blaze.db/keys [node]} system]
      @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0"}]])

      (given
        (verify/verify-tx-cmds
          (d/db node) 2
          [{:op "put" :type "Patient" :id "0" :hash (hash/generate patient-0)
            :if-match 0}])
        ::anom/category := ::anom/conflict
        ::anom/message := "Precondition `W/\"0\"` failed on `Patient/0`."
        :http/status := 412)))

  (testing "conditional create"
    (testing "conflict"
      (with-system [{:blaze.db/keys [node]} system]
        @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0"
                                  :birthDate #fhir/date"2020"}]
                           [:put {:fhir/type :fhir/Patient :id "1"
                                  :birthDate #fhir/date"2020"}]])

        (given
          (verify/verify-tx-cmds
            (d/db node) 2
            [{:op "create" :type "Patient" :id "1"
              :hash (hash/generate patient-0)
              :if-none-exist [["birthdate" "2020"]]}])
          ::anom/category := ::anom/conflict
          ::anom/message := "Conditional create of a Patient with query `birthdate=2020` failed because at least the two matches `Patient/0/_history/1` and `Patient/1/_history/1` were found."
          :http/status := 412)))

    (testing "match"
      (with-system [{:blaze.db/keys [node]} system]
        @(d/transact node [[:put patient-3]])

        (is
          (empty?
            (verify/verify-tx-cmds
              (d/db node) 2
              [{:op "create" :type "Patient" :id "0"
                :hash (hash/generate patient-0)
                :if-none-exist [["identifier" "120426"]]}])))))

    (testing "conflict because matching resource is deleted"
      (with-system [{:blaze.db/keys [node]} system]
        @(d/transact node [[:put patient-3]])

        (given
          (verify/verify-tx-cmds
            (d/db node) 2
            [{:op "delete" :type "Patient" :id "3"}
             {:op "create" :type "Patient" :id "0"
              :hash (hash/generate patient-0)
              :if-none-exist [["identifier" "120426"]]}])
          ::anom/category := ::anom/conflict
          ::anom/message := "Duplicate transaction commands `create Patient?identifier=120426 (resolved to id 3)` and `delete Patient/3`.")))

    (testing "on recreation"
      (with-system [{:blaze.db/keys [node]} system]
        @(d/transact node [[:put patient-0]])
        @(d/transact node [[:delete "Patient" "0"]])

        (is-entries=
          (verify/verify-tx-cmds
            (d/db node) 3
            [{:op "put" :type "Patient" :id "0"
              :hash (hash/generate patient-0)}])
          (let [value (rts/encode-value (hash/generate patient-0) 3 :put)]
            [[:resource-as-of-index
              (rao/encode-key tid-patient (codec/id-byte-string "0") 3)
              value]
             [:type-as-of-index
              (tao/encode-key tid-patient 3 (codec/id-byte-string "0"))
              value]
             [:system-as-of-index
              (sao/encode-key 3 tid-patient (codec/id-byte-string "0"))
              value]
             (type-stats/index-entry tid-patient 3 {:total 1 :num-changes 3})
             (system-stats/index-entry 3 {:total 1 :num-changes 3})]))))))
