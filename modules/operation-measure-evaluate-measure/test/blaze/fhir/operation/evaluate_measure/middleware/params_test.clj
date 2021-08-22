(ns blaze.fhir.operation.evaluate-measure.middleware.params-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.fhir.operation.evaluate-measure.middleware.params :as params]
    [blaze.middleware.fhir.error :refer [wrap-error]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def operation-outcome-uri
  #fhir/uri"http://terminology.hl7.org/CodeSystem/operation-outcome")


(def handler (-> (params/wrap-coerce-params ac/completed-future) wrap-error))


(deftest wrap-coerce-params
  (testing "period start"
    (testing "missing"
      (let [{:keys [status body]}
            @(handler {:params {"periodEnd" "2015"}})]

        (is (= 400 status))

        (is (= :fhir/OperationOutcome (:fhir/type body)))

        (given (-> body :issue first)
          :severity := #fhir/code"error"
          :code := #fhir/code"value"
          [:details :coding 0 :system] := operation-outcome-uri
          [:details :coding 0 :code] := #fhir/code"MSG_PARAM_INVALID"
          :diagnostics := "Missing required parameter `periodStart`."
          [:expression first] := "periodStart")))

    (testing "invalid"
      (let [{:keys [status body]}
            @(handler {:params {"periodStart" "a"}})]

        (is (= 400 status))

        (is (= :fhir/OperationOutcome (:fhir/type body)))

        (given (-> body :issue first)
          :severity := #fhir/code"error"
          :code := #fhir/code"value"
          [:details :coding 0 :system] := operation-outcome-uri
          [:details :coding 0 :code] := #fhir/code"MSG_PARAM_INVALID"
          :diagnostics := "Invalid parameter `periodStart` with value `a`. Should be a date in format YYYY, YYYY-MM or YYYY-MM-DD."
          [:expression first] := "periodStart"))))

  (testing "period end"
    (testing "missing"
      (let [{:keys [status body]}
            @(handler {:params {"periodStart" "2015"}})]

        (is (= 400 status))

        (is (= :fhir/OperationOutcome (:fhir/type body)))

        (given (-> body :issue first)
          :severity := #fhir/code"error"
          :code := #fhir/code"value"
          [:details :coding 0 :system] := operation-outcome-uri
          [:details :coding 0 :code] := #fhir/code"MSG_PARAM_INVALID"
          :diagnostics := "Missing required parameter `periodEnd`."
          [:expression first] := "periodEnd")))

    (testing "invalid"
      (let [{:keys [status body]}
            @(handler {:params {"periodStart" "2015" "periodEnd" "a"}})]

        (is (= 400 status))

        (is (= :fhir/OperationOutcome (:fhir/type body)))

        (given (-> body :issue first)
          :severity := #fhir/code"error"
          :code := #fhir/code"value"
          [:details :coding 0 :system] := operation-outcome-uri
          [:details :coding 0 :code] := #fhir/code"MSG_PARAM_INVALID"
          :diagnostics := "Invalid parameter `periodEnd` with value `a`. Should be a date in format YYYY, YYYY-MM or YYYY-MM-DD."
          [:expression first] := "periodEnd"))))

  (testing "report type"
    (testing "invalid"
      (let [{:keys [status body]}
            @(handler
               {:request-method :get
                :params
                {"periodStart" "2014"
                 "periodEnd" "2015"
                 "reportType" "<invalid>"}})]

        (is (= 400 status))

        (is (= :fhir/OperationOutcome (:fhir/type body)))

        (given (-> body :issue first)
          :severity := #fhir/code"error"
          :code := #fhir/code"value"
          :diagnostics := "Invalid parameter `reportType` with value `<invalid>`. Should be one of `subject`, `subject-list` or `population`.")))

    (testing "subject-list is not possible with a GET request"
      (let [{:keys [status body]}
            @(handler
               {:request-method :get
                :params
                {"reportType" "subject-list"
                 "periodStart" "2014"
                 "periodEnd" "2015"}})]

        (is (= 422 status))

        (is (= :fhir/OperationOutcome (:fhir/type body)))

        (given (-> body :issue first)
          :severity := #fhir/code"error"
          :code := #fhir/code"not-supported"
          :diagnostics := "The parameter `reportType` with value `subject-list` is not supported for GET requests. Please use POST or one of `subject` or `population`.")))

    (testing "default"
      (testing "is population for normal requests"
        (let [{:blaze.fhir.operation.evaluate-measure/keys [params]}
              @(handler
                 {:request-method :get
                  :params
                  {"periodStart" "2014"
                   "periodEnd" "2015"}})]

          (given params
            :report-type := "population")))

      (testing "is subject for subject requests"
        (let [{:blaze.fhir.operation.evaluate-measure/keys [params]}
              @(handler
                 {:request-method :get
                  :params
                  {"periodStart" "2014"
                   "periodEnd" "2015"
                   "subject" "foo"}})]

          (given params
            :report-type := "subject")))))

  (testing "subject"
    (testing "local ref"
      (let [{:blaze.fhir.operation.evaluate-measure/keys [params]}
            @(handler
               {:request-method :get
                :params
                {"periodStart" "2014"
                 "periodEnd" "2015"
                 "subject" "Foo/173216"}})]

        (given params
          :subject-ref := ["Foo" "173216"])))

    (testing "id only"
      (let [{:blaze.fhir.operation.evaluate-measure/keys [params]}
            @(handler
               {:request-method :get
                :params
                {"periodStart" "2014"
                 "periodEnd" "2015"
                 "subject" "173216"}})]

        (given params
          :subject-ref := "173216")))

    (testing "invalid"
      (let [{:keys [status body]}
            @(handler
               {:params
                {"periodStart" "2014"
                 "periodEnd" "2015"
                 "subject" "a/1"}})]

        (is (= 400 status))

        (is (= :fhir/OperationOutcome (:fhir/type body)))

        (given (-> body :issue first)
          :severity := #fhir/code"error"
          :code := #fhir/code"value"
          [:details :coding 0 :system] := operation-outcome-uri
          [:details :coding 0 :code] := #fhir/code"MSG_PARAM_INVALID"
          :diagnostics := "Invalid parameter `subject` with value `a/1`. Should be a reference."
          [:expression first] := "subject")))))
