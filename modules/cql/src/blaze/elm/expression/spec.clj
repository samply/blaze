(ns blaze.elm.expression.spec
  (:require
   [blaze.db.api-spec]
   [blaze.elm.compiler :as-alias c]
   [blaze.elm.expression :as-alias expr]
   [blaze.elm.spec]
   [clojure.spec.alpha :as s]
   [java-time.api :as time]))

(s/def ::now
  time/offset-date-time?)

(s/def ::parameters
  (s/map-of :elm/name ::c/expression))

(s/def ::expr/context
  (s/keys :req-un [:blaze.db/db ::now]
          :opt-un [::c/expression-defs ::parameters]))
