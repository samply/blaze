(ns blaze.validator.extern.impl-spec
  (:require
   [blaze.fhir.spec]
   [blaze.validator.extern.impl :as impl]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef impl/invalid?
  :args (s/cat :operation-outcome :fhir/OperationOutcome)
  :ret boolean?)

(s/fdef impl/tag-invalid
  :args (s/cat :resource :fhir/Resource
               :operation-outcome :fhir/OperationOutcome
               :contain-outcome? boolean?)
  :ret :fhir/Resource)

(s/fdef impl/reject-anomaly
  :args (s/cat :operation-outcome :fhir/OperationOutcome)
  :ret ::anom/anomaly)
