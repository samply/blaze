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
    [blaze.db.kv.mem-spec]
    [blaze.db.node.tx-indexer.verify :as verify]
    [blaze.db.node.tx-indexer.verify-spec]
    [blaze.db.resource-handle-cache]
    [blaze.db.search-param-registry]
    [blaze.db.test-util :refer [system with-system-data]]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [blaze.fhir.spec.type]
    [blaze.fhir.structure-definition-repo]
    [blaze.log]
    [blaze.test-util :refer [with-system]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [clojure.walk :as walk]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def system
  {:blaze.db/node
   {:tx-log (ig/ref :blaze.db/tx-log)
    :resource-handle-cache (ig/ref :blaze.db/resource-handle-cache)
    :tx-cache (ig/ref :blaze.db/tx-cache)
    :indexer-executor (ig/ref :blaze.db.node/indexer-executor)
    :resource-store (ig/ref :blaze.db/resource-store)
    :kv-store (ig/ref :blaze.db/index-kv-store)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
    :poll-timeout (time/millis 10)}

   ::tx-log/local
   {:kv-store (ig/ref :blaze.db/transaction-kv-store)
    :clock (ig/ref :blaze.test/clock)}
   [::kv/mem :blaze.db/transaction-kv-store]
   {:column-families {}}
   :blaze.test/clock {}

   :blaze.db/resource-handle-cache {}

   :blaze.db/tx-cache
   {:kv-store (ig/ref :blaze.db/index-kv-store)}

   :blaze.db.node/indexer-executor {}

   [::kv/mem :blaze.db/index-kv-store]
   {:column-families
    {:search-param-value-index nil
     :resource-value-index nil
     :compartment-search-param-value-index nil
     :compartment-resource-type-index nil
     :active-search-params nil
     :tx-success-index {:reverse-comparator? true}
     :tx-error-index nil
     :t-by-instant-index {:reverse-comparator? true}
     :resource-as-of-index nil
     :type-as-of-index nil
     :system-as-of-index nil
     :type-stats-index nil
     :system-stats-index nil}}

   ::rs/kv
   {:kv-store (ig/ref :blaze.db/resource-kv-store)
    :executor (ig/ref ::rs-kv/executor)}
   [::kv/mem :blaze.db/resource-kv-store]
   {:column-families {}}
   ::rs-kv/executor {}

   :blaze.db/search-param-registry
   {:structure-definition-repo (ig/ref :blaze.fhir/structure-definition-repo)}

   :blaze.fhir/structure-definition-repo {}})


(def tid-patient (codec/tid "Patient"))
(def tid-observation (codec/tid "Observation"))

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


(def ^:private patient-hash
  #blaze/byte-string"C9ADE22457D5AD750735B6B166E3CE8D6878D09B64C2C2868DCB6DE4C9EFBD4F")


(def ^:private observation-hash
  #blaze/byte-string"7B3980C2BFCF43A8CDD61662E1AABDA9CA6431964820BC8D52958AEC9A270378")


(deftest verify-tx-cmds-test
  (testing "adding one patient to an empty store"
    (with-system [{:blaze.db/keys [node]} system]
      (is-entries=
        (verify/tx-entries
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
    (with-system-data [{:blaze.db/keys [node]} system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (is-entries=
        (verify/tx-entries
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
    (with-system-data [{:blaze.db/keys [node]} system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (is-entries=
        (verify/tx-entries
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
      (given (verify/tx-entries
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
    (with-system-data [{:blaze.db/keys [node]} system]
      [[[:delete "Patient" "0"]]]

      (given
        (verify/tx-entries
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
    (with-system-data [{:blaze.db/keys [node]} system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (given
        (verify/tx-entries
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
    (with-system-data [{:blaze.db/keys [node]} system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (is-entries=
        (verify/tx-entries
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

  (testing "on recreation"
    (with-system-data [{:blaze.db/keys [node]} system]
      [[[:put patient-0]]
       [[:delete "Patient" "0"]]]

      (is-entries=
        (verify/tx-entries
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
           (system-stats/index-entry 3 {:total 1 :num-changes 3})])))))
