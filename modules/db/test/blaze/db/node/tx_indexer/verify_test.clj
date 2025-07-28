(ns blaze.db.node.tx-indexer.verify-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.byte-string :as bs]
   [blaze.db.api :as d]
   [blaze.db.impl.index.patient-last-change-test-util :as plc-tu]
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
   [blaze.db.test-util :refer [config with-system-data]]
   [blaze.db.tx-cache]
   [blaze.db.tx-log.local]
   [blaze.db.tx-log.spec]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.fhir.spec.type]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as sg]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [clojure.test.check.properties :as prop]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)
(tu/set-default-locale-english!)                            ; important for the thousands separator in 10,000

(test/use-fixtures :each tu/fixture)

(def patient-0 {:fhir/type :fhir/Patient :id "0"})
(def patient-0-v2 {:fhir/type :fhir/Patient :id "0" :gender #fhir/code"male"})
(def patient-1 {:fhir/type :fhir/Patient :id "1"})
(def observation-0 {:fhir/type :fhir/Observation :id "0"
                    :subject #fhir/Reference{:reference "Patient/0"}})
(def observation-1 {:fhir/type :fhir/Observation :id "1"
                    :subject #fhir/Reference{:reference "Patient/0"}})
(def allergy-intolerance-0 {:fhir/type :fhir/AllergyIntolerance :id "0"
                            :patient #fhir/Reference{:reference "Patient/0"}})

(defn- verify-tx-cmds [{:keys [read-only-matcher] :as node} t tx-cmds]
  (with-open [db (d/new-batch-db (d/db node))]
    (verify/verify-tx-cmds
     {:db-before db :read-only-matcher read-only-matcher}
     t tx-cmds)))

(deftest verify-tx-cmds-test
  (testing "two commands with the same identity aren't allowed"
    (let [hash (hash/generate patient-0)
          op-gen (sg/such-that (complement #{"conditional-delete" "patient-purge"})
                               (s/gen :blaze.db.tx-cmd/op))
          cmd (fn [op] {:op op :type "Patient" :id "0" :hash hash})]
      (with-system [{:blaze.db/keys [node]} config]

        (satisfies-prop 100
          (prop/for-all [op-1 op-gen
                         op-2 op-gen]
            (ba/conflict?
             (verify-tx-cmds node 1 [(cmd op-1) (cmd op-2)])))))))

  (testing "adding one Patient to an empty store"
    (let [hash (hash/generate patient-0)]
      (doseq [op [:create :put]
              if-none-match [nil "*"]]
        (with-system [{:blaze.db/keys [node]} config]
          (given (verify-tx-cmds
                  node 1
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

  (testing "adding a second version of a Patient to a store containing it already"
    (let [hash (hash/generate patient-0-v2)]
      (doseq [if-match [nil 1 [1] [1 2]]]
        (with-system-data [{:blaze.db/keys [node]} config]
          [[[:put patient-0]]]

          (given (verify-tx-cmds
                  node 2
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

          (given (verify-tx-cmds
                  node 3
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

      (is (empty? (verify-tx-cmds
                   node 2
                   [{:op "put" :type "Patient" :id "0"
                     :hash (hash/generate patient-0)}])))))

  (testing "keeping a non-existing Patient fails"
    (with-system [{:blaze.db/keys [node]} config]

      (let [tx-cmd {:op "keep" :type "Patient" :id "0" :hash (hash/generate patient-0)}]
        (given (verify-tx-cmds node 1 [tx-cmd])
          ::anom/category := ::anom/conflict
          ::anom/message := "Keep failed on `Patient/0`."
          :blaze.db/tx-cmd := tx-cmd))))

  (testing "keeping a non-matching Patient fails"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put patient-0]]
       [[:put patient-0-v2]]]

      (let [tx-cmd {:op "keep" :type "Patient" :id "0" :hash (hash/generate patient-0)}]
        (given (verify-tx-cmds node 1 [tx-cmd])
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
            (given (verify-tx-cmds node 1 [tx-cmd])
              ::anom/category := ::anom/conflict
              ::anom/message := "Precondition `W/\"3\"` failed on `Patient/0`."
              :http/status := 412
              :blaze.db/tx-cmd := tx-cmd))))))

  (testing "keeping a non-matching hash and non-matching if-match Patient fails"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put patient-0]]
       [[:put patient-0-v2]]]

      (testing "with a precondition failure"
        (doseq [if-match [3 [3]]]
          (let [tx-cmd {:op "keep" :type "Patient" :id "0"
                        :hash (hash/generate patient-0)
                        :if-match if-match}]
            (given (verify-tx-cmds node 1 [tx-cmd])
              ::anom/category := ::anom/conflict
              ::anom/message := "Precondition `W/\"3\"` failed on `Patient/0`."
              :http/status := 412
              :blaze.db/tx-cmd := tx-cmd))))))

  (testing "keeping a matching Patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put patient-0]]]

      (testing "with different if-matches"
        (doseq [if-match [nil 1 [1] [1 2]]]
          (is (empty? (verify-tx-cmds
                       node 1
                       [(cond->
                         {:op "keep" :type "Patient" :id "0"
                          :hash (hash/generate patient-0)}
                          if-match
                          (assoc :if-match if-match))])))))))

  (testing "on recreation"
    (let [hash (hash/generate patient-0)]
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put patient-0]]
         [[:delete "Patient" "0"]]]

        (given (verify-tx-cmds node 3 [{:op "put" :type "Patient" :id "0" :hash hash}])
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
          [4 2 ss-tu/decode-val] := {:total 1 :num-changes 3}))))

  (testing "deleting a Patient from an empty store"
    (with-system [{:blaze.db/keys [node]} config]
      (given (verify-tx-cmds node 1 [{:op "delete" :type "Patient" :id "0"}])

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

      (is (empty? (verify-tx-cmds node 2 [{:op "delete" :type "Patient" :id "0"}])))))

  (testing "deleting an existing Patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put patient-0]]]

      (given (verify-tx-cmds node 2 [{:op "delete" :type "Patient" :id "0"}])

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

  (testing "deleting an existing Observation"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put patient-0]
        [:put observation-0]]]

      (given (verify-tx-cmds node 2 [{:op "delete" :type "Observation" :id "0"}])

        count := 6

        [0 0] := :resource-as-of-index
        [0 1 rao-tu/decode-key] := {:type "Observation" :id "0" :t 2}
        [0 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete}

        [1 0] := :type-as-of-index
        [1 1 tao-tu/decode-key] := {:type "Observation" :t 2 :id "0"}
        [1 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete}

        [2 0] := :system-as-of-index
        [2 1 sao-tu/decode-key] := {:t 2 :type "Observation" :id "0"}
        [2 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete}

        [3 0] := :patient-last-change-index
        [3 1 plc-tu/decode-key] := {:patient-id "0" :t 2}
        [3 2 bs/from-byte-array] := bs/empty

        [4 0] := :type-stats-index
        [4 1 ts-tu/decode-key] := {:type "Observation" :t 2}
        [4 2 ts-tu/decode-val] := {:total 0 :num-changes 2}

        [5 0] := :system-stats-index
        [5 1 ss-tu/decode-key] := {:t 2}
        [5 2 ss-tu/decode-val] := {:total 1 :num-changes 3})))

  (testing "deleting an existing AllergyIntolerance"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put patient-0]
        [:put allergy-intolerance-0]]]

      (given (verify-tx-cmds node 2 [{:op "delete" :type "AllergyIntolerance" :id "0"}])

        count := 6

        [0 0] := :resource-as-of-index
        [0 1 rao-tu/decode-key] := {:type "AllergyIntolerance" :id "0" :t 2}
        [0 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete}

        [1 0] := :type-as-of-index
        [1 1 tao-tu/decode-key] := {:type "AllergyIntolerance" :t 2 :id "0"}
        [1 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete}

        [2 0] := :system-as-of-index
        [2 1 sao-tu/decode-key] := {:t 2 :type "AllergyIntolerance" :id "0"}
        [2 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete}

        [3 0] := :patient-last-change-index
        [3 1 plc-tu/decode-key] := {:patient-id "0" :t 2}
        [3 2 bs/from-byte-array] := bs/empty

        [4 0] := :type-stats-index
        [4 1 ts-tu/decode-key] := {:type "AllergyIntolerance" :t 2}
        [4 2 ts-tu/decode-val] := {:total 0 :num-changes 2}

        [5 0] := :system-stats-index
        [5 1 ss-tu/decode-key] := {:t 2}
        [5 2 ss-tu/decode-val] := {:total 1 :num-changes 3})))

  (testing "adding a second Patient to a store containing already one"
    (let [hash (hash/generate patient-1)]
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put patient-0]]]

        (given (verify-tx-cmds node 2 [{:op "put" :type "Patient" :id "1" :hash hash}])

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

  (testing "adding an observation referring to an existing patient"
    (let [hash (hash/generate observation-0)]
      (doseq [op [:create :put]
              if-none-match [nil "*"]]
        (with-system-data [{:blaze.db/keys [node]} config]
          [[[:put patient-0]]]

          (given (verify-tx-cmds
                  node 2
                  [(cond-> {:op (name op) :type "Observation" :id "0"
                            :hash hash :refs [["Patient" "0"]]}
                     if-none-match
                     (assoc :if-none-match if-none-match))])

            count := 6

            [0 0] := :resource-as-of-index
            [0 1 rao-tu/decode-key] := {:type "Observation" :id "0" :t 2}
            [0 2 rts-tu/decode-val] := {:hash hash :num-changes 1 :op op}

            [1 0] := :type-as-of-index
            [1 1 tao-tu/decode-key] := {:type "Observation" :t 2 :id "0"}
            [1 2 rts-tu/decode-val] := {:hash hash :num-changes 1 :op op}

            [2 0] := :system-as-of-index
            [2 1 sao-tu/decode-key] := {:t 2 :type "Observation" :id "0"}
            [2 2 rts-tu/decode-val] := {:hash hash :num-changes 1 :op op}

            [3 0] := :patient-last-change-index
            [3 1 plc-tu/decode-key] := {:patient-id "0" :t 2}
            [3 2 bs/from-byte-array] := bs/empty

            [4 0] := :type-stats-index
            [4 1 ts-tu/decode-key] := {:type "Observation" :t 2}
            [4 2 ts-tu/decode-val] := {:total 1 :num-changes 1}

            [5 0] := :system-stats-index
            [5 1 ss-tu/decode-key] := {:t 2}
            [5 2 ss-tu/decode-val] := {:total 2 :num-changes 2})))))

  (testing "update conflict"
    (testing "adding an observation referring to an empty store"
      (let [hash (hash/generate observation-0)]
        (with-system [{:blaze.db/keys [node]} config]

          (given (verify-tx-cmds
                  node 2
                  [{:op "put" :type "Observation" :id "0" :hash hash
                    :refs [["Patient" "0"]]}])

            ::anom/category := ::anom/conflict
            ::anom/message := "Referential integrity violated. Resource `Patient/0` doesn't exist."))))

    (testing "using non-matching if-match"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put patient-0]]]

        (given (verify-tx-cmds
                node 2
                [{:op "put" :type "Patient" :id "0"
                  :hash (hash/generate patient-0)
                  :if-match 0}])
          ::anom/category := ::anom/conflict
          ::anom/message := "Precondition `W/\"0\"` failed on `Patient/0`."
          :http/status := 412)))

    (testing "using if-none-match of `*`"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put patient-0]]]

        (given (verify-tx-cmds
                node 2
                [{:op "put" :type "Patient" :id "0"
                  :hash (hash/generate patient-0)
                  :if-none-match "*"}])
          ::anom/category := ::anom/conflict
          ::anom/message := "Resource `Patient/0` already exists."
          :http/status := 412)))

    (testing "using matching if-none-match"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put patient-0]]]

        (given (verify-tx-cmds
                node 2
                [{:op "put" :type "Patient" :id "0"
                  :hash (hash/generate patient-0)
                  :if-none-match 1}])
          ::anom/category := ::anom/conflict
          ::anom/message := "Resource `Patient/0` with version 1 already exists."
          :http/status := 412))))

  (testing "delete conflict"
    (testing "deleting a patient and creating a resource referencing it in the same transaction"
      (let [hash (hash/generate observation-0)]
        (with-system-data [{:blaze.db/keys [node]} config]
          [[[:put patient-0]]]

          (given (verify-tx-cmds
                  node 2
                  [{:op "delete" :type "Patient" :id "0" :check-refs true}
                   {:op "put" :type "Observation" :id "0" :hash hash :refs [["Patient" "0"]]}])

            ::anom/category := ::anom/conflict
            ::anom/message := "Referential integrity violated. Resource `Patient/0` should be deleted but is referenced from `Observation/0`."))))

    (testing "deleting a patient and an observation referencing it in the same transaction"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put patient-0]
          [:put observation-0]]]

        (given (verify-tx-cmds
                node 2
                [{:op "delete" :type "Patient" :id "0" :check-refs true}
                 {:op "delete" :type "Observation" :id "0" :check-refs true}])

          count := 10

          [0 0] := :resource-as-of-index
          [0 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 2}
          [0 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete}

          [1 0] := :type-as-of-index
          [1 1 tao-tu/decode-key] := {:type "Patient" :t 2 :id "0"}
          [1 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete}

          [2 0] := :system-as-of-index
          [2 1 sao-tu/decode-key] := {:t 2 :type "Patient" :id "0"}
          [2 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete}

          [3 0] := :resource-as-of-index
          [3 1 rao-tu/decode-key] := {:type "Observation" :id "0" :t 2}
          [3 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete}

          [4 0] := :type-as-of-index
          [4 1 tao-tu/decode-key] := {:type "Observation" :t 2 :id "0"}
          [4 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete}

          [5 0] := :system-as-of-index
          [5 1 sao-tu/decode-key] := {:t 2 :type "Observation" :id "0"}
          [5 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete}

          [6 0] := :patient-last-change-index
          [6 1 plc-tu/decode-key] := {:patient-id "0" :t 2}
          [6 2 bs/from-byte-array] := bs/empty

          [7 0] := :type-stats-index
          [7 1 ts-tu/decode-key] := {:type "Patient" :t 2}
          [7 2 ts-tu/decode-val] := {:total 0 :num-changes 2}

          [8 0] := :type-stats-index
          [8 1 ts-tu/decode-key] := {:type "Observation" :t 2}
          [8 2 ts-tu/decode-val] := {:total 0 :num-changes 2}

          [9 0] := :system-stats-index
          [9 1 ss-tu/decode-key] := {:t 2}
          [9 2 ss-tu/decode-val] := {:total 0 :num-changes 4})))

    (testing "deleting a patient which is referenced by a still existing observation"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put patient-0]
          [:put observation-0]]]

        (given (verify-tx-cmds node 2 [{:op "delete" :type "Patient" :id "0" :check-refs true}])
          ::anom/category := ::anom/conflict
          ::anom/message := "Referential integrity violated. Resource `Patient/0` should be deleted but is referenced from `Observation/0`.")))

    (testing "deleting a patient which is referenced by a still existing observation without checking references"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put patient-0]
          [:put observation-0]]]

        (given (verify-tx-cmds node 2 [{:op "delete" :type "Patient" :id "0"}])

          count := 5

          [0 0] := :resource-as-of-index
          [0 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 2}
          [0 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete}

          [1 0] := :type-as-of-index
          [1 1 tao-tu/decode-key] := {:type "Patient" :t 2 :id "0"}
          [1 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete}

          [2 0] := :system-as-of-index
          [2 1 sao-tu/decode-key] := {:type "Patient" :id "0" :t 2}
          [2 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete}

          [3 0] := :type-stats-index
          [3 1 ts-tu/decode-key] := {:type "Patient", :t 2}
          [3 2 ts-tu/decode-val] := {:total 0, :num-changes 2}

          [4 0] := :system-stats-index
          [4 1 ss-tu/decode-key] := {:t 2}
          [4 2 ss-tu/decode-val] := {:total 1 :num-changes 3}))))

  (testing "deleting a patient which is referenced by two still existing observations"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put patient-0]
        [:put observation-0]
        [:put observation-1]]]

      (given (verify-tx-cmds node 2 [{:op "delete" :type "Patient" :id "0" :check-refs true}])
        ::anom/category := ::anom/conflict
        ::anom/message := "Referential integrity violated. Resource `Patient/0` should be deleted but is referenced from `Observation/0` and others."))))

