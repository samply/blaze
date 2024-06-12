(ns blaze.fhir.operation.evaluate-measure.cql-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.elm.compiler :as-alias c]
   [blaze.elm.compiler.spec]
   [blaze.elm.resource :as cr]
   [blaze.fhir.operation.evaluate-measure.cql :as cql]
   [blaze.fhir.operation.evaluate-measure.cql.spec]
   [blaze.fhir.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef cql/evaluate-expression-1
  :args (s/cat :context ::cql/context
               :subject (s/nilable cr/resource?)
               :name string?
               :expression ::c/expression)
  :ret (s/or :result any? :anomaly ::anom/anomaly))

(s/fdef cql/evaluate-expression
  :args (s/cat :context ::cql/evaluate-expression-context :name string?
               :subject-type :fhir.resource/type)
  :ret ac/completable-future?)

(s/fdef cql/evaluate-individual-expression
  :args (s/cat :context ::cql/evaluate-expression-context
               :subject cr/resource?
               :name string?)
  :ret ac/completable-future?)

(s/fdef cql/stratum-expression-evaluator
  :args (s/cat :context ::cql/stratum-expression-evaluator-context
               :name string?)
  :ret (s/or :evaluator fn? :anomaly ::anom/anomaly))

(s/fdef cql/stratum-expression-evaluators
  :args (s/cat :context ::cql/stratum-expression-evaluator-context
               :name (s/coll-of string?))
  :ret (s/or :evaluators (s/coll-of fn?) :anomaly ::anom/anomaly))
