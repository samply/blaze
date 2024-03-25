(ns blaze.job-scheduler-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.coll.spec :as cs]
   [blaze.db.spec]
   [blaze.fhir.spec]
   [blaze.job-scheduler :as js]
   [blaze.job-scheduler.spec]
   [clojure.spec.alpha :as s]))

(s/fdef js/submit
  :args (s/cat :scheduler :blaze/job-scheduler :task :blaze/resource)
  :ret ac/completable-future?)

(s/fdef js/tasks-by-status
  :args (s/cat :db :blaze.db/db :status #{"ready" "in-progress" "completed" "failed"})
  :ret (cs/coll-of :blaze.db/resource-handle))
