(ns blaze.terminology-service.local.code-system-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.spec]
   [blaze.fhir.spec.spec]
   [blaze.terminology-service.code-system-validate-code :as-alias cs-validate-code]
   [blaze.terminology-service.local.code-system :as cs]
   [blaze.terminology-service.local.code-system.sct.context-spec]
   [blaze.terminology-service.local.code-system.spec]
   [blaze.terminology-service.local.priority-spec]
   [blaze.terminology-service.spec]
   [clojure.spec.alpha :as s]))

(s/fdef cs/list
  :args (s/cat :db :blaze.db/db)
  :ret ac/completable-future?)

(s/fdef cs/find
  :args (s/cat :context ::cs/find-context :url string? :version (s/? string?))
  :ret ac/completable-future?)

(s/fdef cs/enhance
  :args (s/cat :context map? :code-system :fhir/CodeSystem)
  :ret :fhir/CodeSystem)

(s/fdef cs/validate-code
  :args (s/cat :code-system :fhir/CodeSystem :request ::cs-validate-code/request)
  :ret :fhir/Parameters)

(s/fdef cs/expand-complete
  :args (s/cat :request ::cs/expand-request :code-system :fhir/CodeSystem)
  :ret (s/coll-of :fhir.ValueSet.expansion/contains))

(s/fdef cs/expand-concept
  :args (s/cat :request ::cs/expand-request
               :code-system :fhir/CodeSystem
               :concepts (s/coll-of :fhir.ValueSet.compose.include/concept))
  :ret (s/coll-of :fhir.ValueSet.expansion/contains))

(s/fdef cs/expand-filter
  :args (s/cat :request ::cs/expand-request
               :code-system :fhir/CodeSystem
               :filter :fhir.ValueSet.compose.include/filter)
  :ret (s/coll-of :fhir.ValueSet.expansion/contains :kind set?))
