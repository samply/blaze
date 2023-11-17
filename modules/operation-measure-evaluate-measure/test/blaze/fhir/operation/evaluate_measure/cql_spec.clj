(ns blaze.fhir.operation.evaluate-measure.cql-spec
  (:require
   [blaze.db.spec]
   [blaze.elm.compiler.external-data :as ed]
   [blaze.elm.compiler.external-data-spec]
   [blaze.elm.compiler.library-spec]
   [blaze.elm.expression-spec]
   [blaze.fhir.operation.evaluate-measure.cql :as cql]
   [blaze.fhir.operation.evaluate-measure.cql.spec]
   [blaze.fhir.operation.evaluate-measure.measure :as-alias measure]
   [blaze.fhir.operation.evaluate-measure.measure.spec]
   [blaze.fhir.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef cql/evaluate-expression
  :args (s/cat :context ::cql/evaluate-expression-context :name string?
               :subject-type :fhir.resource/type :population-basis
               (s/alt :subject-based #{:boolean} :other :fhir.resource/type))
  :ret (s/or :handles ::measure/handles
             :count int?
             :anomaly ::anom/anomaly))

(s/fdef cql/evaluate-individual-expression
  :args (s/cat :context ::cql/evaluate-individual-expression-context
               :subject ed/resource?
               :name string?)
  :ret (s/or :value any?
             :anomaly ::anom/anomaly))

(s/fdef cql/calc-strata
  :args (s/cat :context ::cql/context
               :expression-name string?
               :handles ::measure/handles)
  :ret (s/or :strata (s/map-of any? ::measure/handles)
             :anomaly ::anom/anomaly))

(s/fdef cql/calc-function-strata
  :args (s/cat :context ::cql/context
               :function-name string?
               :handles ::measure/handles)
  :ret (s/or :strata (s/map-of any? ::measure/handles)
             :anomaly ::anom/anomaly))

(s/fdef cql/calc-multi-component-strata
  :args (s/cat :context ::cql/context
               :expression-names (s/coll-of string?)
               :handles ::measure/handles)
  :ret (s/or :strata (s/map-of (s/coll-of any?) ::measure/handles)
             :anomaly ::anom/anomaly))
