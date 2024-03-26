(ns blaze.job-scheduler.task-util-test
  (:require
   [blaze.job-scheduler.task-util :as task-util]
   [blaze.job-scheduler.task-util-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest input-value-test
  (is (= (task-util/input-value
          "foo" "bar"
          {:fhir/type :fhir/Task
           :input
           [{:fhir/type :fhir.Task/input
             :type #fhir/CodeableConcept{:coding [#fhir/Coding{:system #fhir/uri"foo" :code #fhir/code"other"}]}
             :value "other"}
            {:fhir/type :fhir.Task/input
             :type #fhir/CodeableConcept{:coding [#fhir/Coding{:system #fhir/uri"foo" :code #fhir/code"bar"}]}
             :value #fhir/code"baz"}]})
         "baz")))
