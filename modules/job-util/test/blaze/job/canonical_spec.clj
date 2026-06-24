(ns blaze.job.canonical-spec
  (:require
   [blaze.fhir.spec]
   [blaze.job.canonical :as jc]
   [clojure.spec.alpha :as s]))

(s/fdef jc/canonicalize
  :args (s/cat :job :fhir/Task)
  :ret :fhir/Task)
