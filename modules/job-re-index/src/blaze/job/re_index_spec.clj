(ns blaze.job.re-index-spec
  (:require
   [blaze.fhir.spec.spec]
   [blaze.job.re-index :as re-index]
   [blaze.job.re-index.spec]
   [blaze.util-spec]
   [clojure.spec.alpha :as s]))

(s/fdef re-index/job
  :args (s/cat :authored-on :system/date-time :params ::re-index/params)
  :ret :fhir/Task)