(deftest verify-delete-history-test
  (testing "empty database"
    (with-system [{:blaze.db/keys [node]} config]

      (is (empty? (verify-tx-cmds node 1 [{:op "delete-history" :type "Patient" :id "0"}])))))

  (testing "one patient"
    (testing "with one version"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:create {:fhir/type :fhir/Patient :id "0"}]]]

        (testing "nothing to do because there is only the current version"
          (is (empty? (verify-tx-cmds node 2 [{:op "delete-history" :type "Patient" :id "0"}]))))))

    (testing "with two versions"
      (let [patient-v1 {:fhir/type :fhir/Patient :id "0" :active false}
            patient-v2 {:fhir/type :fhir/Patient :id "0" :active true}
            hash-v1 (hash/generate patient-v1)]
        (with-system-data [{:blaze.db/keys [node]} config]
          [[[:create patient-v1]]
           [[:put patient-v2]]]

          (given (verify-tx-cmds node 3 [{:op "delete-history" :type "Patient" :id "0"}])
            count := 5

            [0 0] := :resource-as-of-index
            [0 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 1}
            [0 2 rts-tu/decode-val] := {:hash hash-v1 :num-changes 1 :op :create :purged-at 3}

            [1 0] := :type-as-of-index
            [1 1 tao-tu/decode-key] := {:type "Patient" :t 1 :id "0"}
            [1 2 rts-tu/decode-val] := {:hash hash-v1 :num-changes 1 :op :create :purged-at 3}

            [2 0] := :system-as-of-index
            [2 1 sao-tu/decode-key] := {:t 1 :type "Patient" :id "0"}
            [2 2 rts-tu/decode-val] := {:hash hash-v1 :num-changes 1 :op :create :purged-at 3}

            [3 0] := :type-stats-index
            [3 1 ts-tu/decode-key] := {:type "Patient" :t 3}
            [3 2 ts-tu/decode-val] := {:total 1 :num-changes 1}

            [4 0] := :system-stats-index
            [4 1 ss-tu/decode-key] := {:t 3}
            [4 2 ss-tu/decode-val] := {:total 1 :num-changes 1}))))

    (testing "with three versions"
      (let [patient-v1 {:fhir/type :fhir/Patient :id "0" :active false}
            patient-v2 {:fhir/type :fhir/Patient :id "0" :active true}
            patient-v3 {:fhir/type :fhir/Patient :id "0" :active false}
            hash-v1 (hash/generate patient-v1)
            hash-v2 (hash/generate patient-v2)]
        (with-system-data [{:blaze.db/keys [node]} config]
          [[[:create patient-v1]]
           [[:put patient-v2]]
           [[:put patient-v3]]]

          (given (verify-tx-cmds node 4 [{:op "delete-history" :type "Patient" :id "0"}])
            count := 8

            [0 0] := :resource-as-of-index
            [0 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 2}
            [0 2 rts-tu/decode-val] := {:hash hash-v2 :num-changes 2 :op :put :purged-at 4}

            [1 0] := :type-as-of-index
            [1 1 tao-tu/decode-key] := {:type "Patient" :t 2 :id "0"}
            [1 2 rts-tu/decode-val] := {:hash hash-v2 :num-changes 2 :op :put :purged-at 4}

            [2 0] := :system-as-of-index
            [2 1 sao-tu/decode-key] := {:t 2 :type "Patient" :id "0"}
            [2 2 rts-tu/decode-val] := {:hash hash-v2 :num-changes 2 :op :put :purged-at 4}

            [3 0] := :resource-as-of-index
            [3 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 1}
            [3 2 rts-tu/decode-val] := {:hash hash-v1 :num-changes 1 :op :create :purged-at 4}

            [4 0] := :type-as-of-index
            [4 1 tao-tu/decode-key] := {:type "Patient" :t 1 :id "0"}
            [4 2 rts-tu/decode-val] := {:hash hash-v1 :num-changes 1 :op :create :purged-at 4}

            [5 0] := :system-as-of-index
            [5 1 sao-tu/decode-key] := {:t 1 :type "Patient" :id "0"}
            [5 2 rts-tu/decode-val] := {:hash hash-v1 :num-changes 1 :op :create :purged-at 4}

            [6 0] := :type-stats-index
            [6 1 ts-tu/decode-key] := {:type "Patient" :t 4}
            [6 2 ts-tu/decode-val] := {:total 1 :num-changes 1}

            [7 0] := :system-stats-index
            [7 1 ss-tu/decode-key] := {:t 4}
            [7 2 ss-tu/decode-val] := {:total 1 :num-changes 1}))))))

