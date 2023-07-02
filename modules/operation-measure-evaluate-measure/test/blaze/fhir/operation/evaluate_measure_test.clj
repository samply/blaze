(ns blaze.fhir.operation.evaluate-measure-test
  (:require
    [blaze.anomaly-spec]
    [blaze.async.comp :as ac]
    [blaze.db.api-stub :refer [mem-node-config with-system-data]]
    [blaze.db.resource-store :as rs]
    [blaze.executors :as ex]
    [blaze.fhir.operation.evaluate-measure :as evaluate-measure]
    [blaze.fhir.operation.evaluate-measure.test-util :refer [wrap-error]]
    [blaze.fhir.spec.type :as type]
    [blaze.metrics.spec]
    [blaze.middleware.fhir.db :refer [wrap-db]]
    [blaze.middleware.fhir.db-spec]
    [blaze.test-util :as tu :refer [given-thrown with-system]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [integrant.core :as ig]
    [java-time.api :as time]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log]))


(set! *warn-on-reflection* true)
(st/instrument)
(log/set-level! :trace)


(test/use-fixtures :each tu/fixture)


(def ^:private base-url "base-url-144638")


(def ^:private measure-population-uri
  #fhir/uri"http://terminology.hl7.org/CodeSystem/measure-population")


(def router
  (reitit/router
    [["/MeasureReport" {:name :MeasureReport/type}]]
    {:syntax :bracket}))


(defn- scoring-concept [code]
  (type/codeable-concept
    {:coding
     [(type/coding
        {:system #fhir/uri"http://terminology.hl7.org/CodeSystem/measure-scoring"
         :code (type/code code)})]}))


(defn- population-concept [code]
  (type/codeable-concept
    {:coding
     [(type/coding
        {:system measure-population-uri
         :code (type/code code)})]}))


(defn- cql-expression [expr]
  {:fhir/type :fhir/Expression
   :language #fhir/code"text/cql-identifier"
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


(deftest timeout-init-test
  (testing "nil config"
    (given-thrown (ig/init {::evaluate-measure/timeout nil})
      :key := ::evaluate-measure/timeout
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {::evaluate-measure/timeout {}})
      :key := ::evaluate-measure/timeout
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :millis))))

  (testing "invalid millis"
    (given-thrown (ig/init {::evaluate-measure/timeout {:millis ::invalid}})
      :key := ::evaluate-measure/timeout
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `nat-int?
      [:explain ::s/problems 0 :val] := ::invalid))

  (testing "init"
    (with-system [{::evaluate-measure/keys [timeout]}
                  {::evaluate-measure/timeout {:millis 154912}}]
      (is (= (time/millis 154912) timeout)))))


(deftest executor-init-test
  (testing "nil config"
    (given-thrown (ig/init {::evaluate-measure/executor nil})
      :key := ::evaluate-measure/executor
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "invalid num-threads"
    (given-thrown (ig/init {::evaluate-measure/executor {:num-threads ::invalid}})
      :key := ::evaluate-measure/executor
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `pos-int?
      [:explain ::s/problems 0 :val] := ::invalid))

  (testing "with default num-threads"
    (with-system [{::evaluate-measure/keys [executor]}
                  {::evaluate-measure/executor {}}]
      (is (ex/executor? executor)))))


(deftest compile-duration-seconds-collector-init-test
  (with-system [{collector ::evaluate-measure/compile-duration-seconds}
                {::evaluate-measure/compile-duration-seconds nil}]
    (is (s/valid? :blaze.metrics/collector collector))))


(deftest evaluate-duration-seconds-collector-init-test
  (with-system [{collector ::evaluate-measure/evaluate-duration-seconds}
                {::evaluate-measure/evaluate-duration-seconds nil}]
    (is (s/valid? :blaze.metrics/collector collector))))


(def config
  (assoc mem-node-config
    ::evaluate-measure/handler
    {:node (ig/ref :blaze.db/node)
     :executor (ig/ref :blaze.test/executor)
     :clock (ig/ref :blaze.test/fixed-clock)
     :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}
    :blaze.test/executor {}
    :blaze.test/fixed-rng-fn {}))


