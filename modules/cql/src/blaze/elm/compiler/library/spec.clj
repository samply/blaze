(ns blaze.elm.compiler.library.spec
  (:require
   [blaze.elm.compiler :as-alias c]
   [blaze.elm.compiler.expression-def :as-alias expression-def]
   [blaze.elm.compiler.function-def :as-alias function-def]
   [blaze.elm.compiler.spec]
   [blaze.fhir.spec.spec]
   [blaze.terminology-service.spec]
   [clojure.spec.alpha :as s]))

(s/def ::expression-def/name
  string?)

(s/def ::expression-def/context
  :fhir.resource/type)

(s/def ::c/expression-def
  (s/keys :req-un [::expression-def/name ::expression-def/context ::c/expression]))

(s/def ::c/expression-defs
  (s/map-of :elm/name ::c/expression-def))

(s/def ::function-def/name
  string?)

(s/def ::function-def/context
  :fhir.resource/type)

(s/def ::c/function-def
  (s/keys :req-un [::function-def/name ::function-def/context ::c/function]))

(s/def ::c/function-defs
  (s/coll-of ::c/function-def))

(s/def ::c/parameter-default-values
  (s/map-of :elm/name ::c/expression))

(s/def ::c/parameters
  (s/map-of :elm/name ::c/expression))

(s/def ::c/library
  (s/keys :req-un [::c/expression-defs ::c/function-defs ::c/parameter-default-values]))

(s/def ::c/context
  (s/keys :req-un [:blaze.db/node :blaze/terminology-service]))

(s/def ::c/options
  map?)
