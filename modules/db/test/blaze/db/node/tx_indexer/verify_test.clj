(ns blaze.db.node.tx-indexer.verify-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.byte-string :as bs]
   [blaze.cache-collector.protocols :as ccp]
   [blaze.db.api :as d]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.patient-last-change-test-util :as plc-tu]
   [blaze.db.impl.index.resource-as-of-test-util :as rao-tu]
   [blaze.db.impl.index.rts-as-of-test-util :as rts-tu]
   [blaze.db.impl.index.search-param-value-resource :as sp-vr]
   [blaze.db.impl.index.system-as-of-test-util :as sao-tu]
   [blaze.db.impl.index.system-stats-test-util :as ss-tu]
   [blaze.db.impl.index.type-as-of-test-util :as tao-tu]
   [blaze.db.impl.index.type-stats-test-util :as ts-tu]
   [blaze.db.kv.mem]
   [blaze.db.kv.mem-spec]
   [blaze.db.node]
   [blaze.db.node.tx-indexer.util :as tx-u]
   [blaze.db.node.tx-indexer.verify :as verify]
   [blaze.db.node.tx-indexer.verify-spec]
   [blaze.db.node.tx-indexer.verify.impl :as impl]
   [blaze.db.permissions :as p]
   [blaze.db.test-util :refer [concurrent-calls? config internal-label
                               internal-patient-0 read-only-patient-0
                               resource-handle-cache-config transact-internal
                               with-system-data]]
   [blaze.db.tx-cache]
   [blaze.db.tx-log.local]
   [blaze.db.tx-log.spec]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [com.github.benmanes.caffeine.cache.stats CacheStats]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)
(tu/set-default-locale-english!)                            ; important for the thousands separator in 10,000

(test/use-fixtures :each tu/fixture)

