(ns blaze.terminology-service.local.value-set.expand-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir.spec.spec]
   [blaze.terminology-service.local.value-set.expand :as vs-expand]
   [blaze.terminology-service.local.value-set.expand.spec]
   [clojure.spec.alpha :as s]))

(s/fdef vs-expand/expand-value-set
  :args (s/cat :context ::vs-expand/context :value-set :fhir/ValueSet)
  :ret ac/completable-future?)
