(ns blaze.fhir.operation.evaluate-measure.measure.population-spec
  (:require
    [blaze.fhir.operation.evaluate-measure.cql-spec :as cql-spec]
    [blaze.fhir.operation.evaluate-measure.measure.population :as population]
    [blaze.fhir.spec.spec]
    [clojure.spec.alpha :as s]))


(s/def ::subject-type
  :fhir.resource/type)


(s/def ::subject-handle
  :blaze.db/resource-handle)


(s/def ::context
  (s/merge (s/keys :req-un [(or ::subject-type ::subject-handle)])
           ::cql-spec/context))


(s/fdef population/evaluate
  :args (s/cat :context ::context :idx nat-int? :population map?))
