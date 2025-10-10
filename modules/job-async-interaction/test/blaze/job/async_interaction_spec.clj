(ns blaze.job.async-interaction-spec
  (:require
   [blaze.db.tx-log.spec]
   [blaze.fhir.spec.spec]
   [blaze.job.async-interaction :as job-async]
   [blaze.module-spec]
   [blaze.util-spec]
   [clojure.spec.alpha :as s]))

(s/fdef job-async/job
  :args (s/cat :authored-on :system/date-time
               :bundle-id :blaze.resource/id
               :t :blaze.db/t)
  :ret :fhir/Task)

(s/fdef job-async/t
  :args (s/cat :job :fhir/Task)
  :ret (s/nilable :blaze.db/t))

(s/fdef job-async/response-bundle-ref
  :args (s/cat :job :fhir/Task)
  :ret (s/nilable :blaze.fhir/literal-ref))
