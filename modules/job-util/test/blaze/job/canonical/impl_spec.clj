(ns blaze.job.canonical.impl-spec
  (:require
   [blaze.fhir.spec]
   [blaze.job.canonical.impl :as impl]
   [clojure.spec.alpha :as s]))

(s/fdef impl/upgrade
  :args (s/cat :job :fhir/Task)
  :ret :fhir/Task)

(s/fdef impl/downgrade
  :args (s/cat :job :fhir/Task)
  :ret :fhir/Task)
