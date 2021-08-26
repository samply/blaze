(ns blaze.handler.util-test
  (:require
    [blaze.anomaly :refer [ex-anom]]
    [blaze.async.comp-spec]
    [blaze.handler.util :as handler-util]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest preference-test
  (are [headers res] (= res (handler-util/preference headers "return"))
    {"prefer" "return=representation"} :blaze.preference.return/representation))


(deftest operation-outcome-test
  (testing "fault anomaly"
    (given (handler-util/operation-outcome {::anom/category ::anom/fault})
      :fhir/type := :fhir/OperationOutcome
      [:issue 0 :fhir/type] := :fhir.OperationOutcome/issue
      [:issue 0 :severity] := #fhir/code"error"
      [:issue 0 :code] := #fhir/code"exception")))


(deftest error-response-test
  (testing "fault anomaly"
    (given (handler-util/error-response {::anom/category ::anom/fault})
      :status := 500
      [:body :fhir/type] := :fhir/OperationOutcome
      [:body :issue 0 :fhir/type] := :fhir.OperationOutcome/issue
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"exception"))

  (testing "exception"
    (given (handler-util/error-response (Exception.))
      :status := 500
      [:body :fhir/type] := :fhir/OperationOutcome
      [:body :issue 0 :fhir/type] := :fhir.OperationOutcome/issue
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"exception")

    (testing "with not-found anomaly"
      (given (handler-util/error-response
               (ex-anom {::anom/category ::anom/not-found}))
        :status := 404
        [:body :fhir/type] := :fhir/OperationOutcome
        [:body :issue 0 :fhir/type] := :fhir.OperationOutcome/issue
        [:body :issue 0 :severity] := #fhir/code"error"
        [:body :issue 0 :code] := #fhir/code"not-found"))))


(deftest bundle-error-response-test
  (testing "fault anomaly"
    (given (handler-util/bundle-error-response {::anom/category ::anom/fault})
      :fhir/type := :fhir.Bundle.entry/response
      :status := "500"
      [:outcome :fhir/type] := :fhir/OperationOutcome
      [:outcome :issue 0 :fhir/type] := :fhir.OperationOutcome/issue
      [:outcome :issue 0 :severity] := #fhir/code"error"
      [:outcome :issue 0 :code] := #fhir/code"exception")))
