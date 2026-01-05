(ns blaze.fhir.operation.cql.spec
  (:require
   [blaze.executors :as ex]
   [blaze.fhir.operation.cql :as-alias cql]
   [clojure.spec.alpha :as s]
   [java-time.api :as time]))

(s/def ::cql/executor
  ex/executor?)

(s/def ::cql/timeout
  time/duration?)

(s/def :blaze.fhir.operation.cql.timeout/millis
  nat-int?)
