(ns blaze.elm.compiler.library-spec
  (:require
    [blaze.anomaly-spec]
    [blaze.elm.compiler :as-alias compiler]
    [blaze.elm.compiler-spec]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.expression-def :as-alias expression-def]
    [blaze.elm.compiler.library :as library]
    [blaze.elm.compiler.spec]
    [blaze.fhir.spec.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]))


(s/def ::expression-def/name
  string?)


(s/def ::expression-def/context
  :fhir.resource/type)


(s/def ::compiler/expression-def
  (s/keys :req-un [::expression-def/name ::expression-def/context ::compiler/expression]))


(s/def ::compiler/expression-defs
  (s/map-of :elm/name core/expr?))


(s/def ::compiler/parameter-default-values
  (s/map-of :elm/name ::compiler/expression))


(s/def ::compiler/library
  (s/keys :req-un [::compiler/expression-defs ::compiler/parameter-default-values]))


(s/fdef library/compile-library
  :args (s/cat :node :blaze.db/node :library :elm/library :opts map?)
  :ret (s/or :library ::compiler/library :anomaly ::anom/anomaly))
