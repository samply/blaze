(ns blaze.job-scheduler-test
  (:require
   [blaze.db.api :as d]
   [blaze.db.api-spec]
   [blaze.db.api-stub :as api-stub]
   [blaze.job-scheduler :as js]
   [blaze.job-scheduler-spec]
   [blaze.log]
   [blaze.module.test-util :refer [with-system]]
   [blaze.scheduler]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-level! :trace)

(test/use-fixtures :each tu/fixture)

(def config
  (assoc api-stub/mem-node-config
         :blaze/job-scheduler
         {:node (ig/ref :blaze.db/node)
          :scheduler (ig/ref :blaze/scheduler)}
         :blaze/scheduler {}))

(def code
  #fhir/CodeableConcept
   {:coding
    [#fhir/Coding
      {:system #fhir/uri"http://loinc.org"
       :code #fhir/code"test"}]})

(defmethod js/execute-job :test
  [_node {:keys [id] :as task}]
  (log/debug "start executing job with id =" id)
  (Thread/sleep 200)
  (log/debug "finished executing job with id =" id)
  task)

(deftest submit-test
  (with-system [{scheduler :blaze/job-scheduler :blaze.db/keys [node]} config]

    (testing "the job is created as ready"
      (given @(js/submit scheduler {:fhir/type :fhir/Task :id "0"
                                    :code code})
        :fhir/type := :fhir/Task
        :id := "0"
        [meta :blaze.db/num-changes] := 1
        [meta :blaze.db/op] := :create
        :status := #fhir/code"ready"
        :code := code))

    (Thread/sleep 100)

    (testing "the job is in-progress"
      (given @(d/pull-many node (js/tasks-by-status (d/db node) "in-progress"))
        count := 1
        [0 :fhir/type] := :fhir/Task
        [0 :id] := "0"
        [0 meta :blaze.db/num-changes] := 2
        [0 meta :blaze.db/op] := :put
        [0 :status] := #fhir/code"in-progress"
        [0 :code] := code))

    (Thread/sleep 200)

    (testing "the job is completed"
      (given @(d/pull-many node (js/tasks-by-status (d/db node) "completed"))
        count := 1
        [0 :fhir/type] := :fhir/Task
        [0 :id] := "0"
        [0 meta :blaze.db/num-changes] := 3
        [0 meta :blaze.db/op] := :put
        [0 :status] := #fhir/code"completed"
        [0 :code] := code))))
