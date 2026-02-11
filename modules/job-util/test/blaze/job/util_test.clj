(ns blaze.job.util-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.api-stub :refer [mem-node-config with-system-data]]
   [blaze.fhir.spec.type :as type]
   [blaze.job.util :as job-util]
   [blaze.job.util-spec]
   [blaze.module.test-util :as mtu :refer [given-failed-future]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(defn- codeable-concept [system code]
  (type/codeable-concept
   {:coding
    [(type/coding {:system (type/uri system) :code (type/code code)})]}))

(deftest job-number-test
  (is (= (job-util/job-number
          {:fhir/type :fhir/Task
           :identifier
           [#fhir/Identifier{:system #fhir/uri "https://samply.github.io/blaze/fhir/sid/JobNumber"
                             :value #fhir/string "174731"}]})
         "174731")))

(deftest code-value-test
  (is (= "bar" (job-util/code-value "foo" (codeable-concept "foo" "bar"))))
  (is (nil? (job-util/code-value "foo" (codeable-concept "bar" "baz")))))

(deftest job-type-test
  (is (= (job-util/job-type
          {:fhir/type :fhir/Task
           :code #fhir/CodeableConcept
                  {:coding
                   [#fhir/Coding
                     {:system #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/JobType"
                      :code #fhir/code "type-140532"}]}})
         :type-140532)))

(deftest status-reason-test
  (is (= (job-util/status-reason
          {:fhir/type :fhir/Task
           :statusReason #fhir/CodeableConcept
                          {:coding
                           [#fhir/Coding
                             {:system #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/JobStatusReason"
                              :code #fhir/code "reason-175220"}]}})
         "reason-175220")))

(deftest cancelled-sub-status-test
  (is (= (job-util/cancelled-sub-status
          {:fhir/type :fhir/Task
           :businessStatus #fhir/CodeableConcept
                            {:coding
                             [#fhir/Coding
                               {:system #fhir/uri "https://samply.github.io/blaze/fhir/CodeSystem/JobCancelledSubStatus"
                                :code #fhir/code "sub-status-161316"}]}})
         "sub-status-161316")))

(deftest input-value-test
  (is (= (job-util/input-value
          {:fhir/type :fhir/Task
           :input
           [{:fhir/type :fhir.Task/input
             :type (codeable-concept "foo" "other")
             :value #fhir/string "other"}
            {:fhir/type :fhir.Task/input
             :type (codeable-concept "foo" "bar")
             :value #fhir/code "baz"}]}
          "foo" "bar")
         #fhir/code "baz")))

(deftest output-value-test
  (is (= (job-util/output-value
          {:fhir/type :fhir/Task
           :output
           [(job-util/task-output "foo" "other" #fhir/string "other")
            (job-util/task-output "foo" "bar" #fhir/code "baz")]}
          "foo" "bar")
         #fhir/code "baz"))

  (testing "with default output system"
    (is (= (job-util/output-value
            {:fhir/type :fhir/Task
             :output
             [(job-util/task-output "foo" "other" #fhir/string "other")
              (job-util/task-output "https://samply.github.io/blaze/fhir/CodeSystem/JobOutput" "bar" #fhir/code "baz")]}
            "bar")
           #fhir/code "baz"))))

(deftest error-msg-test
  (is (= (job-util/error-msg
          {:fhir/type :fhir/Task
           :output
           [(job-util/task-output "https://samply.github.io/blaze/fhir/CodeSystem/JobOutput" "error" #fhir/string "msg-175657")]})
         "msg-175657")))

(deftest update-output-value-test
  (is (= (job-util/update-output-value
          {:fhir/type :fhir/Task
           :output
           [(job-util/task-output "foo" "other" #fhir/string "other")
            (job-util/task-output "foo" "bar" #fhir/integer 1)]}
          "foo" "bar"
          (fn [value x]
            (type/integer (+ (:value value) x)))
          1)
         {:fhir/type :fhir/Task
          :output
          [(job-util/task-output "foo" "other" #fhir/string "other")
           (job-util/task-output "foo" "bar" #fhir/integer 2)]})))

(deftest remove-output-test
  (is (= (job-util/remove-output
          {:fhir/type :fhir/Task
           :output
           [(job-util/task-output "foo" "other" #fhir/string "other")
            (job-util/task-output "foo" "bar" #fhir/integer 1)]}
          "foo" "bar")
         {:fhir/type :fhir/Task
          :output
          [(job-util/task-output "foo" "other" #fhir/string "other")]})))

(defn- start-job [job]
  (assoc job :status #fhir/code "in-progress"))

(deftest pull-job-test
  (with-system-data [{:blaze.db/keys [node]} mem-node-config]
    [[[:put {:fhir/type :fhir/Task :id "0"}]]]

    (given @(mtu/assoc-thread-name (job-util/pull-job node "0"))
      [meta :thread-name] :? mtu/common-pool-thread?
      :fhir/type := :fhir/Task
      :id := "0")))

(deftest update-job-test
  (with-system-data [{:blaze.db/keys [node]} mem-node-config]
    [[[:put {:fhir/type :fhir/Task :id "0"}]]]

    (let [job @(job-util/pull-job node "0")]

      (testing "start job"
        (let [job @(mtu/assoc-thread-name (job-util/update-job node job start-job))]
          (given job
            [meta :thread-name] :? mtu/common-pool-thread?
            :status := #fhir/code "in-progress")

          (testing "fail job"
            (given @(job-util/update-job node job job-util/fail-job (ba/fault "msg-181135"))
              :status := #fhir/code "failed"
              job-util/error-msg := "msg-181135"))))))

  (testing "lost updates are detected"
    (with-system-data [{:blaze.db/keys [node]} mem-node-config]
      [[[:put {:fhir/type :fhir/Task :id "0"}]]]

      (let [job @(job-util/pull-job node "0")]

        (testing "start job"
          (given @(job-util/update-job node job start-job)
            :status := #fhir/code "in-progress"))

        (testing "start job again fails"
          (given-failed-future (job-util/update-job node job start-job)
            ::anom/category := ::anom/conflict
            ::anom/message := "Precondition `W/\"1\"` failed on `Task/0`."
            job-util/job-update-failed? := true))

        (testing "fail job again fails"
          (given-failed-future (job-util/update-job node job job-util/fail-job (ba/fault "msg-181135"))
            ::anom/category := ::anom/conflict
            ::anom/message := "Precondition `W/\"1\"` failed on `Task/0`."
            job-util/job-update-failed? := true))))))

(deftest update-job-plus-test
  (testing "without other resources"
    (testing "with no argument"
      (with-system-data [{:blaze.db/keys [node]} mem-node-config]
        [[[:put {:fhir/type :fhir/Task :id "0"}]]]

        (let [job @(job-util/pull-job node "0")]

          (given @(mtu/assoc-thread-name (job-util/update-job+ node job nil start-job))
            [meta :thread-name] :? mtu/common-pool-thread?
            :status := #fhir/code "in-progress"))))

    (testing "with one argument"
      (with-system-data [{:blaze.db/keys [node]} mem-node-config]
        [[[:put {:fhir/type :fhir/Task :id "0"}]]]

        (let [job @(job-util/pull-job node "0")]

          (given @(job-util/update-job+ node job nil job-util/fail-job (ba/fault "msg-162452"))
            :status := #fhir/code "failed"
            job-util/error-msg := "msg-162452"))))

    (testing "with two arguments"
      (with-system-data [{:blaze.db/keys [node]} mem-node-config]
        [[[:put {:fhir/type :fhir/Task :id "0"}]]]

        (let [job @(job-util/pull-job node "0")]

          (given @(job-util/update-job+ node job nil (fn [job a b] (assoc job :status (type/code (str a "-" b)))) "on" "hold")
            :status := #fhir/code "on-hold")))))

  (testing "with one Bundle"
    (with-system-data [{:blaze.db/keys [node]} mem-node-config]
      [[[:put {:fhir/type :fhir/Task :id "0"}]]]

      (let [job @(job-util/pull-job node "0")]

        (given @(job-util/update-job+ node job [{:fhir/type :fhir/Bundle :id "0"}]
                                      job-util/fail-job (ba/fault "msg-181135"))
          :status := #fhir/code "failed"
          job-util/error := (ba/fault "msg-181135"))

        (is (some? (d/resource-handle (d/db node) "Bundle" "0")))))))

(defn- start-job* [{:keys [status] :as job}]
  (if (nil? status)
    (assoc job :status #fhir/code "in-progress")
    (ba/conflict "already started")))

(defn- add-unknown-bundle-reference [job]
  (job-util/add-output job "foo" "bar" #fhir/Reference {:reference #fhir/string "Bundle/unknown"}))

(deftest update-job-with-retry-test
  (testing "retry"
    (with-system-data [{:blaze.db/keys [node]} mem-node-config]
      [[[:put {:fhir/type :fhir/Task :id "0"}]]]

      (given @(job-util/update-job-with-retry node 1 "0" start-job*)
        [:meta :versionId] := #fhir/id "2"
        :status := #fhir/code "in-progress")

      (testing "fails because it's already started"
        (given-failed-future (job-util/update-job-with-retry node 1 "0" start-job*)
          ::anom/category := ::anom/conflict
          ::anom/message := "already started"))))

  (testing "referential integrity problem"
    (with-system-data [{:blaze.db/keys [node]} mem-node-config]
      [[[:put {:fhir/type :fhir/Task :id "0"}]]]

      (given-failed-future (job-util/update-job-with-retry node 1 "0" add-unknown-bundle-reference)
        ::anom/category := ::anom/conflict
        ::anom/message := "Referential integrity violated. Resource `Bundle/unknown` doesn't exist."))))

(def ^:private mask-anomaly vector)

(defn- start-job-with-retry [node id]
  (-> (job-util/update-job-with-retry node 5 id start-job*)
      (ac/exceptionally mask-anomaly)))

(defn- increment-unsigned-int [value n]
  (type/unsignedInt (+ (:value value) n)))

(defn- increment-count [job]
  (job-util/update-output-value job "my" "count" increment-unsigned-int 1))

(defn- increment-count-with-retry [node n id]
  (job-util/update-job-with-retry node n id increment-count))

(defn- get-count [job]
  (job-util/output-value job "my" "count"))

(deftest update-job-with-retry-concurrent-test
  (testing "single status change"
    (with-system-data [{:blaze.db/keys [node]} mem-node-config]
      [[[:put {:fhir/type :fhir/Task :id "0"}]]]

      (let [futures (repeatedly 5 #(start-job-with-retry node "0"))]
        @(ac/all-of futures)
        (let [{jobs true anomalies false} (group-by map? (map ac/join futures))]
          (is (= 1 (count jobs)))
          (is (= #fhir/code "in-progress" (:status (first jobs))))
          (is (every? (comp #{"already started"} ::anom/message) (map first anomalies)))))))

  (testing "multiple counter increments"
    (testing "are all sucessful"
      (with-system-data [{:blaze.db/keys [node]} mem-node-config]
        [[[:put (-> {:fhir/type :fhir/Task :id "0"}
                    (job-util/add-output "my" "count" #fhir/unsignedInt 0))]]]

        (let [futures (repeatedly 5 #(increment-count-with-retry node 4 "0"))]
          @(ac/all-of futures)
          (let [jobs (map ac/join futures)]
            (is (= 5 (count jobs)))
            (is (= #{1 2 3 4 5} (into #{} (map (comp :value get-count)) jobs)))))))

    (testing "at least one fails"
      (with-system-data [{:blaze.db/keys [node]} mem-node-config]
        [[[:put (-> {:fhir/type :fhir/Task :id "0"}
                    (job-util/add-output "my" "count" #fhir/unsignedInt 0))]]]

        (given-failed-future (ac/all-of (repeatedly 50 #(increment-count-with-retry node 2 "0")))
          ::anom/category := ::anom/conflict
          :http/status := 412)))))

(deftest job-update-failed-test
  (are [anomaly] (false? (job-util/job-update-failed? anomaly))
    (ba/fault)
    (ba/conflict "foo")))

(deftest fail-job-test
  (testing "without message"
    (given (job-util/fail-job {:fhir/type :fhir/Task} (ba/fault))
      job-util/error-msg := "empty error message")))
