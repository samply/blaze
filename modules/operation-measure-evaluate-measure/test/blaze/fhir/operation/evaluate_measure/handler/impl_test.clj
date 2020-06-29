(ns blaze.fhir.operation.evaluate-measure.handler.impl-test
  (:require
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.executors :as ex]
    [blaze.fhir.operation.evaluate-measure.handler.impl :refer [handler]]
    [blaze.log]
    [blaze.uuid :refer [random-uuid]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log])
  (:import
    [java.time Clock Instant Year ZoneOffset]))


(defn- fixture [f]
  (st/instrument)
  (log/set-level! :trace)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def clock (Clock/fixed Instant/EPOCH (ZoneOffset/ofHours 1)))
(defonce executor (ex/single-thread-executor))

(def router
  (reitit/router
    [["/Patient/{id}" {:name :Patient/instance}]
     ["/MeasureReport/{id}/_history/{vid}" {:name :MeasureReport/versioned-instance}]]
    {:syntax :bracket}))


(defn- handler-with [txs]
  (fn [request]
    (with-open [node (mem-node-with txs)]
      @((handler clock node executor) request))))


(defn- scoring-concept [code]
  {:coding
   [{:system "http://terminology.hl7.org/CodeSystem/measure-scoring"
     :code code}]})


(defn- population-concept [code]
  {:coding
   [{:system "http://terminology.hl7.org/CodeSystem/measure-population"
     :code code}]})


(defn- cql-expression [expr]
  {:language "text/cql"
   :expression expr})


