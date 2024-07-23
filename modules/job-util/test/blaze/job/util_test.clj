(ns blaze.job.util-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.db.api :as d]
   [blaze.db.api-stub :refer [mem-node-config with-system-data]]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.structure-definition-repo]
   [blaze.fhir.test-util :refer [given-failed-future]]
   [blaze.job.util :as job-util]
   [blaze.job.util-spec]
   [blaze.module.test-util :as mtu]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def structure-definition-repo
  (:blaze.fhir/structure-definition-repo
   (ig/init {:blaze.fhir/structure-definition-repo {}})))

(defn- codeable-concept [system code]
  (type/codeable-concept
   {:coding
    [(type/coding {:system (type/uri system) :code (type/code code)})]}))

(deftest job-number-test
  (is (= (job-util/job-number
          {:fhir/type :fhir/Task
           :identifier
           [#fhir/Identifier{:system #fhir/uri"https://samply.github.io/blaze/fhir/sid/JobNumber"
                             :value "174731"}]})
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
                     {:system #fhir/uri"https://samply.github.io/blaze/fhir/CodeSystem/JobType"
                      :code #fhir/code"type-140532"}]}})
         :type-140532)))

(deftest status-reason-test
  (is (= (job-util/status-reason
          {:fhir/type :fhir/Task
           :statusReason #fhir/CodeableConcept
                          {:coding
                           [#fhir/Coding
                             {:system #fhir/uri"https://samply.github.io/blaze/fhir/CodeSystem/JobStatusReason"
                              :code #fhir/code"reason-175220"}]}})
         "reason-175220")))

(deftest cancelled-sub-status-test
  (is (= (job-util/cancelled-sub-status
          {:fhir/type :fhir/Task
           :businessStatus #fhir/CodeableConcept
                            {:coding
                             [#fhir/Coding
                               {:system #fhir/uri"https://samply.github.io/blaze/fhir/CodeSystem/JobCancelledSubStatus"
                                :code #fhir/code"sub-status-161316"}]}})
         "sub-status-161316")))

(deftest input-value-test
  (is (= (job-util/input-value
          {:fhir/type :fhir/Task
           :input
           [{:fhir/type :fhir.Task/input
             :type (codeable-concept "foo" "other")
             :value "other"}
            {:fhir/type :fhir.Task/input
             :type (codeable-concept "foo" "bar")
             :value #fhir/code"baz"}]}
          "foo" "bar")
         #fhir/code"baz")))

(deftest output-value-test
  (is (= (job-util/output-value
          {:fhir/type :fhir/Task
           :output
           [(job-util/task-output "foo" "other" "other")
            (job-util/task-output "foo" "bar" #fhir/code"baz")]}
          "foo" "bar")
         #fhir/code"baz"))

  (testing "with default output system"
    (is (= (job-util/output-value
            {:fhir/type :fhir/Task
             :output
             [(job-util/task-output "foo" "other" "other")
              (job-util/task-output "https://samply.github.io/blaze/fhir/CodeSystem/JobOutput" "bar" #fhir/code"baz")]}
            "bar")
           #fhir/code"baz"))))

(deftest error-msg-test
  (is (= (job-util/error-msg
          {:fhir/type :fhir/Task
           :output
           [(job-util/task-output "https://samply.github.io/blaze/fhir/CodeSystem/JobOutput" "error" "msg-175657")]})
         "msg-175657")))

(deftest update-output-value-test
  (is (= (job-util/update-output-value
          {:fhir/type :fhir/Task
           :output
           [(job-util/task-output "foo" "other" "other")
            (job-util/task-output "foo" "bar" #fhir/integer 1)]}
          "foo" "bar"
          (fn [value x]
            (type/integer (+ (type/value value) x)))
          1)
         {:fhir/type :fhir/Task
          :output
          [(job-util/task-output "foo" "other" "other")
           (job-util/task-output "foo" "bar" #fhir/integer 2)]})))

(deftest remove-output-test
  (is (= (job-util/remove-output
          {:fhir/type :fhir/Task
           :output
           [(job-util/task-output "foo" "other" "other")
            (job-util/task-output "foo" "bar" #fhir/integer 1)]}
          "foo" "bar")
         {:fhir/type :fhir/Task
          :output
          [(job-util/task-output "foo" "other" "other")]})))

(defn- start-job [job]
  (assoc job :status #fhir/code"in-progress"))

(deftest pull-job-test
  (with-system-data [{:blaze.db/keys [node]} mem-node-config]
    [[[:put {:fhir/type :fhir/Task :id "0"}]]]

    (given @(mtu/assoc-thread-name (job-util/pull-job node "0"))
      :fhir/type := :fhir/Task
      :id := "0"
      [meta :thread-name] :? mtu/common-pool-thread?)))

(deftest update-job-test
  (with-system-data [{:blaze.db/keys [node]} mem-node-config]
    [[[:put {:fhir/type :fhir/Task :id "0"}]]]

    (let [job @(job-util/pull-job node "0")]

      (testing "start job"
        (let [job @(mtu/assoc-thread-name (job-util/update-job node job start-job))]
          (given job
            :status := #fhir/code"in-progress"
            [meta :thread-name] :? mtu/common-pool-thread?)

          (testing "fail job"
            (given @(job-util/update-job node job job-util/fail-job (ba/fault "msg-181135"))
              :status := #fhir/code"failed"
              job-util/error-msg := "msg-181135"))))))

  (testing "lost updates are detected"
    (with-system-data [{:blaze.db/keys [node]} mem-node-config]
      [[[:put {:fhir/type :fhir/Task :id "0"}]]]

      (let [job @(job-util/pull-job node "0")]

        (testing "start job"
          (given @(job-util/update-job node job start-job)
            :status := #fhir/code"in-progress"))

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
            :status := #fhir/code"in-progress"
            [meta :thread-name] :? mtu/common-pool-thread?))))

    (testing "with one argument"
      (with-system-data [{:blaze.db/keys [node]} mem-node-config]
        [[[:put {:fhir/type :fhir/Task :id "0"}]]]

        (let [job @(job-util/pull-job node "0")]

          (given @(job-util/update-job+ node job nil job-util/fail-job (ba/fault "msg-162452"))
            :status := #fhir/code"failed"
            job-util/error-msg := "msg-162452"))))

    (testing "with two arguments"
      (with-system-data [{:blaze.db/keys [node]} mem-node-config]
        [[[:put {:fhir/type :fhir/Task :id "0"}]]]

        (let [job @(job-util/pull-job node "0")]

          (given @(job-util/update-job+ node job nil (fn [job a b] (assoc job :status (type/code (str a "-" b)))) "on" "hold")
            :status := #fhir/code"on-hold")))))

  (testing "with one Bundle"
    (with-system-data [{:blaze.db/keys [node]} mem-node-config]
      [[[:put {:fhir/type :fhir/Task :id "0"}]]]

      (let [job @(job-util/pull-job node "0")]

        (given @(job-util/update-job+ node job [{:fhir/type :fhir/Bundle :id "0"}]
                                      job-util/fail-job (ba/fault "msg-181135"))
          :status := #fhir/code"failed"
          job-util/error-msg := "msg-181135")

        (is (some? (d/resource-handle (d/db node) "Bundle" "0")))))))

(deftest job-update-failed-test
  (are [anomaly] (false? (job-util/job-update-failed? anomaly))
    (ba/fault)
    (ba/conflict "foo")))

(deftest fail-job-test
  (testing "without message"
    (given (job-util/fail-job {:fhir/type :fhir/Task} (ba/fault))
      #(job-util/output-value % "error") := "empty error message")))
