(ns blaze.fhir.operation.evaluate-measure.middleware.params-test
  (:require
    [blaze.fhir.operation.evaluate-measure.middleware.params :as params]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(defn- fixture [f]
  (st/instrument)
  (log/set-level! :trace)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest wrap-coerce-params
  (testing "missing periodStart"
    (given @((params/wrap-coerce-params identity) {})
      :status := 400
      [:body :resourceType] := "OperationOutcome"
      [:body :issue 0 :diagnostics] := "Missing required parameter `periodStart`."))

  (testing "invalid periodStart"
    (given @((params/wrap-coerce-params identity) {:params {"periodStart" "a"}})
      :status := 400
      [:body :resourceType] := "OperationOutcome"
      [:body :issue 0 :diagnostics] := "Invalid parameter periodStart: `a`. Should be a date in format YYYY, YYYY-MM or YYYY-MM-DD.")))
