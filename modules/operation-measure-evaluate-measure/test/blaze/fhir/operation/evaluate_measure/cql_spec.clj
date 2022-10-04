(ns blaze.fhir.operation.evaluate-measure.cql-spec
  (:require
    [blaze.db.spec]
    [blaze.elm.compiler :as-alias compiler]
    [blaze.elm.compiler.library-spec]
    [blaze.elm.expression-spec]
    [blaze.fhir.operation.evaluate-measure.cql :as cql]
    [blaze.fhir.operation.evaluate-measure.measure :as-alias measure]
    [blaze.fhir.operation.evaluate-measure.measure.spec]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [java-time.api :as time]))


(s/def ::now
  time/offset-date-time?)


(s/def ::parameters
  (s/map-of string? any?))


(s/def ::context
  (s/keys :req-un [:blaze.db/db ::now ::compiler/expression-defs]))


(s/def ::individual-context
  (s/keys :req-un [:blaze.db/db ::now ::compiler/expression-defs] :opt-un [::parameters]))


(s/fdef cql/evaluate-expression
  :args (s/cat :context ::context :name string? :subject-type :fhir.resource/type
               :population-basis (s/alt :subject-based #{:boolean} :other :fhir.resource/type))
  :ret (s/or :handles ::measure/handles
             :anomaly ::anom/anomaly))


(s/fdef cql/evaluate-individual-expression
  :args (s/cat :context ::individual-context
               :subject-handle :blaze.db/resource-handle
               :name string?)
  :ret (s/or :value any?
             :anomaly ::anom/anomaly))


(s/fdef cql/calc-strata
  :args (s/cat :context ::context
               :expression-name string?
               :handles ::measure/handles)
  :ret (s/or :strata (s/map-of any? ::measure/handles)
             :anomaly ::anom/anomaly))


(s/fdef cql/calc-function-strata
  :args (s/cat :context ::context
               :function-name string?
               :handles ::measure/handles)
  :ret (s/or :strata (s/map-of any? ::measure/handles)
             :anomaly ::anom/anomaly))


(s/fdef cql/calc-multi-component-strata
  :args (s/cat :context ::context
               :expression-names (s/coll-of string?)
               :handles ::measure/handles)
  :ret (s/or :strata (s/map-of (s/coll-of any?) ::measure/handles)
             :anomaly ::anom/anomaly))
