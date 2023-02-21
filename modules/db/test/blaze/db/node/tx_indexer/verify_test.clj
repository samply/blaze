(ns blaze.db.node.tx-indexer.verify-test
  (:require
    [blaze.db.api :as d]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-as-of-test-util :as rao-tu]
    [blaze.db.impl.index.resource-id-test-util :as ri-tu]
    [blaze.db.impl.index.system-as-of-test-util :as sao-tu]
    [blaze.db.impl.index.system-stats-test-util :as ss-tu]
    [blaze.db.impl.index.type-as-of-test-util :as tao-tu]
    [blaze.db.impl.index.type-stats-test-util :as ts-tu]
    [blaze.db.kv.mem]
    [blaze.db.kv.mem-spec]
    [blaze.db.node]
    [blaze.db.node.tx-indexer.verify :as verify]
    [blaze.db.node.tx-indexer.verify-spec]
    [blaze.db.resource-handle-cache]
    [blaze.db.search-param-registry]
    [blaze.db.test-util :refer [system with-system-data]]
    [blaze.db.tx-cache]
    [blaze.db.tx-log.local]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [blaze.fhir.spec.type]
    [blaze.fhir.structure-definition-repo]
    [blaze.log]
    [blaze.test-util :as tu :refer [with-system]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(test/use-fixtures :each tu/fixture)


(def patient-0 {:fhir/type :fhir/Patient :id "0"})
(def patient-0-v2 {:fhir/type :fhir/Patient :id "0" :gender #fhir/code"male"})
(def patient-1 {:fhir/type :fhir/Patient :id "1"})
(def patient-2 {:fhir/type :fhir/Patient :id "2"
                :identifier [#fhir/Identifier{:value "120426"}]})


(deftest verify-tx-cmds-test
  (testing "adding one patient to an empty store"
    (let [did (codec/did 1 0)
          hash (hash/generate patient-0)]
      (doseq [op [:create :put]
              if-none-match [nil "*"]]
        (with-system [{:blaze.db/keys [node]} system]
          (given (verify/verify-tx-cmds
                   (d/db node) 1
                   [(cond-> {:op (name op) :type "Patient" :id "0" :hash hash}
                      if-none-match
                      (assoc :if-none-match if-none-match))])
            [0 0 0] := :resource-as-of-index
            [0 0 1 rao-tu/decode-key] := {:type "Patient" :did did :t 1}
            [0 0 2 rao-tu/decode-val] := {:hash hash :num-changes 1 :op op :id "0"}

            [0 1 0] := :type-as-of-index
            [0 1 1 tao-tu/decode-key] := {:type "Patient" :t 1 :did did}
            [0 1 2 tao-tu/decode-val] := {:hash hash :num-changes 1 :op op :id "0"}

            [0 2 0] := :system-as-of-index
            [0 2 1 sao-tu/decode-key] := {:t 1 :type "Patient" :did did}
            [0 2 2 sao-tu/decode-val] := {:hash hash :num-changes 1 :op op :id "0"}

            [0 3 0] := :resource-id-index
            [0 3 1 ri-tu/decode-key] := {:type "Patient" :id "0"}
            [0 3 2 ri-tu/decode-val] := {:did did}

            [0 4 0] := :type-stats-index
            [0 4 1 ts-tu/decode-key] := {:type "Patient" :t 1}
            [0 4 2 ts-tu/decode-val] := {:total 1 :num-changes 1}

            [0 5 0] := :system-stats-index
            [0 5 1 ss-tu/decode-key] := {:t 1}
            [0 5 2 ss-tu/decode-val] := {:total 1 :num-changes 1}

            [1 0 :did] := did)))))

  (testing "adding a second version of a patient to a store containing it already"
    (let [did (codec/did 1 0)
          hash (hash/generate patient-0-v2)]
      (doseq [if-match [nil 1]]
        (with-system-data [{:blaze.db/keys [node]} system]
          [[[:put patient-0]]]

          (given (verify/verify-tx-cmds
                   (d/db node) 2
                   [(cond-> {:op "put" :type "Patient" :id "0" :hash hash}
                      if-match
                      (assoc :if-match if-match))])
            [0 0 0] := :resource-as-of-index
            [0 0 1 rao-tu/decode-key] := {:type "Patient" :did did :t 2}
            [0 0 2 rao-tu/decode-val] := {:hash hash :num-changes 2 :op :put :id "0"}

            [0 1 0] := :type-as-of-index
            [0 1 1 tao-tu/decode-key] := {:type "Patient" :t 2 :did did}
            [0 1 2 tao-tu/decode-val] := {:hash hash :num-changes 2 :op :put :id "0"}

            [0 2 0] := :system-as-of-index
            [0 2 1 sao-tu/decode-key] := {:t 2 :type "Patient" :did did}
            [0 2 2 sao-tu/decode-val] := {:hash hash :num-changes 2 :op :put :id "0"}

            [0 3 0] := :type-stats-index
            [0 3 1 ts-tu/decode-key] := {:type "Patient" :t 2}
            [0 3 2 ts-tu/decode-val] := {:total 1 :num-changes 2}

            [0 4 0] := :system-stats-index
            [0 4 1 ss-tu/decode-key] := {:t 2}
            [0 4 2 ss-tu/decode-val] := {:total 1 :num-changes 2}

            [1 0 :did] := did)))))

  (testing "deleting a patient from an empty store"
    (let [did (codec/did 1 0)]
      (with-system [{:blaze.db/keys [node]} system]
        (given (verify/verify-tx-cmds
                 (d/db node) 1
                 [{:op "delete" :type "Patient" :id "0"}])
          [0 0 0] := :resource-as-of-index
          [0 0 1 rao-tu/decode-key] := {:type "Patient" :did did :t 1}
          [0 0 2 rao-tu/decode-val] := {:hash hash/deleted-hash :num-changes 1 :op :delete :id "0"}

          [0 1 0] := :type-as-of-index
          [0 1 1 tao-tu/decode-key] := {:type "Patient" :t 1 :did did}
          [0 1 2 tao-tu/decode-val] := {:hash hash/deleted-hash :num-changes 1 :op :delete :id "0"}

          [0 2 0] := :system-as-of-index
          [0 2 1 sao-tu/decode-key] := {:t 1 :type "Patient" :did did}
          [0 2 2 sao-tu/decode-val] := {:hash hash/deleted-hash :num-changes 1 :op :delete :id "0"}

          [0 3 0] := :resource-id-index
          [0 3 1 ri-tu/decode-key] := {:type "Patient" :id "0"}
          [0 3 2 ri-tu/decode-val] := {:did did}

          [0 4 0] := :type-stats-index
          [0 4 1 ts-tu/decode-key] := {:type "Patient" :t 1}
          [0 4 2 ts-tu/decode-val] := {:total 0 :num-changes 1}

          [0 5 0] := :system-stats-index
          [0 5 1 ss-tu/decode-key] := {:t 1}
          [0 5 2 ss-tu/decode-val] := {:total 0 :num-changes 1}

          [1] :? empty?))))

  (testing "deleting an already deleted patient"
    (let [did (codec/did 1 0)]
      (with-system-data [{:blaze.db/keys [node]} system]
        [[[:delete "Patient" "0"]]]

        (given (verify/verify-tx-cmds
                 (d/db node) 2
                 [{:op "delete" :type "Patient" :id "0"}])
          [0 0 0] := :resource-as-of-index
          [0 0 1 rao-tu/decode-key] := {:type "Patient" :did did :t 2}
          [0 0 2 rao-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete :id "0"}

          [0 1 0] := :type-as-of-index
          [0 1 1 tao-tu/decode-key] := {:type "Patient" :t 2 :did did}
          [0 1 2 tao-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete :id "0"}

          [0 2 0] := :system-as-of-index
          [0 2 1 sao-tu/decode-key] := {:t 2 :type "Patient" :did did}
          [0 2 2 sao-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete :id "0"}

          [0 3 0] := :type-stats-index
          [0 3 1 ts-tu/decode-key] := {:type "Patient" :t 2}
          [0 3 2 ts-tu/decode-val] := {:total 0 :num-changes 2}

          [0 4 0] := :system-stats-index
          [0 4 1 ss-tu/decode-key] := {:t 2}
          [0 4 2 ss-tu/decode-val] := {:total 0 :num-changes 2}

          [1] :? empty?))))

  (testing "deleting an existing patient"
    (let [did (codec/did 1 0)]
      (with-system-data [{:blaze.db/keys [node]} system]
      [[[:put patient-0]]]

        (given (verify/verify-tx-cmds
                 (d/db node) 2
                 [{:op "delete" :type "Patient" :id "0"}])
          [0 0 0] := :resource-as-of-index
          [0 0 1 rao-tu/decode-key] := {:type "Patient" :did did :t 2}
          [0 0 2 rao-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete :id "0"}

          [0 1 0] := :type-as-of-index
          [0 1 1 tao-tu/decode-key] := {:type "Patient" :t 2 :did did}
          [0 1 2 tao-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete :id "0"}

          [0 2 0] := :system-as-of-index
          [0 2 1 sao-tu/decode-key] := {:t 2 :type "Patient" :did did}
          [0 2 2 sao-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete :id "0"}

          [0 3 0] := :type-stats-index
          [0 3 1 ts-tu/decode-key] := {:type "Patient" :t 2}
          [0 3 2 ts-tu/decode-val] := {:total 0 :num-changes 2}

          [0 4 0] := :system-stats-index
          [0 4 1 ss-tu/decode-key] := {:t 2}
          [0 4 2 ss-tu/decode-val] := {:total 0 :num-changes 2}

          [1] :? empty?))))

  (testing "adding a second patient to a store containing already one"
    (let [did (codec/did 2 0)
          hash (hash/generate patient-1)]
      (with-system-data [{:blaze.db/keys [node]} system]
        [[[:put patient-0]]]

        (given (verify/verify-tx-cmds
                 (d/db node) 2
                 [{:op "put" :type "Patient" :id "1" :hash hash}])
          [0 0 0] := :resource-as-of-index
          [0 0 1 rao-tu/decode-key] := {:type "Patient" :did did :t 2}
          [0 0 2 rao-tu/decode-val] := {:hash hash :num-changes 1 :op :put :id "1"}

          [0 1 0] := :type-as-of-index
          [0 1 1 tao-tu/decode-key] := {:type "Patient" :t 2 :did did}
          [0 1 2 tao-tu/decode-val] := {:hash hash :num-changes 1 :op :put :id "1"}

          [0 2 0] := :system-as-of-index
          [0 2 1 sao-tu/decode-key] := {:t 2 :type "Patient" :did did}
          [0 2 2 sao-tu/decode-val] := {:hash hash :num-changes 1 :op :put :id "1"}

          [0 3 0] := :resource-id-index
          [0 3 1 ri-tu/decode-key] := {:type "Patient" :id "1"}
          [0 3 2 ri-tu/decode-val] := {:did did}

          [0 4 0] := :type-stats-index
          [0 4 1 ts-tu/decode-key] := {:type "Patient" :t 2}
          [0 4 2 ts-tu/decode-val] := {:total 2 :num-changes 2}

          [0 5 0] := :system-stats-index
          [0 5 1 ss-tu/decode-key] := {:t 2}
          [0 5 2 ss-tu/decode-val] := {:total 2 :num-changes 2}

          [1 0 :did] := did))))

  (testing "update conflict"
    (testing "using non-matching if-match"
      (with-system-data [{:blaze.db/keys [node]} system]
        [[[:put patient-0]]]

        (given (verify/verify-tx-cmds
                 (d/db node) 2
                 [{:op "put" :type "Patient" :id "0"
                   :hash (hash/generate patient-0)
                   :if-match 0}])
          ::anom/category := ::anom/conflict
          ::anom/message := "Precondition `W/\"0\"` failed on `Patient/0`."
          :http/status := 412)))

    (testing "using if-none-match of `*`"
      (with-system-data [{:blaze.db/keys [node]} system]
        [[[:put patient-0]]]

        (given (verify/verify-tx-cmds
                 (d/db node) 2
                 [{:op "put" :type "Patient" :id "0"
                   :hash (hash/generate patient-0)
                   :if-none-match "*"}])
          ::anom/category := ::anom/conflict
          ::anom/message := "Resource `Patient/0` already exists."
          :http/status := 412)))

    (testing "using matching if-none-match"
      (with-system-data [{:blaze.db/keys [node]} system]
        [[[:put patient-0]]]

        (given (verify/verify-tx-cmds
                 (d/db node) 2
                 [{:op "put" :type "Patient" :id "0"
                   :hash (hash/generate patient-0)
                   :if-none-match 1}])
          ::anom/category := ::anom/conflict
          ::anom/message := "Resource `Patient/0` with version 1 already exists."
          :http/status := 412))))

  (testing "conditional create"
    (testing "conflict"
      (with-system-data [{:blaze.db/keys [node]} system]
        [[[:put {:fhir/type :fhir/Patient :id "0"
                 :birthDate #fhir/date"2020"}]
          [:put {:fhir/type :fhir/Patient :id "1"
                 :birthDate #fhir/date"2020"}]]]

        (given (verify/verify-tx-cmds
                 (d/db node) 2
                 [{:op "create" :type "Patient" :id "foo"
                   :hash (hash/generate patient-0)
                   :if-none-exist [["birthdate" "2020"]]}])
          ::anom/category := ::anom/conflict
          ::anom/message := "Conditional create of a Patient with query `birthdate=2020` failed because at least the two matches `Patient/0/_history/1` and `Patient/1/_history/1` were found."
          :http/status := 412)))

    (testing "match"
      (with-system-data [{:blaze.db/keys [node]} system]
        [[[:put patient-2]]]

        (given (verify/verify-tx-cmds
                 (d/db node) 2
                 [{:op "create" :type "Patient" :id "0"
                   :hash (hash/generate patient-0)
                   :if-none-exist [["identifier" "120426"]]}])
          [0] :? empty?
          [1] :? empty?)))

    (testing "conflict because matching resource is deleted"
      (with-system-data [{:blaze.db/keys [node]} system]
        [[[:put patient-2]]]

        (given
          (verify/verify-tx-cmds
            (d/db node) 2
            [{:op "delete" :type "Patient" :id "2"}
             {:op "create" :type "Patient" :id "0"
              :hash (hash/generate patient-0)
              :if-none-exist [["identifier" "120426"]]}])
          ::anom/category := ::anom/conflict
          ::anom/message := "Duplicate transaction commands `create Patient?identifier=120426 (resolved to id 2)` and `delete Patient/2`.")))

    (testing "on recreation"
      (let [did (codec/did 1 0)
            hash (hash/generate patient-0)]
        (with-system-data [{:blaze.db/keys [node]} system]
          [[[:put patient-0]]
           [[:delete "Patient" "0"]]]

          (given (verify/verify-tx-cmds
                   (d/db node) 3
                   [{:op "put" :type "Patient" :id "0" :hash hash}])

            [0 0 0] := :resource-as-of-index
            [0 0 1 rao-tu/decode-key] := {:type "Patient" :did did :t 3}
            [0 0 2 rao-tu/decode-val] := {:hash hash :num-changes 3 :op :put :id "0"}

            [0 1 0] := :type-as-of-index
            [0 1 1 tao-tu/decode-key] := {:type "Patient" :t 3 :did did}
            [0 1 2 tao-tu/decode-val] := {:hash hash :num-changes 3 :op :put :id "0"}

            [0 2 0] := :system-as-of-index
            [0 2 1 sao-tu/decode-key] := {:t 3 :type "Patient" :did did}
            [0 2 2 sao-tu/decode-val] := {:hash hash :num-changes 3 :op :put :id "0"}

            [0 3 0] := :type-stats-index
            [0 3 1 ts-tu/decode-key] := {:type "Patient" :t 3}
            [0 3 2 ts-tu/decode-val] := {:total 1 :num-changes 3}

            [0 4 0] := :system-stats-index
            [0 4 1 ss-tu/decode-key] := {:t 3}
            [0 4 2 ss-tu/decode-val] := {:total 1 :num-changes 3}

            [1 0 :did] := did))))))
