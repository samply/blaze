(ns blaze.elm.compiler.library-spec
  (:require
    [blaze.anomaly-spec]
    [blaze.elm.compiler-spec]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.library :as library]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]))


(s/def ::compiled-expression-defs
  (s/map-of :elm/name core/expr?))


(s/def ::parameter-default-values
  (s/map-of :elm/name core/expr?))


(s/def :life/compiled-library
  (s/keys :req-un [::compiled-expression-defs ::parameter-default-values]))


(s/fdef library/compile-library
  :args (s/cat :node :blaze.db/node :library :elm/library :opts map?)
  :ret (s/or :library :life/compiled-library :anomaly ::anom/anomaly))
