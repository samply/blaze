(ns blaze.db.node.tx-indexer.verify-test
  (:require
    [blaze.db.api :as d]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-as-of-test-util :as rao-tu]
    [blaze.db.impl.index.rts-as-of-test-util :as rts-tu]
    [blaze.db.impl.index.system-as-of-test-util :as sao-tu]
    [blaze.db.impl.index.system-stats-test-util :as ss-tu]
    [blaze.db.impl.index.type-as-of-test-util :as tao-tu]
    [blaze.db.impl.index.type-stats-test-util :as ts-tu]
    [blaze.db.kv.mem]
    [blaze.db.kv.mem-spec]
    [blaze.db.node]
    [blaze.db.node.tx-indexer.verify :as verify]
    [blaze.db.node.tx-indexer.verify-spec]
    [blaze.db.search-param-registry]
    [blaze.db.test-util :refer [config with-system-data]]
    [blaze.db.tx-cache]
    [blaze.db.tx-log.local]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [blaze.fhir.spec.type]
    [blaze.log]
    [blaze.module.test-util :refer [with-system]]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(test/use-fixtures :each tu/fixture)


(def tid-patient (codec/tid "Patient"))

(def patient-0 {:fhir/type :fhir/Patient :id "0"})
(def patient-0-v2 {:fhir/type :fhir/Patient :id "0" :gender #fhir/code"male"})
(def patient-1 {:fhir/type :fhir/Patient :id "1"})
(def patient-2 {:fhir/type :fhir/Patient :id "2"
                :identifier [#fhir/Identifier{:value "120426"}]})


(deftest verify-tx-cmds-test
  (testing "adding one Patient to an empty store"
    (let [hash (hash/generate patient-0)]
      (doseq [op [:create :put]
              if-none-match [nil "*"]]
        (with-system [{:blaze.db/keys [node]} config]
          (given (verify/verify-tx-cmds
                   (d/db node) 1
                   [(cond-> {:op (name op) :type "Patient" :id "0" :hash hash}
                      if-none-match
                      (assoc :if-none-match if-none-match))])

            count := 5

            [0 0] := :resource-as-of-index
            [0 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 1}
            [0 2 rts-tu/decode-val] := {:hash hash :num-changes 1 :op op}

            [1 0] := :type-as-of-index
            [1 1 tao-tu/decode-key] := {:type "Patient" :t 1 :id "0"}
            [1 2 rts-tu/decode-val] := {:hash hash :num-changes 1 :op op}

            [2 0] := :system-as-of-index
            [2 1 sao-tu/decode-key] := {:t 1 :type "Patient" :id "0"}
            [2 2 rts-tu/decode-val] := {:hash hash :num-changes 1 :op op}

            [3 0] := :type-stats-index
            [3 1 ts-tu/decode-key] := {:type "Patient" :t 1}
            [3 2 ts-tu/decode-val] := {:total 1 :num-changes 1}

            [4 0] := :system-stats-index
            [4 1 ss-tu/decode-key] := {:t 1}
            [4 2 ss-tu/decode-val] := {:total 1 :num-changes 1})))))

  (testing "adding a second version of a patient to a store containing it already"
    (let [hash (hash/generate patient-0-v2)]
      (doseq [if-match [nil 1 [1] [1 2]]]
        (with-system-data [{:blaze.db/keys [node]} config]
          [[[:put patient-0]]]

          (given (verify/verify-tx-cmds
                   (d/db node) 2
                   [(cond-> {:op "put" :type "Patient" :id "0" :hash hash}
                      if-match
                      (assoc :if-match if-match))])

            count := 5

            [0 0] := :resource-as-of-index
            [0 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 2}
            [0 2 rts-tu/decode-val] := {:hash hash :num-changes 2 :op :put}

            [1 0] := :type-as-of-index
            [1 1 tao-tu/decode-key] := {:type "Patient" :t 2 :id "0"}
            [1 2 rts-tu/decode-val] := {:hash hash :num-changes 2 :op :put}

            [2 0] := :system-as-of-index
            [2 1 sao-tu/decode-key] := {:t 2 :type "Patient" :id "0"}
            [2 2 rts-tu/decode-val] := {:hash hash :num-changes 2 :op :put}

            [3 0] := :type-stats-index
            [3 1 ts-tu/decode-key] := {:type "Patient" :t 2}
            [3 2 ts-tu/decode-val] := {:total 1 :num-changes 2}

            [4 0] := :system-stats-index
            [4 1 ss-tu/decode-key] := {:t 2}
            [4 2 ss-tu/decode-val] := {:total 1 :num-changes 2})))))

  (testing "adding a second version of an already deleted Patient"
    (let [hash (hash/generate patient-0-v2)]
      (doseq [if-match [nil 2 [2] [1 2]]]
        (with-system-data [{:blaze.db/keys [node]} config]
          [[[:put patient-0]]
           [[:delete "Patient" "0"]]]

          (given (verify/verify-tx-cmds
                   (d/db node) 3
                   [(cond-> {:op "put" :type "Patient" :id "0" :hash hash}
                      if-match
                      (assoc :if-match if-match))])

            count := 5

            [0 0] := :resource-as-of-index
            [0 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 3}
            [0 2 rts-tu/decode-val] := {:hash hash :num-changes 3 :op :put}

            [1 0] := :type-as-of-index
            [1 1 tao-tu/decode-key] := {:type "Patient" :t 3 :id "0"}
            [1 2 rts-tu/decode-val] := {:hash hash :num-changes 3 :op :put}

            [2 0] := :system-as-of-index
            [2 1 sao-tu/decode-key] := {:t 3 :type "Patient" :id "0"}
            [2 2 rts-tu/decode-val] := {:hash hash :num-changes 3 :op :put}

            [3 0] := :type-stats-index
            [3 1 ts-tu/decode-key] := {:type "Patient" :t 3}
            [3 2 ts-tu/decode-val] := {:total 1 :num-changes 3}

            [4 0] := :system-stats-index
            [4 1 ss-tu/decode-key] := {:t 3}
            [4 2 ss-tu/decode-val] := {:total 1 :num-changes 3})))))

  (testing "adding a Patient with identical content"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put patient-0]]]

      (is (empty? (verify/verify-tx-cmds
                    (d/db node) 2
                    [{:op "put" :type "Patient" :id "0"
                      :hash (hash/generate patient-0)}])))))

  (testing "keeping a non-existing Patient fails"
    (with-system [{:blaze.db/keys [node]} config]

      (let [tx-cmd {:op "keep" :type "Patient" :id "0" :hash (hash/generate patient-0)}]
        (given (verify/verify-tx-cmds (d/db node) 1 [tx-cmd])
          ::anom/category := ::anom/conflict
          ::anom/message := "Keep failed on `Patient/0`."
          :blaze.db/tx-cmd := tx-cmd))))

  (testing "keeping a non-matching Patient fails"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put patient-0]]
       [[:put patient-0-v2]]]

      (let [tx-cmd {:op "keep" :type "Patient" :id "0" :hash (hash/generate patient-0)}]
        (given (verify/verify-tx-cmds (d/db node) 1 [tx-cmd])
          ::anom/category := ::anom/conflict
          ::anom/message := "Keep failed on `Patient/0`."
          :blaze.db/tx-cmd := tx-cmd))))

  (testing "keeping a hash matching but non-matching if-match Patient fails"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put patient-0]]
       [[:put patient-0-v2]]]

      (testing "with a precondition failure"
        (doseq [if-match [3 [3]]]
          (let [tx-cmd {:op "keep" :type "Patient" :id "0"
                        :hash (hash/generate patient-0-v2)
                        :if-match if-match}]
            (given (verify/verify-tx-cmds
                     (d/db node) 1
                     [tx-cmd])
              ::anom/category := ::anom/conflict
              ::anom/message := "Precondition `W/\"3\"` failed on `Patient/0`."
              :http/status := 412
              :blaze.db/tx-cmd := tx-cmd))))))

  (testing "keeping a non-matching hash and non-matching if-match patient fails"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put patient-0]]
       [[:put patient-0-v2]]]

      (testing "with a precondition failure"
        (doseq [if-match [3 [3]]]
          (let [tx-cmd {:op "keep" :type "Patient" :id "0"
                        :hash (hash/generate patient-0)
                        :if-match if-match}]
            (given (verify/verify-tx-cmds (d/db node) 1 [tx-cmd])
              ::anom/category := ::anom/conflict
              ::anom/message := "Precondition `W/\"3\"` failed on `Patient/0`."
              :http/status := 412
              :blaze.db/tx-cmd := tx-cmd))))))

  (testing "keeping a matching Patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put patient-0]]]

      (testing "with different if-matches"
        (doseq [if-match [nil 1 [1] [1 2]]]
          (is (empty? (verify/verify-tx-cmds
                        (d/db node) 1
                        [(cond->
                           {:op "keep" :type "Patient" :id "0"
                            :hash (hash/generate patient-0)}
                           if-match
                           (assoc :if-match if-match))])))))))

  (testing "deleting a Patient from an empty store"
    (with-system [{:blaze.db/keys [node]} config]
      (given (verify/verify-tx-cmds
               (d/db node) 1
               [{:op "delete" :type "Patient" :id "0"}])

        count := 5

        [0 0] := :resource-as-of-index
        [0 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 1}
        [0 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 1 :op :delete}

        [1 0] := :type-as-of-index
        [1 1 tao-tu/decode-key] := {:type "Patient" :t 1 :id "0"}
        [1 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 1 :op :delete}

        [2 0] := :system-as-of-index
        [2 1 sao-tu/decode-key] := {:t 1 :type "Patient" :id "0"}
        [2 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 1 :op :delete}

        [3 0] := :type-stats-index
        [3 1 ts-tu/decode-key] := {:type "Patient" :t 1}
        [3 2 ts-tu/decode-val] := {:total 0 :num-changes 1}

        [4 0] := :system-stats-index
        [4 1 ss-tu/decode-key] := {:t 1}
        [4 2 ss-tu/decode-val] := {:total 0 :num-changes 1})))

  (testing "deleting an already deleted Patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:delete "Patient" "0"]]]

      (given (verify/verify-tx-cmds
               (d/db node) 2
               [{:op "delete" :type "Patient" :id "0"}])

        count := 5

        [0 0] := :resource-as-of-index
        [0 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 2}
        [0 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete}

        [1 0] := :type-as-of-index
        [1 1 tao-tu/decode-key] := {:type "Patient" :t 2 :id "0"}
        [1 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete}

        [2 0] := :system-as-of-index
        [2 1 sao-tu/decode-key] := {:t 2 :type "Patient" :id "0"}
        [2 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete}

        [3 0] := :type-stats-index
        [3 1 ts-tu/decode-key] := {:type "Patient" :t 2}
        [3 2 ts-tu/decode-val] := {:total 0 :num-changes 2}

        [4 0] := :system-stats-index
        [4 1 ss-tu/decode-key] := {:t 2}
        [4 2 ss-tu/decode-val] := {:total 0 :num-changes 2})))

  (testing "deleting an existing Patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put patient-0]]]

      (given (verify/verify-tx-cmds
               (d/db node) 2
               [{:op "delete" :type "Patient" :id "0"}])

        count := 5

        [0 0] := :resource-as-of-index
        [0 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 2}
        [0 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete}

        [1 0] := :type-as-of-index
        [1 1 tao-tu/decode-key] := {:type "Patient" :t 2 :id "0"}
        [1 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete}

        [2 0] := :system-as-of-index
        [2 1 sao-tu/decode-key] := {:t 2 :type "Patient" :id "0"}
        [2 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete}

        [3 0] := :type-stats-index
        [3 1 ts-tu/decode-key] := {:type "Patient" :t 2}
        [3 2 ts-tu/decode-val] := {:total 0 :num-changes 2}

        [4 0] := :system-stats-index
        [4 1 ss-tu/decode-key] := {:t 2}
        [4 2 ss-tu/decode-val] := {:total 0 :num-changes 2})))

  (testing "adding a second patient to a store containing already one"
    (let [hash (hash/generate patient-1)]
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put patient-0]]]

        (given (verify/verify-tx-cmds
                 (d/db node) 2
                 [{:op "put" :type "Patient" :id "1" :hash hash}])

          count := 5

          [0 0] := :resource-as-of-index
          [0 1 rao-tu/decode-key] := {:type "Patient" :id "1" :t 2}
          [0 2 rts-tu/decode-val] := {:hash hash :num-changes 1 :op :put}

          [1 0] := :type-as-of-index
          [1 1 tao-tu/decode-key] := {:type "Patient" :t 2 :id "1"}
          [1 2 rts-tu/decode-val] := {:hash hash :num-changes 1 :op :put}

          [2 0] := :system-as-of-index
          [2 1 sao-tu/decode-key] := {:t 2 :type "Patient" :id "1"}
          [2 2 rts-tu/decode-val] := {:hash hash :num-changes 1 :op :put}

          [3 0] := :type-stats-index
          [3 1 ts-tu/decode-key] := {:type "Patient" :t 2}
          [3 2 ts-tu/decode-val] := {:total 2 :num-changes 2}

          [4 0] := :system-stats-index
          [4 1 ss-tu/decode-key] := {:t 2}
          [4 2 ss-tu/decode-val] := {:total 2 :num-changes 2}))))

  (testing "update conflict"
    (testing "using non-matching if-match"
      (with-system-data [{:blaze.db/keys [node]} config]
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
      (with-system-data [{:blaze.db/keys [node]} config]
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
      (with-system-data [{:blaze.db/keys [node]} config]
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
      (with-system-data [{:blaze.db/keys [node]} config]
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
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put patient-2]]]

        (is
          (empty?
            (verify/verify-tx-cmds
              (d/db node) 2
              [{:op "create" :type "Patient" :id "0"
                :hash (hash/generate patient-0)
                :if-none-exist [["identifier" "120426"]]}])))))

    (testing "conflict because matching resource is deleted"
      (with-system-data [{:blaze.db/keys [node]} config]
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
      (let [hash (hash/generate patient-0)]
        (with-system-data [{:blaze.db/keys [node]} config]
          [[[:put patient-0]]
           [[:delete "Patient" "0"]]]

          (given (verify/verify-tx-cmds
                   (d/db node) 3
                   [{:op "put" :type "Patient" :id "0" :hash hash}])

            count := 5

            [0 0] := :resource-as-of-index
            [0 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 3}
            [0 2 rts-tu/decode-val] := {:hash hash :num-changes 3 :op :put}

            [1 0] := :type-as-of-index
            [1 1 tao-tu/decode-key] := {:type "Patient" :t 3 :id "0"}
            [1 2 rts-tu/decode-val] := {:hash hash :num-changes 3 :op :put}

            [2 0] := :system-as-of-index
            [2 1 sao-tu/decode-key] := {:t 3 :type "Patient" :id "0"}
            [2 2 rts-tu/decode-val] := {:hash hash :num-changes 3 :op :put}

            [3 0] := :type-stats-index
            [3 1 ts-tu/decode-key] := {:type "Patient" :t 3}
            [3 2 ts-tu/decode-val] := {:total 1 :num-changes 3}

            [4 0] := :system-stats-index
            [4 1 ss-tu/decode-key] := {:t 3}
            [4 2 ss-tu/decode-val] := {:total 1 :num-changes 3}))))))
