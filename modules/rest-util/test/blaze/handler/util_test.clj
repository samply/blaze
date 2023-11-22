(ns blaze.handler.util-test
  (:require
   [blaze.async.comp-spec]
   [blaze.fhir.test-util :refer [given-failed-future]]
   [blaze.handler.util :as handler-util]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

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
      (given (handler-util/error-response {::anom/category ::anom/not-found})
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

(deftest method-not-found-handler-test
  (given (handler-util/not-found-handler {})
    :status := 404
    [:body :fhir/type] := :fhir/OperationOutcome
    [:body :issue 0 :severity] := #fhir/code"error"
    [:body :issue 0 :code] := #fhir/code"not-found"))

(deftest method-not-allowed-handler-test
  (given (handler-util/method-not-allowed-handler
          {:uri "/Patient" :request-method :put})
    :status := 405
    [:body :fhir/type] := :fhir/OperationOutcome
    [:body :issue 0 :severity] := #fhir/code"error"
    [:body :issue 0 :code] := #fhir/code"processing"
    [:body :issue 0 :diagnostics] := "Method PUT not allowed on `/Patient` endpoint."))

(deftest method-not-allowed-batch-handler-test
  (given-failed-future (handler-util/method-not-allowed-batch-handler
                        {:uri "/Patient/0" :request-method :post})
    ::anom/category := ::anom/forbidden
    ::anom/message := "Method POST not allowed on `/Patient/0` endpoint."
    :http/status := 405
    :fhir/issue := "processing"))
