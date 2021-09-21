(ns blaze.elm.compiler.library-spec
  (:require
    [blaze.elm.compiler-spec]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.library :as library]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]))


(s/def :life/compiled-expression-defs
  (s/map-of :elm/name core/expr?))


(s/def :life/compiled-library
  (s/keys :req [:life/compiled-expression-defs]))


(s/fdef library/compile-library
  :args (s/cat :node :blaze.db/node :library :elm/library :opts map?)
  :ret (s/or :library :life/compiled-library :anomaly ::anom/anomaly))