(def patient-0 {:fhir/type :fhir/Patient :id "0"})
(def patient-0-v2 {:fhir/type :fhir/Patient :id "0" :gender #fhir/code "male"})
(def patient-1 {:fhir/type :fhir/Patient :id "1"})
(def observation-0 {:fhir/type :fhir/Observation :id "0"
                    :subject #fhir/Reference{:reference #fhir/string "Patient/0"}})
(def observation-1 {:fhir/type :fhir/Observation :id "1"
                    :subject #fhir/Reference{:reference #fhir/string "Patient/0"}})
(def allergy-intolerance-0 {:fhir/type :fhir/AllergyIntolerance :id "0"
                            :patient #fhir/Reference{:reference #fhir/string "Patient/0"}})

(defn- context
  "Returns the verification context of `node`, using the stats the node itself
  keeps, so that the entries are the ones the indexing loop of the node would
  write."
  [{:keys [read-only-matcher internal-matcher stats tx-indexer-pool]} db]
  {:db-before db :read-only-matcher read-only-matcher
   :internal-matcher internal-matcher :stats @stats :pool tx-indexer-pool})

(defn- flatten-parts
  "Flattens the `:entries` and the `:resource-handle-updates` of `res`, which
  are in parts, so that tests can address them by position in the
  transaction."
  [res]
  (cond-> res
    (not (ba/anomaly? res))
    (-> (update :entries #(into [] cat %))
        (update :resource-handle-updates #(into [] cat %)))))

(defn- verify-tx-cmds**
  [node t tx-cmds f]
  (flatten-parts
   (ba/try-anomaly
    (ac/join
     (d/with-db node
       (fn [db]
         (ac/completed-future
          (verify/verify-tx-cmds (f (context node db)) t tx-cmds))))))))

(defn- verify-tx-cmds*
  "Verifies `tx-cmds` against `node` without permissions."
  [node t tx-cmds]
  (verify-tx-cmds** node t tx-cmds identity))

(defn- verify-tx-cmds [node t tx-cmds]
  (let [res (verify-tx-cmds* node t tx-cmds)]
    (if (ba/anomaly? res) res (:entries res))))

(defn- verify-tx-cmds-with
  "Verifies `tx-cmds` against `node` with `permissions`."
  [node t tx-cmds permissions]
  (let [res (verify-tx-cmds** node t tx-cmds
                              #(cond-> % permissions (assoc :permissions permissions)))]
    (if (ba/anomaly? res) res (:entries res))))

(defn- patient-stats
  "Returns the stats of the Patient type of the verification result `res`.

  A function, because the tid isn't a path element `given` understands."
  [res]
  (get-in res [:stats :types (codec/tid "Patient")]))

(defmulti verify-cmd identity)

(defmethod verify-cmd "hold" [op]
  {:op op :type "Patient" :id "0" :hash (hash/generate patient-0)
   :if-none-exist [["foo" "bar"]]})

(defmethod verify-cmd "delete-history-version" [op]
  {:op op :type "Patient" :id "0" :t 1})

(defmethod verify-cmd :default [op]
  {:op op :type "Patient" :id "0" :hash (hash/generate patient-0)})

(deftest verify-tx-cmds-stats-test
  (testing "the stats returned are the ones of the database after the
            transaction"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put patient-0]]]

      (given (verify-tx-cmds* node 2 [{:op "put" :type "Patient" :id "1"
                                       :hash (hash/generate patient-1)}])
        patient-stats := {:total 2 :num-changes 2}
        [:stats :system] := {:total 2 :num-changes 2})))

  (testing "a transaction that changes nothing leaves the stats untouched"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put patient-0]]]

      (given (verify-tx-cmds* node 2 [{:op "keep" :type "Patient" :id "0"
                                       :hash (hash/generate patient-0)}])
        :entries := []
        patient-stats := {:total 1 :num-changes 1}
        [:stats :system] := {:total 1 :num-changes 1}))))

(deftest verify-tx-cmds-test
  (testing "two commands with the same identity aren't allowed"
    (with-system [{:blaze.db/keys [node]} config]

      (doseq [op-1 (s/form ::verify/op)
              op-2 (s/form ::verify/op)]
        (is (ba/conflict?
             (verify-tx-cmds node 1 [(verify-cmd op-1) (verify-cmd op-2)]))))))

  (testing "adding one Patient to an empty store"
    (let [hash (hash/generate patient-0)]
      (doseq [op [:create :put]
              if-none-match [nil "*"]]
        (with-system [{:blaze.db/keys [node]} config]
          (given (verify-tx-cmds*
                  node 1
                  [(cond-> {:op (name op) :type "Patient" :id "0" :hash hash}
                     if-none-match
                     (assoc :if-none-match if-none-match))])

            [:entries count] := 5

            [:entries 0 0] := :resource-as-of-index
            [:entries 0 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 1}
            [:entries 0 2 rts-tu/decode-val] := {:hash hash :num-changes 1 :op op}

            [:entries 1 0] := :type-as-of-index
            [:entries 1 1 tao-tu/decode-key] := {:type "Patient" :t 1 :id "0"}
            [:entries 1 2 rts-tu/decode-val] := {:hash hash :num-changes 1 :op op}

            [:entries 2 0] := :system-as-of-index
            [:entries 2 1 sao-tu/decode-key] := {:t 1 :type "Patient" :id "0"}
            [:entries 2 2 rts-tu/decode-val] := {:hash hash :num-changes 1 :op op}

            [:entries 3 0] := :type-stats-index
            [:entries 3 1 ts-tu/decode-key] := {:type "Patient" :t 1}
            [:entries 3 2 ts-tu/decode-val] := {:total 1 :num-changes 1}

            [:entries 4 0] := :system-stats-index
            [:entries 4 1 ss-tu/decode-key] := {:t 1}
            [:entries 4 2 ss-tu/decode-val] := {:total 1 :num-changes 1}

            [:resource-handle-updates count] := 1
            [:resource-handle-updates 0 0 :fhir/type] := :fhir/Patient
            [:resource-handle-updates 0 0 :id] := "0"
            [:resource-handle-updates 0 0 :t] := 1
            [:resource-handle-updates 0 0 :hash] := hash
            [:resource-handle-updates 0 0 :num-changes] := 1
            [:resource-handle-updates 0 0 :op] := op
            [:resource-handle-updates 0 1] := false)))))

  (testing "adding a second version of a Patient to a store containing it already"
    (let [hash (hash/generate patient-0-v2)]
      (doseq [if-match [nil 1 [1] [1 2]]]
        (with-system-data [{:blaze.db/keys [node]} config]
          [[[:put patient-0]]]

          (given (verify-tx-cmds*
                  node 2
                  [(cond-> {:op "put" :type "Patient" :id "0" :hash hash}
                     if-match
                     (assoc :if-match if-match))])

            [:entries count] := 5

            [:entries 0 0] := :resource-as-of-index
            [:entries 0 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 2}
            [:entries 0 2 rts-tu/decode-val] := {:hash hash :num-changes 2 :op :put}

            [:entries 1 0] := :type-as-of-index
            [:entries 1 1 tao-tu/decode-key] := {:type "Patient" :t 2 :id "0"}
            [:entries 1 2 rts-tu/decode-val] := {:hash hash :num-changes 2 :op :put}

            [:entries 2 0] := :system-as-of-index
            [:entries 2 1 sao-tu/decode-key] := {:t 2 :type "Patient" :id "0"}
            [:entries 2 2 rts-tu/decode-val] := {:hash hash :num-changes 2 :op :put}

            [:entries 3 0] := :type-stats-index
            [:entries 3 1 ts-tu/decode-key] := {:type "Patient" :t 2}
            [:entries 3 2 ts-tu/decode-val] := {:total 1 :num-changes 2}

            [:entries 4 0] := :system-stats-index
            [:entries 4 1 ss-tu/decode-key] := {:t 2}
            [:entries 4 2 ss-tu/decode-val] := {:total 1 :num-changes 2}

            [:resource-handle-updates count] := 1
            [:resource-handle-updates 0 0 :t] := 2
            [:resource-handle-updates 0 0 :hash] := hash
            [:resource-handle-updates 0 0 :num-changes] := 2
            [:resource-handle-updates 0 0 :op] := :put
            [:resource-handle-updates 0 1] := false)))))

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

      (given (verify-tx-cmds*
              node 2
              [{:op "put" :type "Patient" :id "0"
                :hash (hash/generate patient-0)}])
        :entries := []
        :resource-handle-updates := [])))

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

      (given (verify-tx-cmds* node 2 [{:op "delete" :type "Patient" :id "0"}])

        [:entries count] := 5

        [:entries 0 0] := :resource-as-of-index
        [:entries 0 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 2}
        [:entries 0 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete}

        [:entries 1 0] := :type-as-of-index
        [:entries 1 1 tao-tu/decode-key] := {:type "Patient" :t 2 :id "0"}
        [:entries 1 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete}

        [:entries 2 0] := :system-as-of-index
        [:entries 2 1 sao-tu/decode-key] := {:t 2 :type "Patient" :id "0"}
        [:entries 2 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 2 :op :delete}

        [:entries 3 0] := :type-stats-index
        [:entries 3 1 ts-tu/decode-key] := {:type "Patient" :t 2}
        [:entries 3 2 ts-tu/decode-val] := {:total 0 :num-changes 2}

        [:entries 4 0] := :system-stats-index
        [:entries 4 1 ss-tu/decode-key] := {:t 2}
        [:entries 4 2 ss-tu/decode-val] := {:total 0 :num-changes 2}

        [:resource-handle-updates count] := 1
        [:resource-handle-updates 0 0 :t] := 2
        [:resource-handle-updates 0 0 :hash] := hash/deleted-hash
        [:resource-handle-updates 0 0 :num-changes] := 2
        [:resource-handle-updates 0 0 :op] := :delete
        [:resource-handle-updates 0 1] := false)))

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

    (testing "deleting a patient and one of two observations referencing it in the same transaction"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put patient-0]
          [:put observation-0]
          [:put observation-1]]]

        (given (verify-tx-cmds
                node 2
                [{:op "delete" :type "Patient" :id "0" :check-refs true}
                 {:op "delete" :type "Observation" :id "0" :check-refs true}])
          ::anom/category := ::anom/conflict
          ::anom/message := "Referential integrity violated. Resource `Patient/0` should be deleted but is referenced from `Observation/1`.")))

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
        ::anom/message := "Referential integrity violated. Resource `Patient/0` should be deleted but is referenced from `Observation/0`, `Observation/1`.")))

  (testing "deleting a patient which is referenced by eleven still existing observations"
    (with-system-data [{:blaze.db/keys [node]} config]
      [(into [[:put patient-0]]
             (map (fn [id] [:put (assoc observation-0 :id (str id))]))
             (range 11))]

      (given (verify-tx-cmds node 2 [{:op "delete" :type "Patient" :id "0" :check-refs true}])
        ::anom/category := ::anom/conflict
        ::anom/message := "Referential integrity violated. Resource `Patient/0` should be deleted but is referenced from `Observation/0`, `Observation/1`, `Observation/10`, `Observation/2`, `Observation/3`, `Observation/4`, `Observation/5`, `Observation/6`, `Observation/7`, `Observation/8` and others."))))

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
      (let [patient-v1 {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean false}
            patient-v2 {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean true}
            hash-v1 (hash/generate patient-v1)]
        (with-system-data [{:blaze.db/keys [node]} config]
          [[[:create patient-v1]]
           [[:put patient-v2]]]

          (given (verify-tx-cmds* node 3 [{:op "delete-history" :type "Patient" :id "0"}])
            [:entries count] := 5

            [:entries 0 0] := :resource-as-of-index
            [:entries 0 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 1}
            [:entries 0 2 rts-tu/decode-val] := {:hash hash-v1 :num-changes 1 :op :create :purged-at 3}

            [:entries 1 0] := :type-as-of-index
            [:entries 1 1 tao-tu/decode-key] := {:type "Patient" :t 1 :id "0"}
            [:entries 1 2 rts-tu/decode-val] := {:hash hash-v1 :num-changes 1 :op :create :purged-at 3}

            [:entries 2 0] := :system-as-of-index
            [:entries 2 1 sao-tu/decode-key] := {:t 1 :type "Patient" :id "0"}
            [:entries 2 2 rts-tu/decode-val] := {:hash hash-v1 :num-changes 1 :op :create :purged-at 3}

            [:entries 3 0] := :type-stats-index
            [:entries 3 1 ts-tu/decode-key] := {:type "Patient" :t 3}
            [:entries 3 2 ts-tu/decode-val] := {:total 1 :num-changes 1}

            [:entries 4 0] := :system-stats-index
            [:entries 4 1 ss-tu/decode-key] := {:t 3}
            [:entries 4 2 ss-tu/decode-val] := {:total 1 :num-changes 1}

            ;; the current version stays, so there is nothing to update
            :resource-handle-updates := []))))

    (testing "with three versions"
      (let [patient-v1 {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean false}
            patient-v2 {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean true}
            patient-v3 {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean false}
            hash-v1 (hash/generate patient-v1)
            hash-v2 (hash/generate patient-v2)]
        (with-system-data [{:blaze.db/keys [node]} config]
          [[[:create patient-v1]]
           [[:put patient-v2]]
           [[:put patient-v3]]]

          (given (verify-tx-cmds* node 4 [{:op "delete-history" :type "Patient" :id "0"}])
            [:entries count] := 8

            [:entries 0 0] := :resource-as-of-index
            [:entries 0 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 2}
            [:entries 0 2 rts-tu/decode-val] := {:hash hash-v2 :num-changes 2 :op :put :purged-at 4}

            [:entries 1 0] := :type-as-of-index
            [:entries 1 1 tao-tu/decode-key] := {:type "Patient" :t 2 :id "0"}
            [:entries 1 2 rts-tu/decode-val] := {:hash hash-v2 :num-changes 2 :op :put :purged-at 4}

            [:entries 2 0] := :system-as-of-index
            [:entries 2 1 sao-tu/decode-key] := {:t 2 :type "Patient" :id "0"}
            [:entries 2 2 rts-tu/decode-val] := {:hash hash-v2 :num-changes 2 :op :put :purged-at 4}

            [:entries 3 0] := :resource-as-of-index
            [:entries 3 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 1}
            [:entries 3 2 rts-tu/decode-val] := {:hash hash-v1 :num-changes 1 :op :create :purged-at 4}

            [:entries 4 0] := :type-as-of-index
            [:entries 4 1 tao-tu/decode-key] := {:type "Patient" :t 1 :id "0"}
            [:entries 4 2 rts-tu/decode-val] := {:hash hash-v1 :num-changes 1 :op :create :purged-at 4}

            [:entries 5 0] := :system-as-of-index
            [:entries 5 1 sao-tu/decode-key] := {:t 1 :type "Patient" :id "0"}
            [:entries 5 2 rts-tu/decode-val] := {:hash hash-v1 :num-changes 1 :op :create :purged-at 4}

            [:entries 6 0] := :type-stats-index
            [:entries 6 1 ts-tu/decode-key] := {:type "Patient" :t 4}
            [:entries 6 2 ts-tu/decode-val] := {:total 1 :num-changes 1}

            [:entries 7 0] := :system-stats-index
            [:entries 7 1 ss-tu/decode-key] := {:t 4}
            [:entries 7 2 ss-tu/decode-val] := {:total 1 :num-changes 1}

            ;; the current version stays, so there is nothing to update
            :resource-handle-updates := []))))))

