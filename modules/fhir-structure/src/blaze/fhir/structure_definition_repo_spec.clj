(ns blaze.fhir.structure-definition-repo-spec
  (:require
   [blaze.anomaly-spec]
   [blaze.fhir.spec.spec]
   [blaze.fhir.structure-definition-repo :as sdr]
   [blaze.fhir.structure-definition-repo.spec]
   [clojure.spec.alpha :as s]))

(s/fdef sdr/resources
  :args (s/cat :repo :blaze.fhir/structure-definition-repo)
  :ret (s/coll-of map?))
