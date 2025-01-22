(ns blaze.terminology-service.local.value-set.validate-code.issue-spec
  (:require
   [blaze.fhir.spec]
   [blaze.terminology-service.local.validate-code :as-alias vc]
   [blaze.terminology-service.local.validate-code.spec]
   [blaze.terminology-service.local.value-set.validate-code.issue :as issue]
   [blaze.terminology-service.local.value-set.validate-code.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef issue/not-in-vs
  :args (s/cat :value-set :fhir/ValueSet :clause ::vc/clause)
  :ret :fhir.OperationOutcome/issue)

(s/fdef issue/invalid-code
  :args (s/cat :clause ::vc/clause)
  :ret :fhir.OperationOutcome/issue)

(s/fdef issue/invalid-display
  :args (s/cat :clause ::vc/clause :concept map?
               :lenient-display-validation (s/nilable boolean?))
  :ret :fhir.OperationOutcome/issue)

(s/fdef issue/cannot-infer
  :args (s/cat :code-system :fhir/CodeSystem :clause ::vc/clause)
  :ret :fhir.OperationOutcome/issue)

(s/fdef issue/inactive-code
  :args (s/cat :clause ::vc/clause)
  :ret :fhir.OperationOutcome/issue)

(s/fdef issue/value-set-not-found
  :args (s/cat :url string?)
  :ret :fhir.OperationOutcome/issue)

(s/fdef issue/code-system-not-found
  :args (s/cat :clause ::vc/clause)
  :ret :fhir.OperationOutcome/issue)

(s/fdef issue/unknown-value-set
  :args (s/cat :value-set :fhir/ValueSet :unknown-value-set-url string?)
  :ret :fhir.OperationOutcome/issue)

(s/fdef issue/unknown-code-system
  :args (s/cat :value-set :fhir/ValueSet :clause ::vc/clause)
  :ret :fhir.OperationOutcome/issue)

(s/fdef issue/invalid-value-set
  :args (s/cat :value-set :fhir/ValueSet :anomaly ::anom/anomaly)
  :ret :fhir.OperationOutcome/issue)
