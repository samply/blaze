(ns blaze.db.node.stats-test
  (:require
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.system-stats-test-util :as ss-tu]
   [blaze.db.impl.index.type-stats-test-util :as ts-tu]
   [blaze.db.kv.mem]
   [blaze.db.kv.mem-spec]
   [blaze.db.node]
   [blaze.db.node.stats :as stats]
   [blaze.db.node.stats-spec]
   [blaze.db.test-util :refer [config with-system-data]]
   [blaze.db.tx-cache]
   [blaze.db.tx-log.local]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def patient-0 {:fhir/type :fhir/Patient :id "0"})
(def patient-1 {:fhir/type :fhir/Patient :id "1"})
(def observation-0 {:fhir/type :fhir/Observation :id "0"
                    :subject #fhir/Reference{:reference #fhir/string "Patient/0"}})

(def patient-tid (codec/tid "Patient"))
(def observation-tid (codec/tid "Observation"))

(defn- patient-value
  "Returns the value of the Patient type of `stats`.

  A function, because the tid isn't a path element `given` understands."
  [stats]
  (get-in stats [:types patient-tid]))

(defn- observation-value [stats]
  (get-in stats [:types observation-tid]))

(deftest init-test
  (testing "an empty store has empty stats"
    (with-system [{:blaze.db/keys [node]} config]
      (is (= stats/empty-stats (stats/init (:kv-store node) 0)))))

  (testing "one patient"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put patient-0]]]

      (given (stats/init (:kv-store node) 1)
        patient-value := {:total 1 :num-changes 1}
        :system := {:total 1 :num-changes 1})))

  (testing "two types"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put patient-0] [:put observation-0]]]

      (given (stats/init (:kv-store node) 1)
        patient-value := {:total 1 :num-changes 1}
        observation-value := {:total 1 :num-changes 1}
        :system := {:total 2 :num-changes 2})))

  (testing "a deleted patient still counts as change"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put patient-0]]
       [[:delete "Patient" "0"]]]

      (given (stats/init (:kv-store node) 2)
        patient-value := {:total 0 :num-changes 2}
        :system := {:total 0 :num-changes 2})))

  (testing "at an earlier t"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put patient-0]]
       [[:put patient-1]]]

      (given (stats/init (:kv-store node) 1)
        patient-value := {:total 1 :num-changes 1}
        :system := {:total 1 :num-changes 1})))

  (testing "the stats a node keeps are the ones of its indexes"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put patient-0] [:put observation-0]]
       [[:put patient-1]]
       [[:delete "Patient" "1"]]]

      (is (= @(:stats node)
             (stats/init (:kv-store node) (:t @(:state node))))))))

(deftest apply-tx-test
  (testing "a transaction without increments produces no entries"
    (doseq [increments [nil {}]]
      (is (= [[] stats/empty-stats]
             (stats/apply-tx stats/empty-stats 1 increments)))))

  (testing "one type of an empty database"
    (given (stats/apply-tx stats/empty-stats 1
                           {patient-tid {:total 1 :num-changes 1}})
      [0 count] := 2

      [0 0 0] := :type-stats-index
      [0 0 1 ts-tu/decode-key] := {:type "Patient" :t 1}
      [0 0 2 ts-tu/decode-val] := {:total 1 :num-changes 1}

      [0 1 0] := :system-stats-index
      [0 1 1 ss-tu/decode-key] := {:t 1}
      [0 1 2 ss-tu/decode-val] := {:total 1 :num-changes 1}

      [1 patient-value] := {:total 1 :num-changes 1}
      [1 :system] := {:total 1 :num-changes 1}))

  (testing "the increments are added to the current stats"
    (let [stats {:types {patient-tid {:total 1 :num-changes 1}}
                 :system {:total 1 :num-changes 1}}]
      (given (stats/apply-tx stats 2 {patient-tid {:total 1 :num-changes 1}})
        [0 0 2 ts-tu/decode-val] := {:total 2 :num-changes 2}
        [0 1 2 ss-tu/decode-val] := {:total 2 :num-changes 2}

        [1 patient-value] := {:total 2 :num-changes 2}
        [1 :system] := {:total 2 :num-changes 2})))

  (testing "increments without a total leave the total untouched"
    (let [stats {:types {patient-tid {:total 1 :num-changes 2}}
                 :system {:total 1 :num-changes 2}}]
      (given (stats/apply-tx stats 3 {patient-tid {:num-changes -1}})
        [0 0 2 ts-tu/decode-val] := {:total 1 :num-changes 1}
        [0 1 2 ss-tu/decode-val] := {:total 1 :num-changes 1}

        [1 patient-value] := {:total 1 :num-changes 1}
        [1 :system] := {:total 1 :num-changes 1})))

  (testing "increments without a number of changes leave it untouched"
    (let [stats {:types {patient-tid {:total 1 :num-changes 1}}
                 :system {:total 1 :num-changes 1}}]
      (given (stats/apply-tx stats 2 {patient-tid {:total -1}})
        [0 0 2 ts-tu/decode-val] := {:total 0 :num-changes 1}
        [0 1 2 ss-tu/decode-val] := {:total 0 :num-changes 1}

        [1 patient-value] := {:total 0 :num-changes 1}
        [1 :system] := {:total 0 :num-changes 1})))

  (testing "the increments of all types are added to the system stats"
    (given (stats/apply-tx stats/empty-stats 1
                           {patient-tid {:total 1 :num-changes 1}
                            observation-tid {:total 2 :num-changes 2}})
      [0 count] := 3

      [0 peek 0] := :system-stats-index
      [0 peek 2 ss-tu/decode-val] := {:total 3 :num-changes 3}

      [1 patient-value] := {:total 1 :num-changes 1}
      [1 observation-value] := {:total 2 :num-changes 2}
      [1 :system] := {:total 3 :num-changes 3}))

  (testing "a type not touched by the transaction keeps its stats"
    (let [stats {:types {patient-tid {:total 1 :num-changes 1}}
                 :system {:total 1 :num-changes 1}}]
      (given (stats/apply-tx stats 2 {observation-tid {:total 1 :num-changes 1}})
        [0 count] := 2

        [1 patient-value] := {:total 1 :num-changes 1}
        [1 observation-value] := {:total 1 :num-changes 1}
        [1 :system] := {:total 2 :num-changes 2}))))
