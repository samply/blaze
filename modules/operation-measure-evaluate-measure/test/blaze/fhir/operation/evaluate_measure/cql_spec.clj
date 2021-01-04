(ns blaze.fhir.operation.evaluate-measure.cql-spec
  (:require
    [blaze.db.spec]
    [blaze.elm.compiler-spec]
    [blaze.elm.expression-spec]
    [blaze.fhir.operation.evaluate-measure.cql :as cql]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom])
  (:import
    [java.time OffsetDateTime]))


(s/def ::now
  #(instance? OffsetDateTime %))


(s/def ::library
  :life/compiled-library)


(s/def ::subject-type
  string?)


(s/def ::context
  (s/keys :req-un [:blaze.db/db ::now ::library ::subject-type
                   :blaze.fhir.operation.evaluate-measure/report-type]))


(s/fdef cql/evaluate-expression
  :args (s/cat :context ::context :name string?)
  :ret (s/or :count nat-int?
             :subject-ids (s/coll-of :blaze.resource/id)
             :anomaly ::anom/anomaly))


(s/fdef cql/calc-strata
  :args (s/cat :context ::context
               :population-expression-name string?
               :stratum-expression-name string?)
  :ret (s/or :strata (s/map-of any? nat-int?)
             :subject-strata (s/map-of any? (s/coll-of :blaze.resource/id))
             :anomaly ::anom/anomaly))


(s/fdef cql/calc-mult-component-strata
  :args (s/cat :context ::context
               :population-expression-name string?
               :expression-names (s/coll-of string?))
  :ret (s/or :strata (s/map-of (s/coll-of some?) nat-int?)
             :subject-strata (s/map-of (s/coll-of some?) (s/coll-of :blaze.resource/id))
             :anomaly ::anom/anomaly))