(deftest verify-delete-history-version-test
  (testing "empty database"
    (with-system [{:blaze.db/keys [node]} config]

      (is (empty? (verify-tx-cmds node 1 [{:op "delete-history-version" :type "Patient" :id "0" :t 1}])))))

  (testing "one patient"
    (testing "with one version"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:create {:fhir/type :fhir/Patient :id "0"}]]]

        (testing "the current version can't be deleted"
          (given (verify-tx-cmds node 2 [{:op "delete-history-version" :type "Patient" :id "0" :t 1}])
            ::anom/category := ::anom/conflict
            ::anom/message := "Can't delete the current version `1` of the resource `Patient/0`."))

        (testing "nothing to do on a version that doesn't exist"
          (is (empty? (verify-tx-cmds node 2 [{:op "delete-history-version" :type "Patient" :id "0" :t 2}]))))))

    (testing "with three versions"
      (let [patient-v1 {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean false}
            patient-v2 {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean true}
            patient-v3 {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean false}
            hash-v1 (hash/generate patient-v1)
            hash-v2 (hash/generate patient-v2)]
        (with-system-data [{:blaze.db/keys [node]} config]
          [[[:create patient-v1]]
           [[:put patient-v2]]
           [[:put patient-v3]]
           [[:create {:fhir/type :fhir/Patient :id "1"}]]]

          (testing "deleting the middle version"
            (given (verify-tx-cmds* node 5 [{:op "delete-history-version" :type "Patient" :id "0" :t 2}])
              [:entries count] := 5

              [:entries 0 0] := :resource-as-of-index
              [:entries 0 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 2}
              [:entries 0 2 rts-tu/decode-val] := {:hash hash-v2 :num-changes 2 :op :put :purged-at 5}

              [:entries 1 0] := :type-as-of-index
              [:entries 1 1 tao-tu/decode-key] := {:type "Patient" :t 2 :id "0"}
              [:entries 1 2 rts-tu/decode-val] := {:hash hash-v2 :num-changes 2 :op :put :purged-at 5}

              [:entries 2 0] := :system-as-of-index
              [:entries 2 1 sao-tu/decode-key] := {:t 2 :type "Patient" :id "0"}
              [:entries 2 2 rts-tu/decode-val] := {:hash hash-v2 :num-changes 2 :op :put :purged-at 5}

              [:entries 3 0] := :type-stats-index
              [:entries 3 1 ts-tu/decode-key] := {:type "Patient" :t 5}
              [:entries 3 2 ts-tu/decode-val] := {:total 2 :num-changes 3}

              [:entries 4 0] := :system-stats-index
              [:entries 4 1 ss-tu/decode-key] := {:t 5}
              [:entries 4 2 ss-tu/decode-val] := {:total 2 :num-changes 3}

              ;; the current version stays, so there is nothing to update
              :resource-handle-updates := []))

          (testing "deleting the first version"
            (given (verify-tx-cmds* node 5 [{:op "delete-history-version" :type "Patient" :id "0" :t 1}])
              [:entries count] := 5

              [:entries 0 0] := :resource-as-of-index
              [:entries 0 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 1}
              [:entries 0 2 rts-tu/decode-val] := {:hash hash-v1 :num-changes 1 :op :create :purged-at 5}))

          (testing "the current version can't be deleted"
            (given (verify-tx-cmds node 5 [{:op "delete-history-version" :type "Patient" :id "0" :t 3}])
              ::anom/category := ::anom/conflict
              ::anom/message := "Can't delete the current version `3` of the resource `Patient/0`."))

          (testing "nothing to do on a t at which only another resource changed"
            (is (empty? (verify-tx-cmds node 5 [{:op "delete-history-version" :type "Patient" :id "0" :t 4}]))))

          (testing "nothing to do on a version in the future"
            (is (empty? (verify-tx-cmds node 5 [{:op "delete-history-version" :type "Patient" :id "0" :t 5}]))))

          (testing "deleting the first and the middle version together"
            (given (verify-tx-cmds* node 5 [{:op "delete-history-version" :type "Patient" :id "0" :t 1}
                                            {:op "delete-history-version" :type "Patient" :id "0" :t 2}])
              [:entries count] := 8

              [:entries 0 0] := :resource-as-of-index
              [:entries 0 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 1}
              [:entries 0 2 rts-tu/decode-val] := {:hash hash-v1 :num-changes 1 :op :create :purged-at 5}

              [:entries 3 0] := :resource-as-of-index
              [:entries 3 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 2}
              [:entries 3 2 rts-tu/decode-val] := {:hash hash-v2 :num-changes 2 :op :put :purged-at 5}

              [:entries 6 0] := :type-stats-index
              [:entries 6 2 ts-tu/decode-val] := {:total 2 :num-changes 2}

              [:entries 7 0] := :system-stats-index
              [:entries 7 2 ss-tu/decode-val] := {:total 2 :num-changes 2}))

          (testing "the same version can't be deleted twice"
            (given (verify-tx-cmds node 5 [{:op "delete-history-version" :type "Patient" :id "0" :t 2}
                                           {:op "delete-history-version" :type "Patient" :id "0" :t 2}])
              ::anom/category := ::anom/conflict
              ::anom/message := "Duplicate transaction commands `delete-history-version Patient/0/_history/2` and `delete-history-version Patient/0/_history/2`."))

          (testing "a version can't be deleted together with other commands on the same resource"
            (let [delete-version {:op "delete-history-version" :type "Patient" :id "0" :t 1}]
              (doseq [[cmd formatted] [[{:op "put" :type "Patient" :id "0" :hash hash-v1} "put Patient/0"]
                                       [{:op "delete" :type "Patient" :id "0"} "delete Patient/0"]
                                       [{:op "delete-history" :type "Patient" :id "0"} "delete-history Patient/0"]]]
                (testing "after the other command"
                  (given (verify-tx-cmds node 5 [cmd delete-version])
                    ::anom/category := ::anom/conflict
                    ::anom/message := (format "Duplicate transaction commands `%s` and `delete-history-version Patient/0/_history/1`." formatted)))

                (testing "before the other command"
                  (given (verify-tx-cmds node 5 [delete-version cmd])
                    ::anom/category := ::anom/conflict
                    ::anom/message := (format "Duplicate transaction commands `delete-history-version Patient/0/_history/1` and `%s`." formatted)))

                (testing "after another delete history version command"
                  (given (verify-tx-cmds node 5 [{:op "delete-history-version" :type "Patient" :id "0" :t 2} delete-version cmd])
                    ::anom/category := ::anom/conflict
                    ::anom/message := (format "Duplicate transaction commands `delete-history-version Patient/0/_history/2` and `%s`." formatted)))))))))

    (testing "with another resource changed between its versions"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:create {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean false}]]
         [[:create {:fhir/type :fhir/Patient :id "1"}]]
         [[:put {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean true}]]]

        (testing "nothing to do on the t at which only the other resource changed"
          (is (empty? (verify-tx-cmds node 4 [{:op "delete-history-version" :type "Patient" :id "0" :t 2}]))))))

    (testing "with an already deleted version"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:create {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean false}]]
         [[:put {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean true}]]
         [[:put {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean false}]]
         [[:delete-history-version "Patient" "0" 2]]]

        (testing "nothing to do"
          (is (empty? (verify-tx-cmds node 5 [{:op "delete-history-version" :type "Patient" :id "0" :t 2}]))))))

    (testing "with a deleted current version"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:create {:fhir/type :fhir/Patient :id "0"}]]
         [[:delete "Patient" "0"]]]

        (testing "the deletion version can't be deleted"
          (given (verify-tx-cmds node 3 [{:op "delete-history-version" :type "Patient" :id "0" :t 2}])
            ::anom/category := ::anom/conflict
            ::anom/message := "Can't delete the current version `2` of the resource `Patient/0`."))

        (testing "the version before the deletion can be deleted"
          (given (verify-tx-cmds* node 3 [{:op "delete-history-version" :type "Patient" :id "0" :t 1}])
            [:entries count] := 5
            [:entries 0 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 1}
            [:entries 3 2 ts-tu/decode-val] := {:total 0 :num-changes 1}))))))

