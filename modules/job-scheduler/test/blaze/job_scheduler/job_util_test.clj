(ns blaze.job-scheduler.job-util-test
  (:require
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.structure-definition-repo]
   [blaze.job-scheduler.job-util :as job-util]
   [blaze.job-scheduler.job-util-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is]]
   [integrant.core :as ig]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-level! :trace)

(test/use-fixtures :each tu/fixture)

(def structure-definition-repo
  (:blaze.fhir/structure-definition-repo
   (ig/init {:blaze.fhir/structure-definition-repo {}})))

(defn- codeable-concept [system code]
  (type/map->CodeableConcept
   {:coding
    [(type/map->Coding {:system (type/uri system) :code (type/code code)})]}))

(deftest code-value-test
  (is (= "bar" (job-util/code-value "foo" (codeable-concept "foo" "bar"))))
  (is (nil? (job-util/code-value "foo" (codeable-concept "bar" "baz")))))

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
         #fhir/code"baz")))

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
