(ns blaze.fhir.operation.evaluate-measure.measure.stratifier-spec
  (:require
   [blaze.db.spec]
   [blaze.fhir.operation.evaluate-measure.cql :as-alias cql]
   [blaze.fhir.operation.evaluate-measure.cql-spec]
   [blaze.fhir.operation.evaluate-measure.cql.spec]
   [blaze.fhir.operation.evaluate-measure.measure.spec]
   [blaze.fhir.operation.evaluate-measure.measure.stratifier :as strat]
   [blaze.fhir.operation.evaluate-measure.measure.stratifier.spec]
   [blaze.fhir.operation.evaluate-measure.measure.util-spec]
   [blaze.fhir.spec.spec]
   [blaze.luid :as-alias luid]
   [blaze.luid.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef strat/stratum-subject-list
  :args (s/cat :context (s/keys :req [::luid/generator] :req-un [:blaze.db/tx-ops])
               :value some?
               :populations (s/map-of :fhir/CodeableConcept (s/coll-of ::cql/handle)))
  :ret map?)

(s/fdef strat/multi-component-stratum-subject-list
  :args (s/cat :codes (s/coll-of :fhir/CodeableConcept)
               :context (s/keys :req [::luid/generator] :req-un [:blaze.db/tx-ops])
               :values vector?
               :populations (s/map-of :fhir/CodeableConcept (s/coll-of ::cql/handle)))
  :ret map?)

(s/fdef strat/reduce-op
  :args (s/cat :context ::strat/context
               :stratifier :fhir.Measure.group/stratifier)
  :ret (s/or :reduce-op fn? :anomaly ::anom/anomaly))