(deftest verify-distinct-tx-cmds-test
  (let [put {:op "put" :type "Patient" :id "0" :hash (hash/generate patient-0)}]
    (with-system [{:blaze.db/keys [node]} config]
      (testing "commands are checked for duplicates"
        (given (verify-tx-cmds node 1 [put put])
          ::anom/category := ::anom/conflict
          ::anom/message := "Duplicate transaction commands `put Patient/0` and `put Patient/0`."))

      (testing "commands known to act on distinct resources aren't checked for
                duplicates, so that the check doesn't cost time"
        (is (not (ba/anomaly?
                  (verify-tx-cmds** node 1 [put put]
                                    #(assoc % :distinct-tx-cmds true)))))))))

(deftest verify-purge-test
  (testing "empty database"
    (with-system [{:blaze.db/keys [node]} config]

      (is (empty? (verify-tx-cmds node 1 [{:op "purge" :type "Patient" :id "0"}])))))

  (testing "one patient"
    (let [hash (hash/generate patient-0)]
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:create patient-0]]]

        (given (verify-tx-cmds* node 2 [{:op "purge" :type "Patient" :id "0"}])
          [:entries count] := 5

          [:entries 0 0] := :resource-as-of-index
          [:entries 0 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 1}
          [:entries 0 2 rts-tu/decode-val] := {:hash hash :num-changes 1 :op :create :purged-at 2}

          [:entries 1 0] := :type-as-of-index
          [:entries 1 1 tao-tu/decode-key] := {:type "Patient" :t 1 :id "0"}
          [:entries 1 2 rts-tu/decode-val] := {:hash hash :num-changes 1 :op :create :purged-at 2}

          [:entries 2 0] := :system-as-of-index
          [:entries 2 1 sao-tu/decode-key] := {:t 1 :type "Patient" :id "0"}
          [:entries 2 2 rts-tu/decode-val] := {:hash hash :num-changes 1 :op :create :purged-at 2}

          [:entries 3 0] := :type-stats-index
          [:entries 3 1 ts-tu/decode-key] := {:type "Patient" :t 2}
          [:entries 3 2 ts-tu/decode-val] := {:total 0 :num-changes 0}

          [:entries 4 0] := :system-stats-index
          [:entries 4 1 ss-tu/decode-key] := {:t 2}
          [:entries 4 2 ss-tu/decode-val] := {:total 0 :num-changes 0}

          [:resource-handle-updates count] := 1
          [:resource-handle-updates 0 0 :t] := 1
          [:resource-handle-updates 0 0 :hash] := hash
          [:resource-handle-updates 0 0 :num-changes] := 1
          [:resource-handle-updates 0 0 :op] := :create
          [:resource-handle-updates 0 1] := true))))

  (testing "one patient with two versions"
    (let [patient-v1 {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean false}
          patient-v2 {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean true}
          hash-v2 (hash/generate patient-v2)]
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:create patient-v1]]
         [[:put patient-v2]]]

        (given (verify-tx-cmds* node 3 [{:op "purge" :type "Patient" :id "0"}])
          [:entries count] := 8

          ;; only the current version can be cached
          [:resource-handle-updates count] := 1
          [:resource-handle-updates 0 0 :t] := 2
          [:resource-handle-updates 0 0 :hash] := hash-v2
          [:resource-handle-updates 0 0 :num-changes] := 2
          [:resource-handle-updates 0 0 :op] := :put
          [:resource-handle-updates 0 1] := true))))

  (testing "one deleted patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:delete "Patient" "0"]]]

      (given (verify-tx-cmds* node 2 [{:op "purge" :type "Patient" :id "0"}])
        [:entries count] := 5

        [:entries 0 0] := :resource-as-of-index
        [:entries 0 1 rao-tu/decode-key] := {:type "Patient" :id "0" :t 1}
        [:entries 0 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 1 :op :delete :purged-at 2}

        [:entries 1 0] := :type-as-of-index
        [:entries 1 1 tao-tu/decode-key] := {:type "Patient" :t 1 :id "0"}
        [:entries 1 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 1 :op :delete :purged-at 2}

        [:entries 2 0] := :system-as-of-index
        [:entries 2 1 sao-tu/decode-key] := {:t 1 :type "Patient" :id "0"}
        [:entries 2 2 rts-tu/decode-val] := {:hash hash/deleted-hash :num-changes 1 :op :delete :purged-at 2}

        [:entries 3 0] := :type-stats-index
        [:entries 3 1 ts-tu/decode-key] := {:type "Patient" :t 2}
        [:entries 3 2 ts-tu/decode-val] := {:total 0 :num-changes 0}

        [:entries 4 0] := :system-stats-index
        [:entries 4 1 ss-tu/decode-key] := {:t 2}
        [:entries 4 2 ss-tu/decode-val] := {:total 0 :num-changes 0}

        [:resource-handle-updates count] := 1
        [:resource-handle-updates 0 0 :t] := 1
        [:resource-handle-updates 0 0 :hash] := hash/deleted-hash
        [:resource-handle-updates 0 0 :op] := :delete
        [:resource-handle-updates 0 1] := true)))

  (testing "purging a patient which is referenced by a still existing observation"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put patient-0]
        [:put observation-0]]]

      (given (verify-tx-cmds node 2 [{:op "purge" :type "Patient" :id "0" :check-refs true}])
        ::anom/category := ::anom/conflict
        ::anom/message := "Referential integrity violated. Resource `Patient/0` should be deleted but is referenced from `Observation/0`.")))

  (testing "purging a patient doesn't check the references through its purged
            rev-include params, through which the transaction purges every
            resource referencing it, as the expansion guarantees"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put patient-0]
        [:put observation-0]]]

      (testing "so that the observation not purged here isn't found"
        (is (not (ba/anomaly?
                  (verify-tx-cmds
                   node 2
                   [{:op "purge" :type "Patient" :id "0" :check-refs true
                     :purged-rev-include-params
                     #{(tx-u/rev-include-param "Observation" "subject")
                       (tx-u/rev-include-param "Observation" "patient")}}])))))

      (testing "but checks the references through the other search params"
        (given (verify-tx-cmds
                node 2
                [{:op "purge" :type "Patient" :id "0" :check-refs true
                  :purged-rev-include-params
                  #{(tx-u/rev-include-param "Observation" "subject")}}])
          ::anom/category := ::anom/conflict
          ::anom/message := "Referential integrity violated. Resource `Patient/0` should be deleted but is referenced from `Observation/0`."))))

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

(def ^:private internal-patient-0-v2
  {:fhir/type :fhir/Patient :id "0"
   :meta (type/meta {:security [internal-label]})
   :gender #fhir/code "male"})

(def ^:private num-large-tx-cmds
  "A number of commands that is split into several parts that are verified in
  parallel."
  2000)

(defn- create-cmds
  "Returns create commands of `num-large-tx-cmds` resources of `type` with the
  ids from 0 on, applying `f` to each command and its index."
  ([type]
   (create-cmds type (fn [cmd _] cmd)))
  ([type f]
   (let [hash (hash/generate {:fhir/type (keyword "fhir" type) :id "0"})]
     (mapv
      #(f {:op "create" :type type :id (str %) :hash hash} %)
      (range num-large-tx-cmds)))))

(defn- type-lookup?
  "Returns a predicate of the arguments of `d/resource-handle` that is true for
  lookups of resources of `lookup-type`."
  [lookup-type]
  (fn [_ type _] (= lookup-type type)))

(deftest verify-large-transaction-test
  (testing "the result is the one of verifying the commands one after another"
    (with-system [{:blaze.db/keys [node]} config]
      (given (verify-tx-cmds* node 1 (create-cmds "Patient"))
        patient-stats := {:total 2000 :num-changes 2000}
        [:resource-handle-updates count] := 2000
        [:resource-handle-updates 0 0 :id] := "0"
        [:resource-handle-updates 1999 0 :id] := "1999")))

  (testing "the anomaly is the one of the first failing command"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "100"}]
        [:create {:fhir/type :fhir/Patient :id "1500"}]]]

      (given (verify-tx-cmds* node 2 (create-cmds "Patient"))
        ::anom/category := ::anom/conflict
        ::anom/message := "Resource `Patient/100` already exists in the database with t = 1 and can't be created again.")))

  (testing "the anomaly of a command at the end is found"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "1999"}]]]

      (given (verify-tx-cmds* node 2 (create-cmds "Patient"))
        ::anom/category := ::anom/conflict
        ::anom/message := "Resource `Patient/1999` already exists in the database with t = 1 and can't be created again.")))

  (testing "the commands are verified in parallel"
    (with-system [{:blaze.db/keys [node]} config]
      (is (concurrent-calls?
           #'d/resource-handle (type-lookup? "Patient")
           #(verify-tx-cmds* node 1 (create-cmds "Patient")))))))

(defn- observation-cmd-referencing
  "Returns `cmd` referencing the Patient with `patient-id`."
  [patient-id cmd]
  (assoc cmd :refs [["Patient" patient-id]]))

(deftest check-referential-integrity-large-transaction-test
  (testing "the anomaly is the one of the first failing command"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create patient-0]]]

      (given (verify-tx-cmds*
              node 2
              (create-cmds
               "Observation"
               (fn [cmd idx]
                 (observation-cmd-referencing
                  (get {100 "100" 1500 "1500"} idx "0") cmd))))
        ::anom/category := ::anom/conflict
        ::anom/message := "Referential integrity violated. Resource `Patient/100` doesn't exist.")))

  (testing "the references are checked in parallel"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create patient-0]]]

      (is (concurrent-calls?
           #'d/resource-handle (type-lookup? "Patient")
           #(verify-tx-cmds*
             node 2
             (create-cmds
              "Observation"
              (fn [cmd _] (observation-cmd-referencing "0" cmd)))))))))

