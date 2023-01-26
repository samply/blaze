(ns blaze.fhir.operation.evaluate-measure.cql.spec
  (:require
    [blaze.elm.compiler :as-alias compiler]
    [blaze.fhir.operation.evaluate-measure.cql :as-alias cql]
    [clojure.spec.alpha :as s]
    [java-time.api :as time]))


(s/def ::cql/now
  time/offset-date-time?)


(s/def ::cql/timeout-eclipsed?
  ifn?)


(s/def ::cql/timeout
  time/duration?)


(s/def ::cql/context
  (s/keys :req-un [:blaze.db/db ::cql/now ::cql/timeout-eclipsed? ::cql/timeout
                   ::compiler/expression-defs]))


(s/def ::cql/parameters
  (s/map-of string? any?))


(s/def ::cql/individual-context
  (s/merge ::cql/context (s/keys :opt-un [::cql/parameters])))
