(ns blaze.job-scheduler.task-util-spec
  (:require
   [blaze.db.spec]
   [blaze.fhir.spec]
   [blaze.job-scheduler.spec]
   [blaze.job-scheduler.task-util :as task-util]
   [clojure.spec.alpha :as s]))

(s/fdef task-util/input-value
  :args (s/cat :system string? :code string? :task :blaze/resource)
  :ret any?)