(defn- num-lookups
  "Returns the number of lookups, hits and misses, of `resource-handle-cache`."
  [resource-handle-cache]
  (CacheStats/.requestCount (ccp/-stats resource-handle-cache)))

(defn- num-verify-lookups
  "Returns the number of lookups of `resource-handle-cache` verifying `tx-cmds`
  against `node` takes, failing if the verification fails."
  [node resource-handle-cache t tx-cmds]
  (let [lookups-before (num-lookups resource-handle-cache)]
    (is (not (ba/anomaly? (verify-tx-cmds* node t tx-cmds))))
    (- (num-lookups resource-handle-cache) lookups-before)))

(deftest verify-resource-handle-lookups-test
  (testing "a purge checking references looks up no resource handle"
    (with-system-data [{:blaze.db/keys [node resource-handle-cache]}
                       resource-handle-cache-config]
      [[[:put patient-0]]]

      (is (zero? (num-verify-lookups
                  node resource-handle-cache 2
                  [{:op "purge" :type "Patient" :id "0" :check-refs true}])))))

  (testing "a delete checking references looks up the resource handle only once"
    (with-system-data [{:blaze.db/keys [node resource-handle-cache]}
                       resource-handle-cache-config]
      [[[:put patient-0]]]

      (is (= 1 (num-verify-lookups
                node resource-handle-cache 2
                [{:op "delete" :type "Patient" :id "0" :check-refs true}]))))))