(deftest handler-test
  (testing "Returns Not Found on Non-Existing Measure"
    (testing "on type endpoint"
      (let [{:keys [status body]}
            ((handler-with [])
             {:path-params {:id "0"}})]

        (is (= 404 status))

        (is (= "OperationOutcome" (:resourceType body)))

        (given (-> body :issue first)
          :severity := "error"
          :code := "not-found")))

    (testing "on instance endpoint"
      (let [{:keys [status body]}
            ((handler-with [])
             {:params {"measure" "url-181501"}})]

        (is (= 404 status))

        (is (= "OperationOutcome" (:resourceType body)))

        (given (-> body :issue first)
          :severity := "error"
          :code := "not-found"))))


  (testing "Returns Gone on Deleted Resource"
    (let [{:keys [status body]}
          ((handler-with [[[:put {:resourceType "Measure" :id "0"}]]
                          [[:delete "Measure" "0"]]])
           {:path-params {:id "0"}})]

      (is (= 410 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (given (-> body :issue first)
        :severity := "error"
        :code := "deleted")))


  (testing "measure without library"
    (let [{:keys [status body]}
          ((handler-with [[[:put {:resourceType "Measure" :id "0"
                                   :url "url-182126"}]]])
            {::reitit/router router
             :params
             {"measure" "url-182126"
              "periodStart" (Year/of 2014)
              "periodEnd" (Year/of 2015)}})]

      (is (= 422 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (given (-> body :issue first)
        :severity := "error"
        :code := "not-supported"
        :diagnostics := "Missing primary library. Currently only CQL expressions together with one primary library are supported."
        [:expression first] := "Measure.library")))


  (testing "measure with non-existing library"
    (let [{:keys [status body]}
          ((handler-with [[[:put {:resourceType "Measure" :id "0"
                                   :url "url-181501"
                                   :library ["library-url-094115"]}]]])
            {::reitit/router router
             :params
             {"measure" "url-181501"
              "periodStart" (Year/of 2014)
              "periodEnd" (Year/of 2015)}})]

      (is (= 400 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (given (-> body :issue first)
        :severity := "error"
        :code := "value"
        :diagnostics := "Can't find the library with canonical URI `library-url-094115`."
        [:expression first] := "Measure.library")))


  (testing "missing content in library"
    (let [{:keys [status body]}
          ((handler-with [[[:put {:resourceType "Measure" :id "0"
                                   :url "url-182104"
                                   :library ["library-url-094115"]}]
                            [:put {:resourceType "Library" :id "0"
                                   :url "library-url-094115"}]]])
            {::reitit/router router
             :params
             {"measure" "url-182104"
              "periodStart" (Year/of 2014)
              "periodEnd" (Year/of 2015)}})]

      (is (= 400 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (given (-> body :issue first)
        :severity := "error"
        :code := "value"
        :diagnostics := "Missing content in library with id `0`."
        [:expression first] := "Library.content")))


  (testing "non text/cql content type"
    (let [{:keys [status body]}
          ((handler-with [[[:put {:resourceType "Measure" :id "0"
                                   :url "url-182051"
                                   :library ["library-url-094115"]}]
                            [:put {:resourceType "Library" :id "0"
                                   :url "library-url-094115"
                                   :content
                                   [{:contentType "text/plain"}]}]]])
            {::reitit/router router
             :params
             {"measure" "url-182051"
              "periodStart" (Year/of 2014)
              "periodEnd" (Year/of 2015)}})]

      (is (= 400 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (given (-> body :issue first)
        :severity := "error"
        :code := "value"
        :diagnostics := "Non `text/cql` content type of `text/plain` of first attachment in library with id `0`."
        [:expression first] := "Library.content[0].contentType")))


  (testing "missing data in library content"
    (let [{:keys [status body]}
          ((handler-with [[[:put {:resourceType "Measure" :id "0"
                                   :url "url-182039"
                                   :library ["library-url-094115"]}]
                            [:put {:resourceType "Library" :id "0"
                                   :url "library-url-094115"
                                   :content
                                   [{:contentType "text/cql"}]}]]])
            {::reitit/router router
             :params
             {"measure" "url-182039"
              "periodStart" (Year/of 2014)
              "periodEnd" (Year/of 2015)}})]

      (is (= 400 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (given (-> body :issue first)
        :severity := "error"
        :code := "value"
        :diagnostics := "Missing embedded data of first attachment in library with id `0`."
        [:expression first] := "Library.content[0].data")))

  (testing "invalid report type"
    (let [{:keys [status body]}
          ((handler-with
             [[[:put {:resourceType "Measure" :id "0"
                      :url "url-181501"
                      :library ["library-url-094115"]}]
               [:put {:resourceType "Library" :id "0"
                      :url "library-url-094115"
                      :content
                      [{:contentType "text/cql"
                        :data ""}]}]]])
           {:request-method :get
            :params
            {"measure" "url-181501"
             "reportType" "<invalid>"}})]

      (is (= 400 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (given (-> body :issue first)
        :severity := "error"
        :code := "value"
        :diagnostics := "The reportType `<invalid>` is invalid. Please use one of `subject`, `subject-list` or `population`.")))

  (testing "report type of subject-list is not possible with a GET request"
    (let [{:keys [status body]}
          ((handler-with
             [[[:put {:resourceType "Measure" :id "0"
                      :url "url-181501"
                      :library ["library-url-094115"]}]
               [:put {:resourceType "Library" :id "0"
                      :url "library-url-094115"
                      :content
                      [{:contentType "text/cql"
                        :data ""}]}]]])
           {:request-method :get
            :params
            {"measure" "url-181501"
             "reportType" "subject-list"}})]

      (is (= 422 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (given (-> body :issue first)
        :severity := "error"
        :code := "not-supported"
        :diagnostics := "The reportType `subject-list` is not supported for GET requests. Please use POST.")))


  (testing "Success"
    (testing "on type endpoint"
      (testing "as GET request"
        (testing "with empty CQL library"
          (let [{:keys [status body]}
                ((handler-with
                    [[[:put {:resourceType "Measure" :id "0"
                             :url "url-181407"
                             :library ["library-url-094115"]}]
                      [:put {:resourceType "Library" :id "0"
                             :url "library-url-094115"
                             :content
                             [{:contentType "text/cql"
                               :data ""}]}]]])
                  {::reitit/router router
                   :request-method :get
                   :params
                   {"measure" "url-181407"
                    "periodStart" (Year/of 2014)
                    "periodEnd" (Year/of 2015)}})]

            (is (= 200 status))

            (given body
              :resourceType := "MeasureReport"
              :status := "complete"
              :type := "summary"
              :measure := "url-181407"
              :date := "1970-01-01T01:00:00+01:00"
              [:period :start] := "2014"
              [:period :end] := "2015")))

        (testing "cohort scoring"
          (let [{:keys [status body]}
                ((handler-with
                    [[[:put
                       {:resourceType "Measure" :id "0"
                        :url "url-181501"
                        :library ["library-url-094115"]
                        :scoring (scoring-concept "cohort")
                        :group
                        [{:population
                          [{:code (population-concept "initial-population")
                            :criteria (cql-expression "InInitialPopulation")}]}]}]
                      [:put
                       {:resourceType "Library" :id "0"
                        :url "library-url-094115"
                        :content
                        [{:contentType "text/cql"
                          :data "bGlicmFyeSBSZXRyaWV2ZQp1c2luZyBGSElSIHZlcnNpb24gJzQuMC4wJwppbmNsdWRlIEZISVJIZWxwZXJzIHZlcnNpb24gJzQuMC4wJwoKY29udGV4dCBQYXRpZW50CgpkZWZpbmUgSW5Jbml0aWFsUG9wdWxhdGlvbjoKICBQYXRpZW50LmdlbmRlciA9ICdtYWxlJwo="}]}]
                      [:put
                       {:resourceType "Patient"
                        :id "0"
                        :gender "male"}]]])
                  {::reitit/router router
                   :request-method :get
                   :params
                   {"measure" "url-181501"
                    "periodStart" (Year/of 2014)
                    "periodEnd" (Year/of 2015)}})]

            (is (= 200 status))

            (given body
              :resourceType := "MeasureReport"
              :status := "complete"
              :type := "summary"
              :measure := "url-181501"
              :date := "1970-01-01T01:00:00+01:00"
              [:period :start] := "2014"
              [:period :end] := "2015"
              [:group 0 :population 0 :code :coding 0 :system] := "http://terminology.hl7.org/CodeSystem/measure-population"
              [:group 0 :population 0 :code :coding 0 :code] := "initial-population"
              [:group 0 :population 0 :count] := 1)))

        (testing "cohort scoring with stratifiers"
          (let [{:keys [status body]}
                ((handler-with
                    [[[:put
                       {:resourceType "Measure" :id "0"
                        :url "url-181501"
                        :library ["library-url-094115"]
                        :scoring (scoring-concept "cohort")
                        :group
                        [{:population
                          [{:code (population-concept "initial-population")
                            :criteria (cql-expression "InInitialPopulation")}]
                          :stratifier
                          [{:code {:text "gender"}
                            :criteria (cql-expression "Gender")}]}]}]
                      [:put
                       {:resourceType "Library" :id "0"
                        :url "library-url-094115"
                        :content
                        [{:contentType "text/cql"
                          :data "bGlicmFyeSBSZXRyaWV2ZQp1c2luZyBGSElSIHZlcnNpb24gJzQuMC4wJwppbmNsdWRlIEZISVJIZWxwZXJzIHZlcnNpb24gJzQuMC4wJwoKY29udGV4dCBQYXRpZW50CgpkZWZpbmUgSW5Jbml0aWFsUG9wdWxhdGlvbjoKICB0cnVlCgpkZWZpbmUgR2VuZGVyOgogIFBhdGllbnQuZ2VuZGVyCg=="}]}]
                      [:put
                       {:resourceType "Patient"
                        :id "0"
                        :gender "male"}]
                      [:put
                       {:resourceType "Patient"
                        :id "1"
                        :gender "female"}]
                      [:put
                       {:resourceType "Patient"
                        :id "2"
                        :gender "female"}]]])
                  {::reitit/router router
                   :request-method :get
                   :params
                   {"measure" "url-181501"
                    "periodStart" (Year/of 2014)
                    "periodEnd" (Year/of 2015)}})]

            (is (= 200 status))

            (given body
              :resourceType := "MeasureReport"
              :status := "complete"
              :type := "summary"
              :measure := "url-181501"
              :date := "1970-01-01T01:00:00+01:00"
              [:period :start] := "2014"
              [:period :end] := "2015"
              [:group 0 :population 0 :code :coding 0 :system] := "http://terminology.hl7.org/CodeSystem/measure-population"
              [:group 0 :population 0 :code :coding 0 :code] := "initial-population"
              [:group 0 :population 0 :count] := 3
              [:group 0 :stratifier 0 :code 0 :text] := "gender"
              [:group 0 :stratifier 0 :stratum 0 :population 0 :code :coding 0 :system] := "http://terminology.hl7.org/CodeSystem/measure-population"
              [:group 0 :stratifier 0 :stratum 0 :population 0 :code :coding 0 :code] := "initial-population"
              [:group 0 :stratifier 0 :stratum 0 :population 0 :count] := 2
              [:group 0 :stratifier 0 :stratum 0 :value :text] := "female"
              [:group 0 :stratifier 0 :stratum 1 :population 0 :code :coding 0 :system] := "http://terminology.hl7.org/CodeSystem/measure-population"
              [:group 0 :stratifier 0 :stratum 1 :population 0 :code :coding 0 :code] := "initial-population"
              [:group 0 :stratifier 0 :stratum 1 :population 0 :count] := 1
              [:group 0 :stratifier 0 :stratum 1 :value :text] := "male"))))

      (testing "as POST request"
        (with-redefs
          [random-uuid (constantly #uuid "29217af5-1f5b-4860-af2d-277af728fed2")]
          (let [{:keys [status headers body]}
                ((handler-with
                    [[[:put {:resourceType "Measure" :id "0"
                             :url "url-181501"
                             :library ["library-url-094115"]}]
                      [:put {:resourceType "Library" :id "0"
                             :url "library-url-094115"
                             :content
                             [{:contentType "text/cql"
                               :data ""}]}]]])
                  {::reitit/router router
                   :request-method :post
                   :params
                   {"measure" "url-181501"
                    "periodStart" (Year/of 2014)
                    "periodEnd" (Year/of 2015)}})]

            (is (= 201 status))

            (testing "Location header"
              (is (= "/MeasureReport/29217af5-1f5b-4860-af2d-277af728fed2/_history/2"
                     (get headers "Location"))))

            (given body
              :resourceType := "MeasureReport"
              :status := "complete"
              :type := "summary"
              :measure := "url-181501"
              :date := "1970-01-01T01:00:00+01:00"
              [:period :start] := "2014"
              [:period :end] := "2015")))))

    (testing "on instance endpoint"
      (testing "as GET request"
        (let [{:keys [status body]}
              ((handler-with
                  [[[:put {:resourceType "Measure" :id "0"
                           :url "url-181501"
                           :library ["library-url-094115"]}]
                    [:put {:resourceType "Library" :id "0"
                           :url "library-url-094115"
                           :content
                           [{:contentType "text/cql"
                             :data ""}]}]]])
                {::reitit/router router
                 :request-method :get
                 :path-params {:id "0"}
                 :params
                 {"periodStart" (Year/of 2014)
                  "periodEnd" (Year/of 2015)}})]

          (is (= 200 status))

          (given body
            :resourceType := "MeasureReport"
            :status := "complete"
            :type := "summary"
            :measure := "url-181501"
            :date := "1970-01-01T01:00:00+01:00"
            [:period :start] := "2014"
            [:period :end] := "2015")))

      (testing "as POST request"
        (with-redefs
          [random-uuid (constantly #uuid "33e62d8a-cc01-4d8f-af7e-3166c603b87d")]
          (let [{:keys [status headers body]}
                ((handler-with
                    [[[:put {:resourceType "Measure" :id "0"
                             :url "url-181501"
                             :library ["library-url-094115"]}]
                      [:put {:resourceType "Library" :id "0"
                             :url "library-url-094115"
                             :content
                             [{:contentType "text/cql"
                               :data ""}]}]]])
                  {::reitit/router router
                   :request-method :post
                   :path-params {:id "0"}
                   :params
                   {"periodStart" (Year/of 2014)
                    "periodEnd" (Year/of 2015)}})]

            (is (= 201 status))

            (testing "Location header"
              (is (= "/MeasureReport/33e62d8a-cc01-4d8f-af7e-3166c603b87d/_history/2"
                     (get headers "Location"))))

            (given body
              :resourceType := "MeasureReport"
              :status := "complete"
              :type := "summary"
              :measure := "url-181501"
              :date := "1970-01-01T01:00:00+01:00"
              [:period :start] := "2014"
              [:period :end] := "2015")))))))