(deftest verify-purge-test
  (testing "empty database"
    (with-system [{:blaze.db/keys [node]} config]

      (is (empty? (verify-tx-cmds node 1 [{:op "purge" :type "Patient" :id "0"}])))))

  (testing "one patient"
    (let [hash (hash/generate patient-0)]
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:create patient-0]]]

        (given (verify-tx-cmds node 2 [{:op "purge" :type "Patient" :id "0"}])
          count := 5

          [0 0] := :resource-as-of-index
          [0 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 1}
          [0 2 rts-tu/decode-val] := {:hash hash :num-changes 1 :op :create :purged-at 2}

          [1 0] := :type-as-of-index
          [1 1 tao-tu/decode-key] := {:type "Patient" :t 1 :id "0"}
          [1 2 rts-tu/decode-val] := {:hash hash :num-changes 1 :op :create :purged-at 2}

          [2 0] := :system-as-of-index
          [2 1 sao-tu/decode-key] := {:t 1 :type "Patient" :id "0"}
          [2 2 rts-tu/decode-val] := {:hash hash :num-changes 1 :op :create :purged-at 2}

          [3 0] := :type-stats-index
          [3 1 ts-tu/decode-key] := {:type "Patient" :t 2}
          [3 2 ts-tu/decode-val] := {:total 0 :num-changes 0}

          [4 0] := :system-stats-index
          [4 1 ss-tu/decode-key] := {:t 2}
          [4 2 ss-tu/decode-val] := {:total 0 :num-changes 0}))))

  (testing "one deleted patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:delete "Patient" "0"]]]

      (given (verify-tx-cmds node 2 [{:op "purge" :type "Patient" :id "0"}])
        count := 5

        [0 0] := :resource-as-of-index
        [0 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 1}
        [0 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 1 :op :delete :purged-at 2}

        [1 0] := :type-as-of-index
        [1 1 tao-tu/decode-key] := {:type "Patient" :t 1 :id "0"}
        [1 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 1 :op :delete :purged-at 2}

        [2 0] := :system-as-of-index
        [2 1 sao-tu/decode-key] := {:t 1 :type "Patient" :id "0"}
        [2 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 1 :op :delete :purged-at 2}

        [3 0] := :type-stats-index
        [3 1 ts-tu/decode-key] := {:type "Patient" :t 2}
        [3 2 ts-tu/decode-val] := {:total 0 :num-changes 0}

        [4 0] := :system-stats-index
        [4 1 ss-tu/decode-key] := {:t 2}
        [4 2 ss-tu/decode-val] := {:total 0 :num-changes 0})))

  (testing "purging a patient which is referenced by a still existing observation"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put patient-0]
        [:put observation-0]]]

      (given (verify-tx-cmds node 2 [{:op "purge" :type "Patient" :id "0" :check-refs true}])
        ::anom/category := ::anom/conflict
        ::anom/message := "Referential integrity violated. Resource `Patient/0` should be deleted but is referenced from `Observation/0`.")))

  (testing "purging a patient and creating a resource referencing it in the same transaction"
    (let [hash (hash/generate observation-0)]
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put patient-0]]]

        (given (verify-tx-cmds
                node 2
                [{:op "purge" :type "Patient" :id "0" :check-refs true}
                 {:op "put" :type "Observation" :id "0" :hash hash :refs [["Patient" "0"]]}])

          ::anom/category := ::anom/conflict
          ::anom/message := "Referential integrity violated. Resource `Patient/0` should be deleted but is referenced from `Observation/0`.")))))
