(ns blaze.fhir.operation.evaluate-measure.measure.population.spec
  (:require
   [blaze.db.spec]
   [blaze.fhir.operation.evaluate-measure.cql :as-alias cql]
   [blaze.fhir.operation.evaluate-measure.cql.spec]
   [blaze.fhir.operation.evaluate-measure.measure.population :as-alias population]
   [blaze.fhir.spec.spec]
   [blaze.luid :as-alias luid]
   [blaze.luid.spec]
   [clojure.spec.alpha :as s]))

(s/def ::population/subject-type
  :fhir.resource/type)

(s/def ::population/context
  (s/merge
   ::cql/context
   (s/keys :req [::luid/generator]
           :req-un [(or ::population/subject-type ::cql/subject-handle)])))
