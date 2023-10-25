(ns blaze.elm.compiler.library.spec
  (:require
    [blaze.elm.compiler :as-alias c]
    [blaze.elm.compiler.expression-def :as-alias expression-def]
    [blaze.elm.compiler.spec]
    [blaze.fhir.spec.spec]
    [clojure.spec.alpha :as s]))


(s/def ::expression-def/name
  string?)


(s/def ::expression-def/context
  :fhir.resource/type)


(s/def ::c/expression-def
  (s/keys :req-un [::expression-def/name ::expression-def/context ::c/expression]))


(s/def ::c/expression-defs
  (s/map-of :elm/name ::c/expression-def))


(s/def ::c/parameter-default-values
  (s/map-of :elm/name ::c/expression))


(s/def ::c/parameters
  (s/map-of :elm/name ::c/expression))


(s/def ::c/library
  (s/keys :req-un [::c/expression-defs ::c/parameter-default-values]))


(s/def ::c/options
  map?)