(def ^:private should-be-deleted-msg
  "Referential integrity violated. Resource `Patient/0` should be deleted but is referenced from `Observation/0`.")

(def ^:private doesnt-exist-msg
  "Referential integrity violated. Resource `Patient/0` doesn't exist.")

(def ^:private patient-0-states
  "The transactions bringing Patient/0 into each state."
  {"existing" [[[:put patient-0]]]
   "already deleted" [[[:put patient-0]] [[:delete "Patient" "0"]]]
   "missing" [[[:put patient-1]]]})

(defn- write-referencing-deleted-msg
  "Returns the message of a write referencing Patient/0 in `state` that is
  deleted or purged by `op` in the same transaction.

  Only a resource deleted by the transaction should be deleted. A delete of a
  missing resource deletes it too, but its purge doesn't."
  [state op]
  (cond
    (= "existing" state) should-be-deleted-msg
    (= "already deleted" state) doesnt-exist-msg
    (= "delete" op) should-be-deleted-msg
    :else doesnt-exist-msg))

(deftest verify-write-referencing-deleted-resource-test
  (let [write {:op "put" :type "Observation" :id "0"
               :hash (hash/generate observation-0) :refs [["Patient" "0"]]}]
    (doseq [[state txs] patient-0-states
            op ["delete" "purge"]
            check-refs [true false]]
      (testing (format "a write referencing a%s %s patient %s %s references"
                       (if (= "existing" state) "n" "") state op
                       (if check-refs "checking" "not checking"))
        (with-system-data [{:blaze.db/keys [node]} config]
          txs

          (given (verify-tx-cmds
                  node (inc (count txs))
                  [(cond-> {:op op :type "Patient" :id "0"}
                     check-refs (assoc :check-refs true))
                   write])
            ::anom/category := ::anom/conflict
            ::anom/message := (write-referencing-deleted-msg state op)))))))

(defn- num-prefix-seeks
  "Returns the number of calls of `sp-vr/index-handles`, the seeks of the
  reference prefilter of deletes, verifying `tx-cmds` against `node` takes,
  failing if the verification fails."
  [node t tx-cmds]
  (let [calls (atom 0)
        index-handles sp-vr/index-handles]
    (with-redefs [sp-vr/index-handles (fn [& args]
                                        (swap! calls inc)
                                        (apply index-handles args))]
      (is (not (ba/anomaly? (verify-tx-cmds* node t tx-cmds)))))
    @calls))

(defn- checked-delete-cmd [type id]
  {:op "delete" :type type :id id :check-refs true})

(defn- unchecked-delete-cmd
  "Returns a delete command not checking references. Of a resource that doesn't
  exist, it fills a part of the verification without a delete handle."
  [type id]
  {:op "delete" :type type :id id})

(deftest verify-delete-reference-prefilter-test
  (with-system-data [{:blaze.db/keys [node]} config]
    [[[:put patient-0]
      [:put patient-1]
      [:put {:fhir/type :fhir/Observation :id "0"}]]]

    (testing "a single delete checking references doesn't prefilter the search
              params, because the prefilter would take a seek per search param
              in addition to the ones of the delete"
      (is (zero? (num-prefix-seeks
                  node 2 [(checked-delete-cmd "Patient" "0")]))))

    (testing "a delete of a resource that doesn't exist isn't counted, because
              its references aren't checked"
      (is (zero? (num-prefix-seeks
                  node 2 [(checked-delete-cmd "Patient" "0")
                          (checked-delete-cmd "Patient" "2")]))))

    (testing "two deletes of resources of the same type checking references
              prefilter the search params"
      (is (pos? (num-prefix-seeks
                 node 2 [(checked-delete-cmd "Patient" "0")
                         (checked-delete-cmd "Patient" "1")])))

      (testing "also if they are verified in different parts"
        (is (pos? (num-prefix-seeks
                   node 2 (-> [(checked-delete-cmd "Patient" "0")]
                              (into (map #(unchecked-delete-cmd "Observation" (str "x" %)))
                                    (range 600))
                              (conj (checked-delete-cmd "Patient" "1"))))))))))

(deftest check-referential-integrity-deletes-of-several-parts-test
  (testing "a deleted resource referenced only by a resource deleted in another
            part of the transaction doesn't violate the referential integrity,
            because the deletes of all parts are merged"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put patient-0]
        [:put observation-0]]]

      (is (not (ba/anomaly?
                (verify-tx-cmds
                 node 2
                 (-> [(checked-delete-cmd "Patient" "0")]
                     (into (map #(unchecked-delete-cmd "Observation" (str "x" %)))
                           (range 600))
                     (conj (checked-delete-cmd "Observation" "0"))))))))))

