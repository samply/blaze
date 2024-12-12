(ns blaze.terminology-service.local.code-system-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.spec]
   [blaze.fhir.spec.spec]
   [blaze.terminology-service.local.code-system :as cs]
   [blaze.terminology-service.local.code-system.sct.context-spec]
   [blaze.terminology-service.local.code-system.spec]
   [blaze.terminology-service.local.priority-spec]
   [clojure.spec.alpha :as s]))

(s/fdef cs/list
  :args (s/cat :db :blaze.db/db)
  :ret ac/completable-future?)

(s/fdef cs/find
  :args (s/cat :context ::cs/find-context :url string? :version (s/? string?))
  :ret ac/completable-future?)

(s/fdef cs/validate-code
  :args (s/cat :code-system :fhir/CodeSystem :context map?)
  :ret :fhir/Parameters)

(s/fdef cs/expand-filter
  :args (s/cat :code-system :fhir/CodeSystem
               :filters (s/coll-of :fhir.ValueSet.compose.include/filter))
  :ret (s/coll-of :fhir.ValueSet.expansion/contains))
