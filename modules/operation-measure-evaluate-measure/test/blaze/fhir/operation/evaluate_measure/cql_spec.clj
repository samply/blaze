(ns blaze.fhir.operation.evaluate-measure.cql-spec
  (:require
    [blaze.async.comp :as ac]
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
    [clojure.spec.alpha :as s]))


(s/fdef cql/evaluate-expression
  :args (s/cat :context ::cql/evaluate-expression-context :name string?
               :subject-type :fhir.resource/type)
  :ret ac/completable-future?)


(s/fdef cql/evaluate-individual-expression
  :args (s/cat :context ::cql/evaluate-expression-context
               :subject ed/resource?
               :name string?)
  :ret ac/completable-future?)


(s/fdef cql/calc-strata
  :args (s/cat :context ::cql/context
               :expression-name string?
               :handles ::measure/handles)
  :ret ac/completable-future?)


(s/fdef cql/calc-function-strata
  :args (s/cat :context ::cql/context
               :function-name string?
               :handles ::measure/handles)
  :ret ac/completable-future?)


(s/fdef cql/calc-multi-component-strata
  :args (s/cat :context ::cql/context
               :expression-names (s/coll-of string?)
               :handles ::measure/handles)
  :ret ac/completable-future?)
