(ns blaze.fhir.operation.evaluate-measure.measure.stratifier.spec
  (:require
   [blaze.fhir.operation.evaluate-measure :as-alias evaluate-measure]
   [blaze.fhir.operation.evaluate-measure.cql :as-alias cql]
   [blaze.fhir.operation.evaluate-measure.cql.spec]
   [blaze.fhir.operation.evaluate-measure.measure :as-alias measure]
   [blaze.fhir.operation.evaluate-measure.measure.stratifier :as-alias strat]
   [blaze.fhir.operation.evaluate-measure.spec]
   [clojure.spec.alpha :as s]))

(s/def ::strat/handles
  (s/coll-of ::measure/handles))

(s/def ::strat/evaluated-populations
  (s/keys :req-un [::strat/handles]))

(s/def ::strat/context
  (s/merge ::cql/context
           (s/keys :req-un [::evaluate-measure/executor ::measure/report-type])))
