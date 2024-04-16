(ns blaze.job-scheduler.job-util-spec
  (:require
   [blaze.db.spec]
   [blaze.fhir.spec]
   [blaze.job-scheduler.job-util :as job-util]
   [blaze.job-scheduler.spec]
   [clojure.spec.alpha :as s]))

(s/fdef job-util/input-value
  :args (s/cat :job :blaze/resource :system string? :code string?)
  :ret any?)

(s/fdef job-util/output-value
  :args (s/cat :job :blaze/resource :system (s/? string?) :code string?)
  :ret any?)

(s/fdef job-util/update-output-value
  :args (s/cat :job :blaze/resource :system string? :code string? :f fn? :x any?)
  :ret any?)

(s/fdef job-util/update-job
  :args (s/cat :node :blaze.db/node :job :blaze/resource :f fn? :args (s/* any?))
  :ret :blaze/resource)
