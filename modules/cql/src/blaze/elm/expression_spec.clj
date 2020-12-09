(ns blaze.elm.expression-spec
  (:require
    [blaze.db.api-spec]
    [blaze.elm.compiler.spec]
    [blaze.elm.expression :as expr]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]
    [java-time :as jt]))


(s/def ::now
  jt/offset-date-time?)


(s/def ::library-context
  (s/map-of string? :blaze.elm.compiler/expression))


(s/def :blaze.elm.expression/context
  (s/keys :req-un [:blaze.db/db ::now] :opt-un [::library-context]))


(s/def :blaze.elm.expression/scope
  any?)


(s/fdef expr/eval
  :args (s/cat :expression :blaze.elm.compiler/expression
               :context :blaze.elm.expression/context
               :resource (s/nilable (s/or :resource :blaze/resource :resource-handle :blaze.db/resource-handle))
               :scope (s/nilable :blaze.elm.expression/scope)))
