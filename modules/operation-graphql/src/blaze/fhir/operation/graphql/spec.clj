(ns blaze.fhir.operation.graphql.spec
  (:require
    [blaze.executors :as ex]
    [blaze.fhir.operation.graphql :as-alias graphql]
    [clojure.spec.alpha :as s]))


(s/def ::graphql/executor
  ex/executor?)


(s/def ::graphql/num-threads
  pos-int?)
