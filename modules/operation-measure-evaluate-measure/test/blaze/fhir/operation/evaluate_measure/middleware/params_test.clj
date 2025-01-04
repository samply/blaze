(ns blaze.fhir.operation.evaluate-measure.middleware.params-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir.operation.evaluate-measure.middleware.params :as params]
   [blaze.fhir.operation.evaluate-measure.test-util :refer [wrap-error]]
   [blaze.fhir.util :as u]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def operation-outcome-uri
  #fhir/uri"http://terminology.hl7.org/CodeSystem/operation-outcome")

(def handler
  "This testing handler wraps the request into a future.

   If an error arises, it returns a normal ring response map.
   If no error arises, it returns the original request."
  (-> (params/wrap-coerce-params ac/completed-future)
      wrap-error))

(deftest wrap-coerce-params-test
  (testing "period start"
    (testing "missing"
      (let [{:keys [status body]}
            @(handler {:params {}})]

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

  (testing "valid period"
    (testing "with params"
      (let [{:blaze.fhir.operation.evaluate-measure/keys [params]}
            @(handler {:params {"periodStart" "2020"
                                "periodEnd" "2021"}})]

        (given params
          [:period 0] := #fhir/date"2020"
          [:period 1] := #fhir/date"2021")))

    (testing "with resource"
      (let [{:blaze.fhir.operation.evaluate-measure/keys [params]}
            @(handler
              {:body
               (u/parameters
                "periodStart" #fhir/date"2020"
                "periodEnd" #fhir/date"2021")})]

        (given params
          [:period 0] := #fhir/date"2020"
          [:period 1] := #fhir/date"2021"))))

  (testing "measure"
    (testing "invalid (only POST)"
      (let [request {:request-method :post
                     :body
                     (u/parameters
                      "periodStart" #fhir/date"2014"
                      "periodEnd" #fhir/date"2015"
                      "measure" #fhir/date"2015")}
            {:keys [status body]} @(handler request)]

        (is (= 400 status))

        (is (= :fhir/OperationOutcome (:fhir/type body)))

        (given (-> body :issue first)
          :severity := #fhir/code"error"
          :code := #fhir/code"value"
          :diagnostics := "Invalid parameter `measure` with value `2015`. Should be a string.")))

    (testing "valid (both GET and POST)"
      (doseq [request
              [{:params
                {"periodStart" "2015"
                 "periodEnd" "2016"
                 "measure" "measure-202606"}}
               {:body
                (u/parameters
                 "periodStart" #fhir/date"2014"
                 "periodEnd" #fhir/date"2015"
                 "measure" #fhir/string"measure-202606")}]]
        (let [{:blaze.fhir.operation.evaluate-measure/keys [params]}
              @(handler request)]

          (given params
            :measure := "measure-202606")))))

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

    (testing "subject-list on POST request"
      (let [{:blaze.fhir.operation.evaluate-measure/keys [params]}
            @(handler
              {:request-method :post
               :body
               (u/parameters
                "periodStart" #fhir/date"2014"
                "periodEnd" #fhir/date"2015"
                "reportType" #fhir/code"subject-list")})]

        (given params
          :report-type := "subject-list")))

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
