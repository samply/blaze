(ns blaze.fhir.operation.evaluate-measure.middleware.params-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir.operation.evaluate-measure.middleware.params :as params]
   [blaze.fhir.operation.evaluate-measure.test-util :refer [wrap-error]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-level! :trace)

(test/use-fixtures :each tu/fixture)

(def operation-outcome-uri
  #fhir/uri"http://terminology.hl7.org/CodeSystem/operation-outcome")

(def handler (-> (params/wrap-coerce-params ac/completed-future) wrap-error))

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
               {:fhir/type :fhir/Parameters
                :parameter
                [{:fhir/type :fhir.Parameters/parameter
                  :name "periodStart"
                  :value #fhir/date"2020"}
                 {:fhir/type :fhir.Parameters/parameter
                  :name "periodEnd"
                  :value #fhir/date"2021"}]}})]

        (given params
          [:period 0] := #fhir/date"2020"
          [:period 1] := #fhir/date"2021"))))

  (testing "measure"
    (doseq [request
            [{:params
              {"periodStart" "2015"
               "periodEnd" "2016"
               "measure" "measure-202606"}}
             {:body
              {:fhir/type :fhir/Parameters
               :parameter
               [{:fhir/type :fhir.Parameters/parameter
                 :name "periodStart"
                 :value #fhir/date"2014"}
                {:fhir/type :fhir.Parameters/parameter
                 :name "periodEnd"
                 :value #fhir/date"2015"}
                {:fhir/type :fhir.Parameters/parameter
                 :name "measure"
                 :value #fhir/string"measure-202606"}]}}]]
      (let [{:blaze.fhir.operation.evaluate-measure/keys [params]}
            @(handler request)]

        (given params
          :measure := "measure-202606"))))

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
               {:fhir/type :fhir/Parameters
                :parameter
                [{:fhir/type :fhir.Parameters/parameter
                  :name "periodStart"
                  :value #fhir/date"2014"}
                 {:fhir/type :fhir.Parameters/parameter
                  :name "periodEnd"
                  :value #fhir/date"2015"}
                 {:fhir/type :fhir.Parameters/parameter
                  :name "reportType"
                  :value #fhir/code"subject-list"}]}})]

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
