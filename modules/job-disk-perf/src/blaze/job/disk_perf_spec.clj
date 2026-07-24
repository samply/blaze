(ns blaze.job.disk-perf-spec
  (:require
   [blaze.db.tx-log.spec]
   [blaze.fhir.spec.spec]
   [blaze.job.disk-perf :as job-disk-perf]
   [blaze.job.disk-perf.spec]
   [blaze.util-spec]
   [clojure.spec.alpha :as s]))

(s/fdef job-disk-perf/job
  :args (s/cat :authored-on :system/date-time :params ::job-disk-perf/params)
  :ret :fhir/Task)
