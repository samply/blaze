(ns blaze.job.re-index
  (:require
   [blaze.db.api :as d]
   [blaze.job-scheduler :as js]
   [blaze.job-scheduler.task-util :as task-util]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private parameter-system
  "https://samply.github.io/blaze/fhir/CodeSystem/ReIndexJobParameter")

(defmethod js/execute-job :re-index
  [node {:keys [id] :as task}]
  (log/debug "start executing job with id =" id)
  (d/re-index (d/db node) (task-util/input-value parameter-system "search-param-url" task))
  (log/debug "finished executing job with id =" id)
  task)