(defn- num-matches-calls
  "Returns the number of calls of `d/matches?` verifying `tx-cmds` against
  `node` takes, failing if the verification fails."
  [node t tx-cmds]
  (let [calls (atom 0)
        matches? d/matches?]
    (with-redefs [d/matches? (fn [db matcher resource-handle]
                               (swap! calls inc)
                               (matches? db matcher resource-handle))]
      (is (not (ba/anomaly? (verify-tx-cmds* node t tx-cmds)))))
    @calls))

(deftest verify-internal-matches-calls-test
  (with-system [{:blaze.db/keys [node]} config]
    (transact-internal node [[:create internal-patient-0]
                             [:create patient-1]
                             [:create observation-0]])

    (testing "a purge of a resource of a type without resources labelled
              internal doesn't match the resource"
      (is (zero? (num-matches-calls
                  node 2 [{:op "purge" :type "Observation" :id "0"}]))))

    (testing "a purge of a resource of a type with resources labelled internal
              matches the resource"
      (is (= 1 (num-matches-calls
                node 2 [{:op "purge" :type "Patient" :id "1"}]))))))

(def ^:private patient-1-v2
  (assoc patient-1 :gender #fhir/code "male"))

(def ^:private observation-0-v2
  (assoc observation-0 :status #fhir/code "final"))

(deftest verify-read-only-matches-calls-test
  (with-system [{:blaze.db/keys [node]} config]
    (transact-internal node [[:create read-only-patient-0]
                             [:create patient-1]
                             [:create observation-0]])

    (testing "an update of a resource of a type without resources tagged
              read-only doesn't match the resource"
      (is (zero? (num-matches-calls
                  node 2 [{:op "put" :type "Observation" :id "0"
                           :hash (hash/generate observation-0-v2)}]))))

    (testing "an update of a resource of a type with resources tagged read-only
              matches the resource"
      (is (= 1 (num-matches-calls
                node 2 [{:op "put" :type "Patient" :id "1"
                         :hash (hash/generate patient-1-v2)}]))))

    (testing "a delete of a resource of a type without resources tagged
              read-only doesn't match the resource"
      (is (zero? (num-matches-calls
                  node 2 [{:op "delete" :type "Observation" :id "0"}]))))

    (testing "a delete of a resource of a type with resources tagged read-only
              matches the resource"
      (is (= 1 (num-matches-calls
                node 2 [{:op "delete" :type "Patient" :id "1"}]))))))

(defn- type-lookups
  "Returns the set of tuples of the AccessControl code of the matcher and the
  type `impl/type-may-match?` is called with verifying `tx-cmds` against
  `node`, failing if the verification fails."
  [{:keys [read-only-matcher internal-matcher] :as node} t tx-cmds]
  (let [lookups (atom #{})
        type-may-match? impl/type-may-match?]
    (with-redefs [impl/type-may-match?
                  (fn [db matcher type]
                    (swap! lookups conj [(condp identical? matcher
                                           read-only-matcher "read-only"
                                           internal-matcher "internal")
                                         type])
                    (type-may-match? db matcher type))]
      (is (not (ba/anomaly? (verify-tx-cmds* node t tx-cmds)))))
    @lookups))

(deftest verify-type-lookups-test
  (with-system [{:blaze.db/keys [node]} config]
    (transact-internal node [[:create internal-patient-0]
                             [:create patient-1]
                             [:create observation-0]])

    (testing "a transaction that changes no existing resource looks up no type"
      (is (empty? (type-lookups
                   node 2 [{:op "create" :type "Observation" :id "1"
                            :hash (hash/generate observation-1)}
                           {:op "purge" :type "Encounter" :id "0"}]))))

    (testing "the types of all commands are looked up for each label, whatever
              their op, so that no check depends on a list of ops"
      (is (= #{["read-only" "Observation"]
               ["read-only" "Condition"]
               ["read-only" "Encounter"]
               ["read-only" "Patient"]
               ["internal" "Observation"]
               ["internal" "Condition"]
               ["internal" "Encounter"]
               ["internal" "Patient"]}
             (type-lookups
              node 2 [{:op "create" :type "Observation" :id "1"
                       :hash (hash/generate observation-1)}
                      (assoc (verify-cmd "hold") :type "Condition")
                      {:op "keep" :type "Observation" :id "0"
                       :hash (hash/generate observation-0)}
                      {:op "purge" :type "Encounter" :id "0"}
                      {:op "put" :type "Patient" :id "1"
                       :hash (hash/generate patient-1-v2)}]))))))

(def ^:private read-only-permissions
  {:write-access-control #{"read-only"}})

(def ^:private internal-permissions
  {:write-access-control #{"internal"}})

(defn- put-cmd [resource]
  {:op "put" :type "Patient" :id "0" :hash (hash/generate resource)})

(deftest verify-read-only-current-version-test
  (testing "put"
    (with-system [{:blaze.db/keys [node]} config]
      (transact-internal node [[:create read-only-patient-0]])

      (testing "without permissions fails"
        (doseq [permissions [nil {} internal-permissions]]
          (given (verify-tx-cmds-with node 2 [(put-cmd patient-0-v2)] permissions)
            ::anom/category := ::anom/conflict
            ::anom/message := "Can't update the read-only resource `Patient/0`.")))

      (testing "with permission for read-only succeeds"
        (given (verify-tx-cmds-with node 2 [(put-cmd patient-0-v2)] read-only-permissions)
          count := 5))

      (testing "identical update without permissions is a no-op"
        (given (verify-tx-cmds node 2 [(put-cmd read-only-patient-0)])
          count := 0))))

  (testing "delete"
    (with-system [{:blaze.db/keys [node]} config]
      (transact-internal node [[:create read-only-patient-0]])

      (testing "without permissions fails"
        (given (verify-tx-cmds node 2 [{:op "delete" :type "Patient" :id "0"}])
          ::anom/category := ::anom/conflict
          ::anom/message := "Can't delete the read-only resource `Patient/0`."))

      (testing "with permission for read-only succeeds"
        (given (verify-tx-cmds-with node 2 [{:op "delete" :type "Patient" :id "0"}]
                                    read-only-permissions)
          count := 5))))

  (testing "delete-history and purge stay allowed without permissions"
    (with-system [{:blaze.db/keys [node]} config]
      (transact-internal node [[:create read-only-patient-0]])
      (transact-internal node [[:put (assoc read-only-patient-0 :gender #fhir/code "male")]])

      (doseq [op ["delete-history" "purge"]]
        (given (verify-tx-cmds node 3 [{:op op :type "Patient" :id "0"}])
          count :? pos-int?)))))

(defn- delete-history-version-cmd [t]
  {:op "delete-history-version" :type "Patient" :id "0" :t t})

(deftest verify-internal-current-version-test
  (doseq [[op msg] [["delete" "Can't delete the internal resource `Patient/0`."]
                    ["delete-history" "Can't delete the history of the internal resource `Patient/0`."]
                    ["purge" "Can't purge the internal resource `Patient/0`."]]]
    (testing op
      (with-system [{:blaze.db/keys [node]} config]
        (transact-internal node [[:create internal-patient-0]])
        (transact-internal node [[:put internal-patient-0-v2]])

        (testing "without permissions fails"
          (doseq [permissions [nil {} read-only-permissions]]
            (given (verify-tx-cmds-with node 3 [{:op op :type "Patient" :id "0"}]
                                        permissions)
              ::anom/category := ::anom/conflict
              ::anom/message := msg)))

        (testing "with permission for internal succeeds"
          (given (verify-tx-cmds-with node 3 [{:op op :type "Patient" :id "0"}]
                                      internal-permissions)
            count :? pos-int?)))))

  (testing "put"
    (with-system [{:blaze.db/keys [node]} config]
      (transact-internal node [[:create internal-patient-0]])

      (testing "without permissions fails"
        (doseq [permissions [nil {} read-only-permissions]]
          (given (verify-tx-cmds-with node 2 [(put-cmd patient-0-v2)] permissions)
            ::anom/category := ::anom/conflict
            ::anom/message := "Can't update the internal resource `Patient/0`.")))

      (testing "with permission for internal succeeds"
        (given (verify-tx-cmds-with node 2 [(put-cmd patient-0-v2)] internal-permissions)
          count := 5))

      (testing "identical update without permissions is a no-op"
        (given (verify-tx-cmds node 2 [(put-cmd internal-patient-0)])
          count := 0))

      (testing "a stale if-match fails with the precondition anomaly regardless
                of the permissions"
        (given (verify-tx-cmds-with node 2 [(assoc (put-cmd patient-0-v2) :if-match [2])]
                                    p/internal)
          ::anom/category := ::anom/conflict
          ::anom/message := "Precondition `W/\"2\"` failed on `Patient/0`."
          :http/status := 412))))

  (testing "delete-history-version"
    (with-system [{:blaze.db/keys [node]} config]
      (transact-internal node [[:create internal-patient-0]])
      (transact-internal node [[:put internal-patient-0-v2]])
      (transact-internal node [[:put (assoc internal-patient-0-v2 :gender #fhir/code "female")]])

      (testing "without permissions fails"
        (doseq [permissions [nil {} read-only-permissions]]
          (given (verify-tx-cmds-with node 4 [(delete-history-version-cmd 1)]
                                      permissions)
            ::anom/category := ::anom/conflict
            ::anom/message := "Can't delete a history version of the internal resource `Patient/0`.")))

      (testing "deleting all historical versions in one transaction without
                permissions fails"
        (given (verify-tx-cmds node 4 [(delete-history-version-cmd 1)
                                       (delete-history-version-cmd 2)])
          ::anom/category := ::anom/conflict
          ::anom/message := "Can't delete a history version of the internal resource `Patient/0`."))

      (testing "with permission for internal succeeds"
        (given (verify-tx-cmds-with node 4 [(delete-history-version-cmd 1)
                                            (delete-history-version-cmd 2)]
                                    internal-permissions)
          count :? pos-int?))

      (testing "deleting a non-existing version without permissions is a no-op"
        (given (verify-tx-cmds node 4 [(delete-history-version-cmd 4)])
          count := 0)))))

(defn- write-cmd [op access-control]
  (cond-> {:op op :type "Patient" :id "0" :hash (hash/generate patient-0)}
    access-control (assoc :access-control access-control)))

(deftest verify-new-version-test
  (doseq [[op verb] [["create" "create"] ["put" "update"]]]
    (testing op
      (doseq [[code permission] [["internal" internal-permissions]
                                 ["read-only" read-only-permissions]]]
        (testing (str "labelled with " code)
          (with-system [{:blaze.db/keys [node]} config]
            (testing "without permissions fails"
              (doseq [permissions [nil {}]]
                (given (verify-tx-cmds-with node 1 [(write-cmd op [code])] permissions)
                  ::anom/category := ::anom/conflict
                  ::anom/message := (format "Can't %s the resource `Patient/0` labelled with the AccessControl code `%s`." verb code))))

            (testing "with permission for the code succeeds"
              (given (verify-tx-cmds-with node 1 [(write-cmd op [code])] permission)
                count := 5)))))

      (testing "labelled with both codes"
        (with-system [{:blaze.db/keys [node]} config]
          (testing "with permission for only one code fails"
            (given (verify-tx-cmds-with node 1 [(write-cmd op ["read-only" "internal"])]
                                        read-only-permissions)
              ::anom/category := ::anom/conflict
              ::anom/message := (format "Can't %s the resource `Patient/0` labelled with the AccessControl code `internal`." verb))

            (given (verify-tx-cmds-with node 1 [(write-cmd op ["read-only" "internal"])]
                                        internal-permissions)
              ::anom/category := ::anom/conflict
              ::anom/message := (format "Can't %s the resource `Patient/0` labelled with the AccessControl code `read-only`." verb)))

          (testing "with permission for both codes succeeds"
            (given (verify-tx-cmds-with node 1 [(write-cmd op ["read-only" "internal"])]
                                        p/internal)
              count := 5))))

      (testing "labelled with an unknown code fails even for internal code"
        (with-system [{:blaze.db/keys [node]} config]
          (given (verify-tx-cmds-with node 1 [(write-cmd op ["foo"])] p/internal)
            ::anom/category := ::anom/conflict
            ::anom/message := (format "Can't %s the resource `Patient/0` labelled with the AccessControl code `foo`." verb))))

      (testing "a command without extracted codes, like an old log entry,
                succeeds without permissions"
        (with-system [{:blaze.db/keys [node]} config]
          (given (verify-tx-cmds node 1 [(write-cmd op nil)])
            count := 5))))))
