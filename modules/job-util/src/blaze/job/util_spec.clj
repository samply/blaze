(ns blaze.job.util-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.spec]
   [blaze.fhir.spec]
   [blaze.job.util :as job-util]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef job-util/job-number
  :args (s/cat :job :fhir/Task)
  :ret string?)

(s/fdef job-util/job-type
  :args (s/cat :job :fhir/Task)
  :ret simple-keyword?)

(s/fdef job-util/status-reason
  :args (s/cat :job :fhir/Task)
  :ret string?)

(s/fdef job-util/cancelled-sub-status
  :args (s/cat :job :fhir/Task)
  :ret string?)

(s/fdef job-util/input-value
  :args (s/cat :job :fhir/Task :system string? :code string?)
  :ret any?)

(s/fdef job-util/output-value
  :args (s/cat :job :fhir/Task :system (s/? string?) :code string?)
  :ret any?)

(s/fdef job-util/error-category
  :args (s/cat :job :fhir/Task)
  :ret (s/nilable ::anom/category))

(s/fdef job-util/error-msg
  :args (s/cat :job :fhir/Task)
  :ret (s/nilable string?))

(s/fdef job-util/error
  :args (s/cat :job :fhir/Task)
  :ret (s/nilable ::anom/anomaly))

(s/fdef job-util/update-output-value
  :args (s/cat :job :fhir/Task :system string? :code string? :f fn? :x any?)
  :ret any?)

(s/fdef job-util/task-output
  :args (s/cat :system string? :code string? :value any?)
  :ret :fhir.Task/output)

(s/fdef job-util/add-output
  :args (s/cat :job :fhir/Task :system (s/? string?) :code string? :value any?)
  :ret :fhir/Task)

(s/fdef job-util/pull-job
  :args (s/cat :node :blaze.db/node :db (s/? :blaze.db/db) :id :blaze.resource/id)
  :ret ac/completable-future?)

(s/fdef job-util/update-job+
  :args (s/cat :node :blaze.db/node :job :fhir/Task
               :other-resources (s/nilable (s/coll-of :fhir/Resource))
               :f fn? :args (s/* any?))
  :ret ac/completable-future?)

(s/fdef job-util/update-job
  :args (s/cat :node :blaze.db/node :job :fhir/Task :f fn? :args (s/* any?))
  :ret ac/completable-future?)

(s/fdef job-util/job-update-failed?
  :args (s/cat :anomaly ::anom/anomaly)
  :ret boolean?)
