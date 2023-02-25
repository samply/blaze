(ns blaze.fhir.operation.graphql.middleware-test
  (:require
    [blaze.fhir.operation.graphql.middleware :as middleware]
    [blaze.log]
    [blaze.middleware.fhir.db-spec]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest]]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(test/use-fixtures :each tu/fixture)


(deftest init-test
  (given (ig/init {::middleware/query {}})
    [::middleware/query :name] := ::middleware/query
    [::middleware/query :wrap] :? fn?))
