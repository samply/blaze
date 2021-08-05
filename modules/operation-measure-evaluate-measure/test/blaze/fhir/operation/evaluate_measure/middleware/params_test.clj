(ns blaze.fhir.operation.evaluate-measure.middleware.params-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.fhir.operation.evaluate-measure.middleware.params :as params]
    [blaze.middleware.fhir.error :refer [wrap-error]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def handler (-> (params/wrap-coerce-params ac/completed-future) wrap-error))


(deftest wrap-coerce-params
  (testing "missing periodStart"
    (given @(handler {})
      :status := 400
      [:body :fhir/type] := :fhir/OperationOutcome
      [:body :issue 0 :diagnostics] := "Missing required parameter `periodStart`."))

  (testing "invalid periodStart"
    (given @(handler {:params {"periodStart" "a"}})
      :status := 400
      [:body :fhir/type] := :fhir/OperationOutcome
      [:body :issue 0 :diagnostics] := "Invalid parameter periodStart: `a`. Should be a date in format YYYY, YYYY-MM or YYYY-MM-DD.")))
