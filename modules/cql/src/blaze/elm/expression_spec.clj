(ns blaze.elm.expression-spec
  (:require
    [blaze.db.api-spec]
    [blaze.elm.compiler :as-alias compiler]
    [blaze.elm.compiler.library-spec]
    [blaze.elm.compiler.spec]
    [blaze.elm.expression :as expr]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]
    [java-time :as time]))


(s/def ::now
  time/offset-date-time?)


(s/def ::parameters
  (s/map-of :elm/name ::compiler/expression))


(s/def :blaze.elm.expression/context
  (s/keys :req-un [:blaze.db/db ::now]
          :opt-un [::compiler/expression-defs ::parameters]))


(s/fdef expr/eval
  :args (s/cat :context :blaze.elm.expression/context
               :expression :blaze.elm.compiler/expression
               :resource (s/nilable (s/or :resource :blaze/resource
                                          :resource-handle :blaze.db/resource-handle))))
