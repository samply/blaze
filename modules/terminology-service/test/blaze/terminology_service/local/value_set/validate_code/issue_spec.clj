(ns blaze.terminology-service.local.value-set.validate-code.issue-spec
  (:require
   [blaze.fhir.spec]
   [blaze.terminology-service.local.value-set.validate-code :as-alias vs-validate-code]
   [blaze.terminology-service.local.value-set.validate-code.issue :as issue]
   [blaze.terminology-service.local.value-set.validate-code.spec]
   [clojure.spec.alpha :as s]))

(s/fdef issue/not-in-vs
  :args (s/cat :value-set :fhir/ValueSet :clause ::vs-validate-code/clause)
  :ret :fhir.OperationOutcome/issue)

(s/fdef issue/invalid-code
  :args (s/cat :clause ::vs-validate-code/clause)
  :ret :fhir.OperationOutcome/issue)

(s/fdef issue/invalid-display
  :args (s/cat :clause ::vs-validate-code/clause :concept map?
               :lenient-display-validation (s/nilable boolean?))
  :ret :fhir.OperationOutcome/issue)

(s/fdef issue/cannot-infer
  :args (s/cat :code-system :fhir/CodeSystem :clause ::vs-validate-code/clause)
  :ret :fhir.OperationOutcome/issue)

(s/fdef issue/inactive-code
  :args (s/cat :clause ::vs-validate-code/clause)
  :ret :fhir.OperationOutcome/issue)

(s/fdef issue/value-set-not-found
  :args (s/cat :url string?)
  :ret :fhir.OperationOutcome/issue)

(s/fdef issue/code-system-not-found
  :args (s/cat :clause ::vs-validate-code/clause)
  :ret :fhir.OperationOutcome/issue)

(s/fdef issue/unknown-value-set
  :args (s/cat :value-set :fhir/ValueSet :unknown-value-set-url string?)
  :ret :fhir.OperationOutcome/issue)
