(ns blaze.elm.compiler-spec
  (:require
    [blaze.elm.compiler :as compiler]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.spec]
    [blaze.elm.spec]
    [blaze.fhir.spec-spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]))


(set! *warn-on-reflection* true)


(s/fdef compiler/compile
  :args (s/cat :context :elm/compile-context :expression :elm/expression)
  :ret core/expr?)


(s/def :life/compiled-expression-defs
  (s/map-of :elm/name core/expr?))


(s/def :life/compiled-library
  (s/keys :req [:life/compiled-expression-defs]))


(s/fdef compiler/compile-library
  :args (s/cat :node :blaze.db/node :library :elm/library :opts map?)
  :ret (s/or :library :life/compiled-library :anomaly ::anom/anomaly))
