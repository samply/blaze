(ns blaze.fhir.operation.evaluate-measure.cql.spec
  (:require
   [blaze.elm.compiler :as-alias c]
   [blaze.elm.compiler.library.spec]
   [blaze.elm.expression :as-alias expr]
   [blaze.elm.expression.spec]
   [blaze.elm.resource :as cr]
   [blaze.fhir.operation.evaluate-measure :as-alias evaluate-measure]
   [blaze.fhir.operation.evaluate-measure.cql :as-alias cql]
   [blaze.fhir.operation.evaluate-measure.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/def ::cql/interrupted?
  (s/fspec :args (s/cat) :ret (s/nilable ::anom/anomaly)))

(s/def ::cql/context
  (s/merge
   ::expr/context
   (s/keys :req-un [::cql/interrupted? ::c/expression-defs])))

(s/def ::cql/reduce-op
  fn?)

(s/def ::cql/combine-op
  fn?)

(s/def ::cql/population-basis
  (s/nilable :fhir.resource/type))

(s/def ::cql/evaluate-expression-context
  (s/merge
   ::cql/context
   (s/keys :req-un [::evaluate-measure/executor ::cql/reduce-op ::cql/combine-op]
           :opt-un [::cql/population-basis])))

(s/def ::cql/subject-handle
  cr/resource?)

(s/def ::cql/population-handle
  cr/resource?)

(s/def ::cql/handle
  (s/keys :req-un [::cql/subject-handle ::cql/population-handle]))

(s/def ::cql/stratum-expression-evaluator-context
  (s/keys :opt-un [::c/expression-defs ::c/function-defs
                   ::cql/population-basis]))