(defn wrap-defaults [handler]
  (fn [request]
    (handler
      (assoc request
        :blaze/base-url base-url
        ::reitit/router router))))


(defmacro with-handler [[handler-binding] & more]
  (let [[txs body] (tu/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         handler# ::evaluate-measure/handler} config]
       ~txs
       (let [~handler-binding (-> handler# wrap-defaults (wrap-db node# 100)
                                  wrap-error)]
         ~@body))))


(deftest handler-test
  (testing "Returns Not Found on Non-Existing Measure"
    (testing "on instance endpoint"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler
                 {:path-params {:id "0"}
                  :params {"periodStart" "2014" "periodEnd" "2015"}})]

          (is (= 404 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"not-found"
            [:issue 0 :diagnostics] := "The Measure resource with id `0` was not found."))))

    (testing "on type endpoint"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler
                 {:params
                  {"measure" "url-181501"
                   "periodStart" "2014"
                   "periodEnd" "2015"}})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"not-found"
            [:issue 0 :diagnostics] := "The Measure resource with reference `url-181501` was not found.")))

      (testing "with missing measure parameter"
        (with-handler [handler]
          (let [{:keys [status body]}
                @(handler
                   {:params
                    {"periodStart" "2014"
                     "periodEnd" "2015"}})]

            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"required"
              [:issue 0 :diagnostics] := "The measure parameter is missing."))))))

  (testing "Returns Gone on Deleted Resource"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Measure :id "0"}]]
       [[:delete "Measure" "0"]]]

      (let [{:keys [status body]}
            @(handler
               {:path-params {:id "0"}
                :params {"periodStart" "2014" "periodEnd" "2015"}})]

        (is (= 410 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code"error"
          [:issue 0 :code] := #fhir/code"deleted"
          [:issue 0 :diagnostics] := "The Measure resource with the id `0` was deleted."))))

  (testing "missing measure content"
    (with-redefs [rs/get (fn [_ _] (ac/completed-future nil))]
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Measure :id "0"}]]]

        (let [{:keys [status body]}
              @(handler
                 {:path-params {:id "0"}
                  :params
                  {"periodStart" "2014"
                   "periodEnd" "2015"}})]

          (is (= 500 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"incomplete"
            [:issue 0 :diagnostics] := "The resource content of `Measure/0` with hash `D0CCBAF739DAC930C5A0844A48CDE18F0004D4549CEF7E1FF0DEB9A0611D9451` was not found.")))))

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

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code"error"
          [:issue 0 :code] := #fhir/code"not-supported"
          [:issue 0 :diagnostics] := "Missing primary library. Currently only CQL expressions together with one primary library are supported."
          [:issue 0 :expression first] := "Measure.library"))))

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

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code"error"
          [:issue 0 :code] := #fhir/code"value"
          [:issue 0 :diagnostics] := "The Library resource with canonical URI `library-url-094115` was not found."
          [:issue 0 :expression first] := "Measure.library"))))

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

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code"error"
          [:issue 0 :code] := #fhir/code"value"
          [:issue 0 :diagnostics] := "Missing content in library with id `0`."
          [:issue 0 :expression first] := "Library.content"))))

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

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code"error"
          [:issue 0 :code] := #fhir/code"value"
          [:issue 0 :diagnostics] := "Non `text/cql` content type of `text/plain` of first attachment in library with id `0`."
          [:issue 0 :expression first] := "Library.content[0].contentType"))))

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

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code"error"
          [:issue 0 :code] := #fhir/code"value"
          [:issue 0 :diagnostics] := "Missing embedded data of first attachment in library with id `0`."
          [:issue 0 :expression first] := "Library.content[0].data"))))

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
                [:extension 0 :value :unit] := #fhir/string"s"
                [:extension 0 :value :value] :instanceof BigDecimal
                :status := #fhir/code"complete"
                :type := #fhir/code"summary"
                :measure := #fhir/canonical"url-181501"
                :date := #fhir/dateTime"1970-01-01T00:00:00Z"
                [:period :start] := #fhir/dateTime"2014"
                [:period :end] := #fhir/dateTime"2015"
                [:group 0 :population 0 :code :coding 0 :system] := measure-population-uri
                [:group 0 :population 0 :code :coding 0 :code] := #fhir/code"initial-population"
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
                    :code #fhir/CodeableConcept{:text #fhir/string"gender"}
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
                [:group 0 :population 0 :code :coding 0 :system] := measure-population-uri
                [:group 0 :population 0 :code :coding 0 :code] := #fhir/code"initial-population"
                [:group 0 :population 0 :count] := 3
                [:group 0 :stratifier 0 :code 0 :text] := #fhir/string"gender"
                [:group 0 :stratifier 0 :stratum 0 :population 0 :code :coding 0 :system] := measure-population-uri
                [:group 0 :stratifier 0 :stratum 0 :population 0 :code :coding 0 :code] := #fhir/code"initial-population"
                [:group 0 :stratifier 0 :stratum 0 :population 0 :count] := 2
                [:group 0 :stratifier 0 :stratum 0 :value :text] := #fhir/string"female"
                [:group 0 :stratifier 0 :stratum 1 :population 0 :code :coding 0 :system] := measure-population-uri
                [:group 0 :stratifier 0 :stratum 1 :population 0 :code :coding 0 :code] := #fhir/code"initial-population"
                [:group 0 :stratifier 0 :stratum 1 :population 0 :count] := 1
                [:group 0 :stratifier 0 :stratum 1 :value :text] := #fhir/string"male")))))

      (testing "as POST request"
        (testing "with no Prefer header"
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
                      :body
                      {:fhir/type :fhir/Parameters
                       :parameter
                       [{:fhir/type :fhir.Parameters/parameter
                         :name "measure"
                         :value #fhir/string"url-181501"}
                        {:fhir/type :fhir.Parameters/parameter
                         :name "periodStart"
                         :value #fhir/date"2014"}
                        {:fhir/type :fhir.Parameters/parameter
                         :name "periodEnd"
                         :value #fhir/date"2015"}]}})]

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
                [:period :end] := #fhir/dateTime"2015"))))

        (testing "with return=minimal Prefer header"
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
                      :headers {"prefer" "return=minimal"}
                      :body
                      {:fhir/type :fhir/Parameters
                       :parameter
                       [{:fhir/type :fhir.Parameters/parameter
                         :name "measure"
                         :value #fhir/string"url-181501"}
                        {:fhir/type :fhir.Parameters/parameter
                         :name "periodStart"
                         :value #fhir/date"2014"}
                        {:fhir/type :fhir.Parameters/parameter
                         :name "periodEnd"
                         :value #fhir/date"2015"}]}})]

              (is (= 201 status))

              (testing "Location header"
                (is (= "base-url-144638/MeasureReport/AAAAAAAAAAAAAAAA/_history/2"
                       (get headers "Location"))))

              (is (nil? body)))))))

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
                    :body
                    {:fhir/type :fhir/Parameters
                     :parameter
                     [{:fhir/type :fhir.Parameters/parameter
                       :name "periodStart"
                       :value #fhir/date"2014"}
                      {:fhir/type :fhir.Parameters/parameter
                       :name "periodEnd"
                       :value #fhir/date"2015"}]}})]

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


(deftest indexer-executor-shutdown-timeout-test
  (let [{::evaluate-measure/keys [executor] :as system}
        (ig/init {::evaluate-measure/executor {}})]

    ;; will produce a timeout, because the function runs 11 seconds
    (ex/execute! executor #(Thread/sleep 11000))

    ;; ensure that the function is called before the scheduler is halted
    (Thread/sleep 100)

    (ig/halt! system)

    ;; the scheduler is shut down
    (is (ex/shutdown? executor))

    ;; but it isn't terminated yet
    (is (not (ex/terminated? executor)))))
