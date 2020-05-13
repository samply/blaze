(ns blaze.elm.compiler-spec
  (:require
    [blaze.elm.compiler :as compiler]
    [blaze.elm.compiler.protocols :refer [expr?]]
    [blaze.elm.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]))


(set! *warn-on-reflection* true)


(s/def :life/expression
  expr?)


(s/def ::compile-context
  (s/keys :req-un [:elm/library :blaze.db/node]))


(s/fdef compiler/compile
  :args (s/cat :context ::compile-context :expression :elm/expression)
  :ret expr?)


(s/def :life/compiled-expression-defs
  (s/map-of :elm/name expr?))


(s/def :life/compiled-library
  (s/keys :req [:life/compiled-expression-defs]))


(s/fdef compiler/compile-library
  :args (s/cat :node :blaze.db/node :library :elm/library :opts map?)
  :ret (s/or :library :life/compiled-library :anomaly ::anom/anomaly))
