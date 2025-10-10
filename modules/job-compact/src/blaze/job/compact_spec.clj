(ns blaze.job.compact-spec
  (:require
   [blaze.db.tx-log.spec]
   [blaze.fhir.spec.spec]
   [blaze.job.compact :as job-compact]
   [blaze.util-spec]
   [clojure.spec.alpha :as s]))

(s/fdef job-compact/job
  :args (s/cat :authored-on :system/date-time
               :database string?
               :column-family string?)
  :ret :fhir/Task)
