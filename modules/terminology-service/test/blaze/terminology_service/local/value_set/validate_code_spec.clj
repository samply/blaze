(ns blaze.terminology-service.local.value-set.validate-code-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir.spec.spec]
   [blaze.terminology-service.local.value-set.validate-code :as vs-validate-code]
   [clojure.spec.alpha :as s]))

(s/fdef vs-validate-code/validate-code
  :args (s/cat :context map? :code-system :fhir/ValueSet)
  :ret ac/completable-future?)
