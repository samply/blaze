(ns blaze.fhir.operation.evaluate-measure.cql-spec
  (:require
    [blaze.db.spec]
    [blaze.elm.compiler.library-spec]
    [blaze.elm.expression-spec]
    [blaze.fhir.operation.evaluate-measure.cql :as cql]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [java-time :as time]))


(s/def ::now
  time/offset-date-time?)


(s/def ::library
  :life/compiled-library)


(s/def ::subject-type
  :fhir.resource/type)


(s/def ::context
  (s/keys :req-un [:blaze.db/db ::now ::library ::subject-type
                   :blaze.fhir.operation.evaluate-measure/report-type]))


(s/def ::individual-context
  (s/keys :req-un [:blaze.db/db ::now ::library]))


(s/fdef cql/evaluate-expression
  :args (s/cat :context ::context :name string?)
  :ret (s/or :count nat-int?
             :subject-ids (s/coll-of :blaze.resource/id)
             :anomaly ::anom/anomaly))


(s/fdef cql/evaluate-individual-expression
  :args (s/cat :context ::individual-context
               :subject-handle :blaze.db/resource-handle
               :name string?)
  :ret (s/or :result boolean? :anomaly ::anom/anomaly))


(s/fdef cql/calc-strata
  :args (s/cat :context ::context
               :population-expression-name string?
               :stratum-expression-name string?)
  :ret (s/or :strata (s/map-of any? nat-int?)
             :subject-strata (s/map-of any? (s/coll-of :blaze.resource/id))
             :anomaly ::anom/anomaly))


(s/fdef cql/calc-individual-strata
  :args (s/cat :context ::individual-context
               :subject-handle :blaze.db/resource-handle
               :population-expression-name string?
               :stratum-expression-name string?)
  :ret (s/or :strata (s/map-of any? nat-int?)
             :anomaly ::anom/anomaly))


(s/fdef cql/calc-multi-component-strata
  :args (s/cat :context ::context
               :population-expression-name string?
               :expression-names (s/coll-of string?))
  :ret (s/or :strata (s/map-of (s/coll-of some?) nat-int?)
             :subject-strata (s/map-of (s/coll-of some?) (s/coll-of :blaze.resource/id))
             :anomaly ::anom/anomaly))
