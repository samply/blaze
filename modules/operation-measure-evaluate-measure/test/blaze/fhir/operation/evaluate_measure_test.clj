(ns blaze.fhir.operation.evaluate-measure-test
  (:require
    [blaze.db.api-stub :refer [mem-node-system with-system-data]]
    [blaze.executors :as ex]
    [blaze.fhir.operation.evaluate-measure :as evaluate-measure]
    [blaze.fhir.spec.type :as type]
    [blaze.middleware.fhir.db :refer [wrap-db]]
    [blaze.middleware.fhir.db-spec]
    [blaze.middleware.fhir.error :refer [wrap-error]]
    [blaze.test-util :refer [given-thrown]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log])
  (:import
    [java.util.concurrent ExecutorService]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def ^:private base-url "base-url-144638")


(def router
  (reitit/router
    [["/MeasureReport" {:name :MeasureReport/type}]]
    {:syntax :bracket}))


(defn- scoring-concept [code]
  (type/map->CodeableConcept
    {:coding
     [(type/map->Coding
        {:system #fhir/uri"http://terminology.hl7.org/CodeSystem/measure-scoring"
         :code (type/->Code code)})]}))


(defn- population-concept [code]
  (type/map->CodeableConcept
    {:coding
     [(type/map->Coding
        {:system #fhir/uri"http://terminology.hl7.org/CodeSystem/measure-population"
         :code (type/->Code code)})]}))


(defn- cql-expression [expr]
  {:fhir/type :fhir/Expression
   :language #fhir/code"text/cql"
   :expression expr})


(def library-content
  #fhir/Attachment
      {:contentType #fhir/code"text/cql"
       :data #fhir/base64Binary"bGlicmFyeSBSZXRyaWV2ZQp1c2luZyBGSElSIHZlcnNpb24gJzQuMC4wJwppbmNsdWRlIEZISVJIZWxwZXJzIHZlcnNpb24gJzQuMC4wJwoKY29udGV4dCBQYXRpZW50CgpkZWZpbmUgSW5Jbml0aWFsUG9wdWxhdGlvbjoKICB0cnVlCgpkZWZpbmUgR2VuZGVyOgogIFBhdGllbnQuZ2VuZGVyCg=="})


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {::evaluate-measure/handler nil})
      :key := ::evaluate-measure/handler
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {::evaluate-measure/handler {}})
      :key := ::evaluate-measure/handler
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :executor))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:explain ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))))

  (testing "invalid executor"
    (given-thrown (ig/init {::evaluate-measure/handler {:executor ::invalid}})
      :key := ::evaluate-measure/handler
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:explain ::s/problems 3 :pred] := `ex/executor?
      [:explain ::s/problems 3 :val] := ::invalid)))


(def system
  (assoc mem-node-system
    ::evaluate-measure/handler
    {:node (ig/ref :blaze.db/node)
     :executor (ig/ref :blaze.test/executor)
     :clock (ig/ref :blaze.test/clock)
     :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}
    :blaze.test/executor {}
    :blaze.test/fixed-rng-fn {}))


(defn wrap-defaults [handler]
  (fn [request]
    (handler
       (assoc request
         :blaze/base-url base-url
         ::reitit/router router))))


(defmacro with-handler [[handler-binding] txs & body]
  `(with-system-data [{node# :blaze.db/node
                       handler# ::evaluate-measure/handler} system]
     ~txs
     (let [~handler-binding (-> handler# wrap-defaults (wrap-db node#)
                                wrap-error)]
       ~@body)))


(deftest handler-test
  (testing "Fails on missing mandatory params"
    (testing "periodStart"
      (with-handler [handler]
        []
        (let [{:keys [status body]}
              @(handler
                {:path-params {:id "0"}
                 :params {"periodEnd" "2015"}})]

          (is (= 400 status))

          (is (= :fhir/OperationOutcome (:fhir/type body)))

          (given (-> body :issue first)
            :severity := #fhir/code"error"
            :code := #fhir/code"value"
            [:details :coding 0 :system] := #fhir/uri"http://terminology.hl7.org/CodeSystem/operation-outcome"
            [:details :coding 0 :code] := #fhir/code"MSG_PARAM_INVALID"
            :diagnostics := "Missing required parameter `periodStart`."
            [:expression first] := "periodStart"))))

    (testing "periodEnd"
      (with-handler [handler]
        []
        (let [{:keys [status body]}
              @(handler
                {:path-params {:id "0"}
                 :params {"periodStart" "2014"}})]

          (is (= 400 status))

          (is (= :fhir/OperationOutcome (:fhir/type body)))

          (given (-> body :issue first)
            :severity := #fhir/code"error"
            :code := #fhir/code"value"
            [:details :coding 0 :system] := #fhir/uri"http://terminology.hl7.org/CodeSystem/operation-outcome"
            [:details :coding 0 :code] := #fhir/code"MSG_PARAM_INVALID"
            :diagnostics := "Missing required parameter `periodEnd`."
            [:expression first] := "periodEnd")))))

  (testing "Returns Not Found on Non-Existing Measure"
    (testing "on type endpoint"
      (with-handler [handler]
        []
        (let [{:keys [status body]}
              @(handler
                {:path-params {:id "0"}
                 :params {"periodStart" "2014" "periodEnd" "2015"}})]

          (is (= 404 status))

          (is (= :fhir/OperationOutcome (:fhir/type body)))

          (given (-> body :issue first)
            :severity := #fhir/code"error"
            :code := #fhir/code"not-found"))))

    (testing "on instance endpoint"
      (with-handler [handler]
        []
        (let [{:keys [status body]}
              @(handler
                {:params
                 {"measure" "url-181501"
                  "periodStart" "2014"
                  "periodEnd" "2015"}})]

          (is (= 404 status))

          (is (= :fhir/OperationOutcome (:fhir/type body)))

          (given (-> body :issue first)
            :severity := #fhir/code"error"
            :code := #fhir/code"not-found")))))


  (testing "Returns Gone on Deleted Resource"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Measure :id "0"}]]
       [[:delete "Measure" "0"]]]

      (let [{:keys [status body]}
            @(handler
              {:path-params {:id "0"}
               :params {"periodStart" "2014" "periodEnd" "2015"}})]

        (is (= 410 status))

        (is (= :fhir/OperationOutcome (:fhir/type body)))

        (given (-> body :issue first)
          :severity := #fhir/code"error"
          :code := #fhir/code"deleted"))))


  (testing "invalid report type"
    (with-handler [handler]
      []
      (let [{:keys [status body]}
            @(handler
              {:request-method :get
               :params
               {"measure" "url-181501"
                "reportType" "<invalid>"
                "periodStart" "2014"
                "periodEnd" "2015"}})]

        (is (= 400 status))

        (is (= :fhir/OperationOutcome (:fhir/type body)))

        (given (-> body :issue first)
          :severity := #fhir/code"error"
          :code := #fhir/code"value"
          :diagnostics := "The reportType `<invalid>` is invalid. Please use one of `subject`, `subject-list` or `population`."))))


  (testing "report type of subject-list is not possible with a GET request"
    (with-handler [handler]
      []
      (let [{:keys [status body]}
            @(handler
              {:request-method :get
               :params
               {"measure" "url-181501"
                "reportType" "subject-list"
                "periodStart" "2014"
                "periodEnd" "2015"}})]

        (is (= 422 status))

        (is (= :fhir/OperationOutcome (:fhir/type body)))

        (given (-> body :issue first)
          :severity := #fhir/code"error"
          :code := #fhir/code"not-supported"
          :diagnostics := "The reportType `subject-list` is not supported for GET requests. Please use POST."))))


  (testing "measure without library"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Measure :id "0"
               :url #fhir/uri"url-182126"}]]]

      (let [{:keys [status body]}
            @(handler
              {:params
               {"measure" "url-182126"
                "periodStart" "2014"
                "periodEnd" "2015"}})]

        (is (= 422 status))

        (is (= :fhir/OperationOutcome (:fhir/type body)))

        (given (-> body :issue first)
          :severity := #fhir/code"error"
          :code := #fhir/code"not-supported"
          :diagnostics := "Missing primary library. Currently only CQL expressions together with one primary library are supported."
          [:expression first] := "Measure.library"))))


  (testing "measure with non-existing library"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Measure :id "0"
               :url #fhir/uri"url-181501"
               :library [#fhir/canonical"library-url-094115"]}]]]

      (let [{:keys [status body]}
            @(handler
              {:params
               {"measure" "url-181501"
                "periodStart" "2014"
                "periodEnd" "2015"}})]

        (is (= 400 status))

        (is (= :fhir/OperationOutcome (:fhir/type body)))

        (given (-> body :issue first)
          :severity := #fhir/code"error"
          :code := #fhir/code"value"
          :diagnostics := "Can't find the library with canonical URI `library-url-094115`."
          [:expression first] := "Measure.library"))))


  (testing "missing content in library"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Measure :id "0"
               :url #fhir/uri"url-182104"
               :library [#fhir/canonical"library-url-094115"]}]
        [:put {:fhir/type :fhir/Library :id "0"
               :url #fhir/uri"library-url-094115"}]]]

      (let [{:keys [status body]}
            @(handler
              {:params
               {"measure" "url-182104"
                "periodStart" "2014"
                "periodEnd" "2015"}})]

        (is (= 400 status))

        (is (= :fhir/OperationOutcome (:fhir/type body)))

        (given (-> body :issue first)
          :severity := #fhir/code"error"
          :code := #fhir/code"value"
          :diagnostics := "Missing content in library with id `0`."
          [:expression first] := "Library.content"))))


  (testing "non text/cql content type"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Measure :id "0"
               :url #fhir/uri"url-182051"
               :library [#fhir/canonical"library-url-094115"]}]
        [:put {:fhir/type :fhir/Library :id "0"
               :url #fhir/uri"library-url-094115"
               :content
               [#fhir/Attachment{:contentType #fhir/code"text/plain"}]}]]]

      (let [{:keys [status body]}
            @(handler
              {:params
               {"measure" "url-182051"
                "periodStart" "2014"
                "periodEnd" "2015"}})]

        (is (= 400 status))

        (is (= :fhir/OperationOutcome (:fhir/type body)))

        (given (-> body :issue first)
          :severity := #fhir/code"error"
          :code := #fhir/code"value"
          :diagnostics := "Non `text/cql` content type of `text/plain` of first attachment in library with id `0`."
          [:expression first] := "Library.content[0].contentType"))))


  (testing "missing data in library content"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Measure :id "0"
               :url #fhir/uri"url-182039"
               :library [#fhir/canonical"library-url-094115"]}]
        [:put {:fhir/type :fhir/Library :id "0"
               :url #fhir/uri"library-url-094115"
               :content
               [#fhir/Attachment{:contentType #fhir/code"text/cql"}]}]]]

      (let [{:keys [status body]}
            @(handler
              {:params
               {"measure" "url-182039"
                "periodStart" "2014"
                "periodEnd" "2015"}})]

        (is (= 400 status))

        (is (= :fhir/OperationOutcome (:fhir/type body)))

        (given (-> body :issue first)
          :severity := #fhir/code"error"
          :code := #fhir/code"value"
          :diagnostics := "Missing embedded data of first attachment in library with id `0`."
          [:expression first] := "Library.content[0].data"))))


  (testing "Success"
    (testing "on type endpoint"
      (testing "as GET request"
        (testing "cohort scoring"
          (with-handler [handler]
            [[[:put
               {:fhir/type :fhir/Measure :id "0"
                :url #fhir/uri"url-181501"
                :library [#fhir/canonical"library-url-094115"]
                :scoring (scoring-concept "cohort")
                :group
                [{:fhir/type :fhir.Measure/group
                  :population
                  [{:fhir/type :fhir.Measure.group/population
                    :code (population-concept "initial-population")
                    :criteria (cql-expression "InInitialPopulation")}]}]}]
              [:put
               {:fhir/type :fhir/Library :id "0"
                :url #fhir/uri"library-url-094115"
                :content
                [#fhir/Attachment
                    {:contentType #fhir/code"text/cql"
                     :data #fhir/base64Binary"bGlicmFyeSBSZXRyaWV2ZQp1c2luZyBGSElSIHZlcnNpb24gJzQuMC4wJwppbmNsdWRlIEZISVJIZWxwZXJzIHZlcnNpb24gJzQuMC4wJwoKY29udGV4dCBQYXRpZW50CgpkZWZpbmUgSW5Jbml0aWFsUG9wdWxhdGlvbjoKICBQYXRpZW50LmdlbmRlciA9ICdtYWxlJwo="}]}]
              [:put
               {:fhir/type :fhir/Patient
                :id "0"
                :gender #fhir/code"male"}]]]

            (let [{:keys [status body]}
                  @(handler
                    {:request-method :get
                     :params
                     {"measure" "url-181501"
                      "periodStart" "2014"
                      "periodEnd" "2015"}})]

              (is (= 200 status))

              (given body
                :fhir/type := :fhir/MeasureReport
                [:extension 0 :url] := "https://samply.github.io/blaze/fhir/StructureDefinition/eval-duration"
                [:extension 0 :value :code] := #fhir/code"s"
                [:extension 0 :value :system] := #fhir/uri"http://unitsofmeasure.org"
                [:extension 0 :value :unit] := "s"
                [:extension 0 :value :value] :instanceof BigDecimal
                :status := #fhir/code"complete"
                :type := #fhir/code"summary"
                :measure := #fhir/canonical"url-181501"
                :date := #fhir/dateTime"1970-01-01T00:00:00Z"
                [:period :start] := #fhir/dateTime"2014"
                [:period :end] := #fhir/dateTime"2015"
                [:group 0 :population 0 :code :coding 0 :system]
                := #fhir/uri"http://terminology.hl7.org/CodeSystem/measure-population"
                [:group 0 :population 0 :code :coding 0 :code]
                := #fhir/code"initial-population"
                [:group 0 :population 0 :count] := 1))))

        (testing "cohort scoring with stratifiers"
          (with-handler [handler]
            [[[:put
               {:fhir/type :fhir/Measure :id "0"
                :url #fhir/uri"url-181501"
                :library [#fhir/canonical"library-url-094115"]
                :scoring (scoring-concept "cohort")
                :group
                [{:fhir/type :fhir.Measure/group
                  :population
                  [{:fhir/type :fhir.Measure.group/population
                    :code (population-concept "initial-population")
                    :criteria (cql-expression "InInitialPopulation")}]
                  :stratifier
                  [{:fhir/type :fhir.Measure.group/stratifier
                    :code #fhir/CodeableConcept{:text "gender"}
                    :criteria (cql-expression "Gender")}]}]}]
              [:put
               {:fhir/type :fhir/Library :id "0"
                :url #fhir/uri"library-url-094115"
                :content [library-content]}]
              [:put
               {:fhir/type :fhir/Patient
                :id "0"
                :gender #fhir/code"male"}]
              [:put
               {:fhir/type :fhir/Patient
                :id "1"
                :gender #fhir/code"female"}]
              [:put
               {:fhir/type :fhir/Patient
                :id "2"
                :gender #fhir/code"female"}]]]

            (let [{:keys [status body]}
                  @(handler
                    {:request-method :get
                     :params
                     {"measure" "url-181501"
                      "periodStart" "2014"
                      "periodEnd" "2015"}})]

              (is (= 200 status))

              (given body
                :fhir/type := :fhir/MeasureReport
                :status := #fhir/code"complete"
                :type := #fhir/code"summary"
                :measure := #fhir/canonical"url-181501"
                :date := #fhir/dateTime"1970-01-01T00:00:00Z"
                [:period :start] := #fhir/dateTime"2014"
                [:period :end] := #fhir/dateTime"2015"
                [:group 0 :population 0 :code :coding 0 :system]
                := #fhir/uri"http://terminology.hl7.org/CodeSystem/measure-population"
                [:group 0 :population 0 :code :coding 0 :code]
                := #fhir/code"initial-population"
                [:group 0 :population 0 :count] := 3
                [:group 0 :stratifier 0 :code 0 :text] := "gender"
                [:group 0 :stratifier 0 :stratum 0 :population 0 :code :coding 0 :system]
                := #fhir/uri"http://terminology.hl7.org/CodeSystem/measure-population"
                [:group 0 :stratifier 0 :stratum 0 :population 0 :code :coding 0 :code]
                := #fhir/code"initial-population"
                [:group 0 :stratifier 0 :stratum 0 :population 0 :count] := 2
                [:group 0 :stratifier 0 :stratum 0 :value :text] := "female"
                [:group 0 :stratifier 0 :stratum 1 :population 0 :code :coding 0 :system]
                := #fhir/uri"http://terminology.hl7.org/CodeSystem/measure-population"
                [:group 0 :stratifier 0 :stratum 1 :population 0 :code :coding 0 :code]
                := #fhir/code"initial-population"
                [:group 0 :stratifier 0 :stratum 1 :population 0 :count] := 1
                [:group 0 :stratifier 0 :stratum 1 :value :text] := "male")))))

      (testing "as POST request"
        (with-handler [handler]
          [[[:put {:fhir/type :fhir/Measure :id "0"
                   :url #fhir/uri"url-181501"
                   :library [#fhir/canonical"library-url-094115"]}]
            [:put {:fhir/type :fhir/Library :id "0"
                   :url #fhir/uri"library-url-094115"
                   :content [library-content]}]]]

          (let [{:keys [status headers body]}
                @(handler
                  {:request-method :post
                   :params
                   {"measure" "url-181501"
                    "periodStart" "2014"
                    "periodEnd" "2015"}})]

            (is (= 201 status))

            (testing "Location header"
              (is (= "base-url-144638/MeasureReport/AAAAAAAAAAAAAAAA/_history/2"
                     (get headers "Location"))))

            (given body
              :fhir/type := :fhir/MeasureReport
              :status := #fhir/code"complete"
              :type := #fhir/code"summary"
              :measure := #fhir/canonical"url-181501"
              :date := #fhir/dateTime"1970-01-01T00:00:00Z"
              [:period :start] := #fhir/dateTime"2014"
              [:period :end] := #fhir/dateTime"2015")))))

    (testing "on instance endpoint"
      (testing "as GET request"
        (with-handler [handler]
          [[[:put {:fhir/type :fhir/Measure :id "0"
                   :url #fhir/uri"url-181501"
                   :library [#fhir/canonical"library-url-094115"]}]
            [:put {:fhir/type :fhir/Library :id "0"
                   :url #fhir/uri"library-url-094115"
                   :content [library-content]}]]]

          (let [{:keys [status body]}
                @(handler
                  {:request-method :get
                   :path-params {:id "0"}
                   :params
                   {"periodStart" "2014"
                    "periodEnd" "2015"}})]

            (is (= 200 status))

            (given body
              :fhir/type := :fhir/MeasureReport
              :status := #fhir/code"complete"
              :type := #fhir/code"summary"
              :measure := #fhir/canonical"url-181501"
              :date := #fhir/dateTime"1970-01-01T00:00:00Z"
              [:period :start] := #fhir/dateTime"2014"
              [:period :end] := #fhir/dateTime"2015"))))

      (testing "as POST request"
        (with-handler [handler]
          [[[:put {:fhir/type :fhir/Measure :id "0"
                   :url #fhir/uri"url-181501"
                   :library [#fhir/canonical"library-url-094115"]}]
            [:put {:fhir/type :fhir/Library :id "0"
                   :url #fhir/uri"library-url-094115"
                   :content [library-content]}]]]

          (let [{:keys [status headers body]}
                @(handler
                  {:request-method :post
                   :path-params {:id "0"}
                   :params
                   {"periodStart" "2014"
                    "periodEnd" "2015"}})]

            (is (= 201 status))

            (testing "Location header"
              (is (= "base-url-144638/MeasureReport/AAAAAAAAAAAAAAAA/_history/2"
                     (get headers "Location"))))

            (given body
              :fhir/type := :fhir/MeasureReport
              :status := #fhir/code"complete"
              :type := #fhir/code"summary"
              :measure := #fhir/canonical"url-181501"
              :date := #fhir/dateTime"1970-01-01T00:00:00Z"
              [:period :start] := #fhir/dateTime"2014"
              [:period :end] := #fhir/dateTime"2015")))))))


(deftest executor-test
  (let [system (ig/init {::evaluate-measure/executor {}})]
    (is (instance? ExecutorService (::evaluate-measure/executor system)))
    (ig/halt! system)))
