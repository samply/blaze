(ns blaze.fhir.operation.evaluate-measure-test
  (:require
   [blaze.anomaly-spec]
   [blaze.async.comp :as ac]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.db.resource-store :as rs]
   [blaze.db.spec]
   [blaze.elm.expression :as-alias expr]
   [blaze.elm.expression.cache-spec]
   [blaze.executors :as ex]
   [blaze.fhir.operation.evaluate-measure :as evaluate-measure]
   [blaze.fhir.operation.evaluate-measure.measure-spec]
   [blaze.fhir.operation.evaluate-measure.test-util :refer [wrap-error]]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util]
   [blaze.fhir.util :as fu]
   [blaze.metrics.spec]
   [blaze.middleware.fhir.db :refer [wrap-db]]
   [blaze.middleware.fhir.db-spec]
   [blaze.module-spec]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.spec]
   [blaze.terminology-service :as-alias ts]
   [blaze.terminology-service-spec]
   [blaze.test-util :as tu]
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
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private base-url "base-url-144638")

(def ^:private measure-population-uri
  #fhir/uri "http://terminology.hl7.org/CodeSystem/measure-population")

(def ^:private router
  (reitit/router
   [["/MeasureReport" {:name :MeasureReport/type}]]
   {:syntax :bracket}))

(defn- scoring-concept [code]
  (type/codeable-concept
   {:coding
    [(type/coding
      {:system #fhir/uri "http://terminology.hl7.org/CodeSystem/measure-scoring"
       :code (type/code code)})]}))

(defn- population-concept [code]
  (type/codeable-concept
   {:coding
    [(type/coding
      {:system measure-population-uri
       :code (type/code code)})]}))

(defn- cql-expression [expr]
  (type/expression {:language #fhir/code "text/cql-identifier"
                    :expression (type/string expr)}))

(def cql-attachment
  #fhir/Attachment
   {:contentType #fhir/code "text/cql"
    :data #fhir/base64Binary "bGlicmFyeSBSZXRyaWV2ZQp1c2luZyBGSElSIHZlcnNpb24gJzQuMC4wJwppbmNsdWRlIEZISVJIZWxwZXJzIHZlcnNpb24gJzQuMC4wJwoKY29udGV4dCBQYXRpZW50CgpkZWZpbmUgSW5Jbml0aWFsUG9wdWxhdGlvbjoKICB0cnVlCgpkZWZpbmUgR2VuZGVyOgogIFBhdGllbnQuZ2VuZGVyCg=="})

(def ^:private config
  (assoc
   api-stub/mem-node-config
   ::evaluate-measure/handler
   {:node (ig/ref :blaze.db/node)
    ::expr/cache (ig/ref ::expr/cache)
    :terminology-service (ig/ref ::ts/local)
    :executor (ig/ref :blaze.test/executor)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}
   :blaze/job-scheduler
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}
   ::expr/cache
   {:node (ig/ref :blaze.db/node)
    :executor (ig/ref :blaze.test/executor)}
   :blaze.test/executor {}))

(deftest init-test
  (testing "nil config"
    (given-failed-system {::evaluate-measure/handler nil}
      :key := ::evaluate-measure/handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {::evaluate-measure/handler {}}
      :key := ::evaluate-measure/handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :terminology-service))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :executor))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 4 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))))

  (testing "invalid node"
    (given-failed-system (assoc-in config [::evaluate-measure/handler :node] ::invalid)
      :key := ::evaluate-measure/handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/node]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid terminology-service"
    (given-failed-system (assoc-in config [::evaluate-measure/handler :terminology-service] ::invalid)
      :key := ::evaluate-measure/handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/terminology-service]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid executor"
    (given-failed-system (assoc-in config [::evaluate-measure/handler :executor] ::invalid)
      :key := ::evaluate-measure/handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::evaluate-measure/executor]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid clock"
    (given-failed-system (assoc-in config [::evaluate-measure/handler :clock] ::invalid)
      :key := ::evaluate-measure/handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/clock]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid rng-fn"
    (given-failed-system (assoc-in config [::evaluate-measure/handler :rng-fn] ::invalid)
      :key := ::evaluate-measure/handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/rng-fn]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "init"
    (with-system [{::evaluate-measure/keys [handler]} config]
      (is (fn? handler)))

    (testing "with timeout"
      (with-system [{::evaluate-measure/keys [handler]}
                    (assoc-in config [::evaluate-measure/handler :timeout] (time/seconds 1))]
        (is (fn? handler))))))

(deftest timeout-init-test
  (testing "nil config"
    (given-failed-system {::evaluate-measure/timeout nil}
      :key := ::evaluate-measure/timeout
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {::evaluate-measure/timeout {}}
      :key := ::evaluate-measure/timeout
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :millis))))

  (testing "invalid millis"
    (given-failed-system {::evaluate-measure/timeout {:millis ::invalid}}
      :key := ::evaluate-measure/timeout
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.fhir.operation.evaluate-measure.timeout/millis]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "init"
    (with-system [{::evaluate-measure/keys [timeout]}
                  {::evaluate-measure/timeout {:millis 154912}}]
      (is (= (time/millis 154912) timeout)))))

(deftest executor-init-test
  (testing "nil config"
    (given-failed-system {::evaluate-measure/executor nil}
      :key := ::evaluate-measure/executor
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "invalid num-threads"
    (given-failed-system {::evaluate-measure/executor {:num-threads ::invalid}}
      :key := ::evaluate-measure/executor
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.fhir.operation.evaluate-measure.executor/num-threads]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "init with default number of threads"
    (with-system [{::evaluate-measure/keys [executor]}
                  {::evaluate-measure/executor {}}]
      (is (ex/executor? executor))))

  (testing "init with given number of threads"
    (with-system [{::evaluate-measure/keys [executor]}
                  {::evaluate-measure/executor {:num-threads 4}}]
      (is (ex/executor? executor)))))

(deftest compile-duration-seconds-collector-init-test
  (with-system [{collector ::evaluate-measure/compile-duration-seconds}
                {::evaluate-measure/compile-duration-seconds nil}]
    (is (s/valid? :blaze.metrics/collector collector))))

(deftest evaluate-duration-seconds-collector-init-test
  (with-system [{collector ::evaluate-measure/evaluate-duration-seconds}
                {::evaluate-measure/evaluate-duration-seconds nil}]
    (is (s/valid? :blaze.metrics/collector collector))))

(defn- wrap-defaults [handler]
  (fn [request]
    (handler
     (assoc request
            :blaze/base-url base-url
            ::reitit/router router))))

(defn- wrap-job-scheduler [handler job-scheduler]
  (fn [request]
    (handler (assoc request :blaze/job-scheduler job-scheduler))))

(defmacro with-handler [[handler-binding] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         job-scheduler# :blaze/job-scheduler
                         handler# ::evaluate-measure/handler} config]
       ~txs
       (let [~handler-binding (-> handler# wrap-defaults (wrap-db node# 100)
                                  (wrap-job-scheduler job-scheduler#)
                                  wrap-error)]
         ~@body))))

(deftest handler-instance-test
  (testing "Returns Success"
    (testing "Sync"
      (testing "as GET request"
        (doseq [content [[cql-attachment]
                         [#fhir/Attachment{:contentType #fhir/code "text/plain"}
                          cql-attachment]]]
          (with-handler [handler]
            [[[:put {:fhir/type :fhir/Measure :id "0"
                     :url #fhir/uri "url-181501"
                     :library [#fhir/canonical "library-url-094115"]}]
              [:put {:fhir/type :fhir/Library :id "0"
                     :url #fhir/uri "library-url-094115"
                     :content content}]]]

            (let [{:keys [status body]}
                  @(handler
                    {:request-method :get
                     :path-params {:id "0"}
                     :params {"periodStart" "2014"
                              "periodEnd" "2015"}})]

              (is (= 200 status))

              (given body
                :fhir/type := :fhir/MeasureReport
                :status := #fhir/code "complete"
                :type := #fhir/code "summary"
                :measure := #fhir/canonical "url-181501"
                :date := #fhir/dateTime #system/date-time "1970-01-01T00:00:00Z"
                [:period :start] := #fhir/dateTime #system/date-time "2014"
                [:period :end] := #fhir/dateTime #system/date-time "2015")))))

      (testing "as POST request"
        (with-handler [handler]
          [[[:put {:fhir/type :fhir/Measure :id "0"
                   :url #fhir/uri "url-181501"
                   :library [#fhir/canonical "library-url-094115"]}]
            [:put {:fhir/type :fhir/Library :id "0"
                   :url #fhir/uri "library-url-094115"
                   :content [cql-attachment]}]]]

          (let [{:keys [status headers body]}
                @(handler
                  {:request-method :post
                   :path-params {:id "0"}
                   :body
                   (fu/parameters
                    "periodStart" #fhir/date #system/date "2014"
                    "periodEnd" #fhir/date #system/date "2015")})]

            (is (= 201 status))

            (testing "Location header"
              (is (= "base-url-144638/MeasureReport/AAAAAAAAAAAAAAAA/_history/2"
                     (get headers "Location"))))

            (given body
              :fhir/type := :fhir/MeasureReport
              :status := #fhir/code "complete"
              :type := #fhir/code "summary"
              :measure := #fhir/canonical "url-181501"
              :date := #fhir/dateTime #system/date-time "1970-01-01T00:00:00Z"
              [:period :start] := #fhir/dateTime #system/date-time "2014"
              [:period :end] := #fhir/dateTime #system/date-time "2015"))))))

  (testing "Returns Not Found on Non-Existing Measure"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler
              {:path-params {:id "0"}
               :params {"periodStart" "2014"
                        "periodEnd" "2015"}})]

        (is (= 404 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "not-found"
          [:issue 0 :diagnostics] := #fhir/string "The Measure resource with id `0` was not found."))))

  (testing "Returns Gone on Deleted Resource"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Measure :id "0"}]]
       [[:delete "Measure" "0"]]]

      (let [{:keys [status body]}
            @(handler
              {:path-params {:id "0"}
               :params {"periodStart" "2014"
                        "periodEnd" "2015"}})]

        (is (= 410 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "deleted"
          [:issue 0 :diagnostics] := #fhir/string "The Measure resource with the id `0` was deleted."))))

  (testing "Returns Unprocessable Entity on Measure without Library"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Measure :id "0"}]]]

      (let [{:keys [status body]}
            @(handler
              {:path-params {:id "0"}
               :params {"periodStart" "2014"
                        "periodEnd" "2015"}})]

        (is (= 422 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "not-supported"
          [:issue 0 :diagnostics] := #fhir/string "Missing primary library. Currently only CQL expressions together with one primary library are supported."))))

  (testing "Returns Bad Request on Measure with Non-Existing Library"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Measure :id "0"
               :library [#fhir/canonical "library-url-203737"]}]]]

      (let [{:keys [status body]}
            @(handler
              {:path-params {:id "0"}
               :params {"periodStart" "2014"
                        "periodEnd" "2015"}})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "value"
          [:issue 0 :diagnostics] := #fhir/string "The Library resource with canonical URI `library-url-203737` was not found."))))

  (testing "Returns Bad Request on Measure with Deleted Library"
    (doseq [library-ref [#fhir/canonical "library-url-203737"
                         #fhir/canonical "Library/0"
                         #fhir/canonical "/Library/0"]]
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Measure :id "0"
                 :library [library-ref]}]
          [:put {:fhir/type :fhir/Library :id "0"
                 :url #fhir/uri "library-url-203737"}]]
         [[:delete "Library" "0"]]]

        (let [{:keys [status body]}
              @(handler
                {:path-params {:id "0"}
                 :params {"periodStart" "2014"
                          "periodEnd" "2015"}})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "value"
            [:issue 0 :diagnostics] := (type/string (format "The Library resource with canonical URI `%s` was not found." (:value library-ref))))))))

  (testing "Returns Server Error on Missing Measure Content"
    (with-redefs [rs/get (fn [_ _] (ac/completed-future nil))]
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Measure :id "0"}]]]

        (let [{:keys [status body]}
              @(handler
                {:path-params {:id "0"}
                 :params {"periodStart" "2014"
                          "periodEnd" "2015"}})]

          (is (= 500 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "incomplete"
            [:issue 0 :diagnostics] := #fhir/string "The resource content of `Measure/0` with hash `D0CCBAF739DAC930C5A0844A48CDE18F0004D4549CEF7E1FF0DEB9A0611D9451` was not found."))))))

(deftest handler-type-test
  (testing "Returns Success"
    (testing "as GET request"
      (testing "Async"
        (with-handler [handler]
          [[[:put
             {:fhir/type :fhir/Measure :id "0"
              :url #fhir/uri "url-181501"
              :library [#fhir/canonical "Library/0"]
              :scoring (scoring-concept "cohort")
              :group
              [{:fhir/type :fhir.Measure/group
                :population
                [{:fhir/type :fhir.Measure.group/population
                  :code (population-concept "initial-population")
                  :criteria (cql-expression "InInitialPopulation")}]}]}]
            [:put
             {:fhir/type :fhir/Library :id "0"
              :url #fhir/uri "library-url-094115"
              :content
              [#fhir/Attachment
                {:contentType #fhir/code "text/cql"
                 :data #fhir/base64Binary "bGlicmFyeSBSZXRyaWV2ZQp1c2luZyBGSElSIHZlcnNpb24gJzQuMC4wJwppbmNsdWRlIEZISVJIZWxwZXJzIHZlcnNpb24gJzQuMC4wJwoKY29udGV4dCBQYXRpZW50CgpkZWZpbmUgSW5Jbml0aWFsUG9wdWxhdGlvbjoKICBQYXRpZW50LmdlbmRlciA9ICdtYWxlJwo="}]}]
            [:put
             {:fhir/type :fhir/Patient :id "0"
              :gender #fhir/code "male"}]]]

          (let [{:keys [status headers]}
                @(handler
                  {:request-method :get
                   :uri "/Measure"
                   :headers {"prefer" "respond-async"}
                   :params {"measure" "url-181501"
                            "periodStart" "2014"
                            "periodEnd" "2015"}})]

            (is (= 202 status))

            (testing "the Content-Location header contains the status endpoint URL"
              (is (= (get headers "Content-Location")
                     (str base-url "/__async-status/AAAAAAAAAAAAAAAA")))))))

      (testing "cohort scoring"
        (doseq [library-ref [#fhir/canonical"library-url-094115"
                             #fhir/canonical"Library/0"
                             #fhir/canonical"/Library/0"]
                cancelled?  [nil (constantly nil)]]
          (with-handler [handler]
            [[[:put
               {:fhir/type :fhir/Measure :id "0"
                :url #fhir/uri "url-181501"
                :library [library-ref]
                :scoring (scoring-concept "cohort")
                :group
                [{:fhir/type :fhir.Measure/group
                  :population
                  [{:fhir/type :fhir.Measure.group/population
                    :code (population-concept "initial-population")
                    :criteria (cql-expression "InInitialPopulation")}]}]}]
              [:put
               {:fhir/type :fhir/Library :id "0"
                :url #fhir/uri "library-url-094115"
                :content
                [#fhir/Attachment
                  {:contentType #fhir/code "text/cql"
                   :data #fhir/base64Binary "bGlicmFyeSBSZXRyaWV2ZQp1c2luZyBGSElSIHZlcnNpb24gJzQuMC4wJwppbmNsdWRlIEZISVJIZWxwZXJzIHZlcnNpb24gJzQuMC4wJwoKY29udGV4dCBQYXRpZW50CgpkZWZpbmUgSW5Jbml0aWFsUG9wdWxhdGlvbjoKICBQYXRpZW50LmdlbmRlciA9ICdtYWxlJwo="}]}]
              [:put
               {:fhir/type :fhir/Patient
                :id "0"
                :gender #fhir/code "male"}]]]

            (let [{:keys [status body]}
                  @(handler
                    {:request-method :get
                     :params {"measure" "url-181501"
                              "periodStart" "2014"
                              "periodEnd" "2015"}

                     :blaze/cancelled? cancelled?})]

              (is (= 200 status))

              (given body
                :fhir/type := :fhir/MeasureReport
                [:extension 0 :url] := "https://samply.github.io/blaze/fhir/StructureDefinition/eval-duration"
                [:extension 0 :value :code] := #fhir/code "s"
                [:extension 0 :value :system] := #fhir/uri "http://unitsofmeasure.org"
                [:extension 0 :value :unit] := #fhir/string "s"
                [:extension 0 :value :value :value] :? decimal?
                :status := #fhir/code "complete"
                :type := #fhir/code "summary"
                :measure := #fhir/canonical "url-181501"
                :date := #fhir/dateTime #system/date-time "1970-01-01T00:00:00Z"
                [:period :start] := #fhir/dateTime #system/date-time "2014"
                [:period :end] := #fhir/dateTime #system/date-time "2015"
                [:group 0 :population 0 :code :coding 0 :system] := measure-population-uri
                [:group 0 :population 0 :code :coding 0 :code] := #fhir/code "initial-population"
                [:group 0 :population 0 :count] := #fhir/integer 1)))))

      (testing "cohort scoring with stratifiers"
        (with-handler [handler]
          [[[:put
             {:fhir/type :fhir/Measure :id "0"
              :url #fhir/uri "url-181501"
              :library [#fhir/canonical "library-url-094115"]
              :scoring (scoring-concept "cohort")
              :group
              [{:fhir/type :fhir.Measure/group
                :population
                [{:fhir/type :fhir.Measure.group/population
                  :code (population-concept "initial-population")
                  :criteria (cql-expression "InInitialPopulation")}]
                :stratifier
                [{:fhir/type :fhir.Measure.group/stratifier
                  :code #fhir/CodeableConcept{:text #fhir/string "gender"}
                  :criteria (cql-expression "Gender")}]}]}]
            [:put
             {:fhir/type :fhir/Library :id "0"
              :url #fhir/uri "library-url-094115"
              :content [cql-attachment]}]
            [:put
             {:fhir/type :fhir/Patient
              :id "0"
              :gender #fhir/code "male"}]
            [:put
             {:fhir/type :fhir/Patient
              :id "1"
              :gender #fhir/code "female"}]
            [:put
             {:fhir/type :fhir/Patient
              :id "2"
              :gender #fhir/code "female"}]]]

          (let [{:keys [status body]}
                @(handler
                  {:request-method :get
                   :params {"measure" "url-181501"
                            "periodStart" "2014"
                            "periodEnd" "2015"}})]

            (is (= 200 status))

            (given body
              :fhir/type := :fhir/MeasureReport
              :status := #fhir/code "complete"
              :type := #fhir/code "summary"
              :measure := #fhir/canonical "url-181501"
              :date := #fhir/dateTime #system/date-time "1970-01-01T00:00:00Z"
              [:period :start] := #fhir/dateTime #system/date-time "2014"
              [:period :end] := #fhir/dateTime #system/date-time "2015"
              [:group 0 :population 0 :code :coding 0 :system] := measure-population-uri
              [:group 0 :population 0 :code :coding 0 :code] := #fhir/code "initial-population"
              [:group 0 :population 0 :count] := #fhir/integer 3
              [:group 0 :stratifier 0 :code 0 :text] := #fhir/string "gender"
              [:group 0 :stratifier 0 :stratum 0 :population 0 :code :coding 0 :system] := measure-population-uri
              [:group 0 :stratifier 0 :stratum 0 :population 0 :code :coding 0 :code] := #fhir/code "initial-population"
              [:group 0 :stratifier 0 :stratum 0 :population 0 :count] := #fhir/integer 1
              [:group 0 :stratifier 0 :stratum 0 :value :text] := #fhir/string "male"
              [:group 0 :stratifier 0 :stratum 1 :population 0 :code :coding 0 :system] := measure-population-uri
              [:group 0 :stratifier 0 :stratum 1 :population 0 :code :coding 0 :code] := #fhir/code "initial-population"
              [:group 0 :stratifier 0 :stratum 1 :population 0 :count] := #fhir/integer 2
              [:group 0 :stratifier 0 :stratum 1 :value :text] := #fhir/string "female")))))

    (testing "as POST request"
      (testing "with no Prefer header"
        (with-handler [handler]
          [[[:put {:fhir/type :fhir/Measure :id "0"
                   :url #fhir/uri "url-181501"
                   :library [#fhir/canonical "library-url-094115"]}]
            [:put {:fhir/type :fhir/Library :id "0"
                   :url #fhir/uri "library-url-094115"
                   :content [cql-attachment]}]]]

          (let [{:keys [status headers body]}
                @(handler
                  {:request-method :post
                   :body
                   (fu/parameters
                    "measure" #fhir/string "url-181501"
                    "periodStart" #fhir/date #system/date "2014"
                    "periodEnd" #fhir/date #system/date "2015")})]

            (is (= 201 status))

            (testing "Location header"
              (is (= "base-url-144638/MeasureReport/AAAAAAAAAAAAAAAA/_history/2"
                     (get headers "Location"))))

            (given body
              :fhir/type := :fhir/MeasureReport
              :status := #fhir/code "complete"
              :type := #fhir/code "summary"
              :measure := #fhir/canonical "url-181501"
              :date := #fhir/dateTime #system/date-time "1970-01-01T00:00:00Z"
              [:period :start] := #fhir/dateTime #system/date-time "2014"
              [:period :end] := #fhir/dateTime #system/date-time "2015")

            (testing "with return=minimal Prefer header"
              (with-handler [handler]
                [[[:put {:fhir/type :fhir/Measure :id "0"
                         :url #fhir/uri "url-181501"
                         :library [#fhir/canonical "library-url-094115"]}]
                  [:put {:fhir/type :fhir/Library :id "0"
                         :url #fhir/uri "library-url-094115"
                         :content [cql-attachment]}]]]

                (let [{:keys [status headers body]}
                      @(handler
                        {:request-method :post
                         :headers {"prefer" "return=minimal"}
                         :body
                         (fu/parameters
                          "measure" #fhir/string "url-181501"
                          "periodStart" #fhir/date #system/date "2014"
                          "periodEnd" #fhir/date #system/date "2015")})]

                  (is (= 201 status))

                  (testing "Location header"
                    (is (= "base-url-144638/MeasureReport/AAAAAAAAAAAAAAAA/_history/2"
                           (get headers "Location"))))

                  (is (nil? body))))))))

      (testing "Returns 4xx on Missing Measure Parameter"
        (testing "on GET requests"
          (with-handler [handler]
            (let [{:keys [status body]}
                  @(handler
                    {:params {"periodStart" "2014"
                              "periodEnd" "2015"}})]

              (is (= 400 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "required"
                [:issue 0 :diagnostics] := #fhir/string "The measure parameter is missing."))))

        (testing "on POST requests"
          (with-handler [handler]
            (let [{:keys [status body]}
                  @(handler
                    {:request-method :post
                     :body
                     (fu/parameters
                      "periodStart" #fhir/date #system/date "2014"
                      "periodEnd" #fhir/date #system/date "2015")})]

              (is (= 422 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "required"
                [:issue 0 :diagnostics] := #fhir/string "The measure parameter is missing.")))))

      (testing "Returns 4xx on Non-Existing Measure"
        (testing "on GET requests"
          (with-handler [handler]
            (let [{:keys [status body]}
                  @(handler
                    {:params {"measure" "url-181501"
                              "periodStart" "2014"
                              "periodEnd" "2015"}})]

              (is (= 400 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "not-found"
                [:issue 0 :diagnostics] := #fhir/string "The Measure resource with reference `url-181501` was not found."))))

        (testing "on POST requests"
          (with-handler [handler]
            (let [{:keys [status body]}
                  @(handler
                    {:request-method :post
                     :body
                     (fu/parameters
                      "periodStart" #fhir/date #system/date "2014"
                      "periodEnd" #fhir/date #system/date "2015"
                      "measure" #fhir/string "url-181501")})]

              (is (= 422 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "not-found"
                [:issue 0 :diagnostics] := #fhir/string "The Measure resource with reference `url-181501` was not found.")))))))

  (testing "Returns Bad Request"
    (testing "on Measure with Non-Existing Library"
      (testing "with URN canonical"
        (with-handler [handler]
          [[[:put {:fhir/type :fhir/Measure :id "0"
                   :url #fhir/uri "url-181501"
                   :library [#fhir/canonical "urn:uuid:98060091-4638-497d-ba99-7a0084ab17f6"]}]]]

          (let [{:keys [status body]}
                @(handler
                  {:params {"measure" "url-181501"
                            "periodStart" "2014"
                            "periodEnd" "2015"}})]

            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "value"
              [:issue 0 :diagnostics] := #fhir/string "The Library resource with canonical URI `urn:uuid:98060091-4638-497d-ba99-7a0084ab17f6` was not found."
              [:issue 0 :expression first] := #fhir/string "Measure.library"))))

      (testing "with literal reference"
        (testing "with non Library type"
          (with-handler [handler]
            [[[:put {:fhir/type :fhir/Patient :id "0"}]
              [:put {:fhir/type :fhir/Measure :id "0"
                     :url #fhir/uri "url-181501"
                     :library [#fhir/canonical "Patient/0"]}]]]

            (let [{:keys [status body]}
                  @(handler
                    {:params {"measure" "url-181501"
                              "periodStart" "2014"
                              "periodEnd" "2015"}})]

              (is (= 400 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "value"
                [:issue 0 :diagnostics] := #fhir/string "The Library resource with canonical URI `Patient/0` was not found."
                [:issue 0 :expression first] := #fhir/string "Measure.library"))))

        (testing "with non existing id"
          (with-handler [handler]
            [[[:put {:fhir/type :fhir/Library :id "0"}]
              [:put {:fhir/type :fhir/Measure :id "0"
                     :url #fhir/uri "url-181501"
                     :library [#fhir/canonical "Library/1"]}]]]

            (let [{:keys [status body]}
                  @(handler
                    {:params {"measure" "url-181501"
                              "periodStart" "2014"
                              "periodEnd" "2015"}})]

              (is (= 400 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "value"
                [:issue 0 :diagnostics] := #fhir/string "The Library resource with canonical URI `Library/1` was not found."
                [:issue 0 :expression first] := #fhir/string "Measure.library"))))))

    (testing "on Missing Content in Library"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Measure :id "0"
                 :url #fhir/uri "url-182104"
                 :library [#fhir/canonical "library-url-094115"]}]
          [:put {:fhir/type :fhir/Library :id "0"
                 :url #fhir/uri "library-url-094115"}]]]

        (let [{:keys [status body]}
              @(handler
                {:params {"measure" "url-182104"
                          "periodStart" "2014"
                          "periodEnd" "2015"}})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "value"
            [:issue 0 :diagnostics] := #fhir/string "No attachment with `text/cql` content type found in library with id `0`."
            [:issue 0 :expression first] := #fhir/string "Library.content"))))

    (testing "on Missing Data in Library Content"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Measure :id "0"
                 :url #fhir/uri "url-182039"
                 :library [#fhir/canonical "library-url-094115"]}]
          [:put {:fhir/type :fhir/Library :id "0"
                 :url #fhir/uri "library-url-094115"
                 :content
                 [#fhir/Attachment{:contentType #fhir/code "text/cql"}]}]]]

        (let [{:keys [status body]}
              @(handler
                {:params {"measure" "url-182039"
                          "periodStart" "2014"
                          "periodEnd" "2015"}})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "value"
            [:issue 0 :diagnostics] := #fhir/string "Missing embedded data of first attachment in library with id `0`."
            [:issue 0 :expression first] := #fhir/string "Library.content[0].data"))))

    (testing "on non text/cql content type"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Measure :id "0"
                 :url #fhir/uri "url-182051"
                 :library [#fhir/canonical "library-url-094115"]}]
          [:put {:fhir/type :fhir/Library :id "0"
                 :url #fhir/uri "library-url-094115"
                 :content
                 [#fhir/Attachment{:contentType #fhir/code "text/plain"}]}]]]

        (let [{:keys [status body]}
              @(handler
                {:params {"measure" "url-182051"
                          "periodStart" "2014"
                          "periodEnd" "2015"}})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "value"
            [:issue 0 :diagnostics] := #fhir/string "No attachment with `text/cql` content type found in library with id `0`."
            [:issue 0 :expression first] := #fhir/string "Library.content")))))

  (testing "Returns Unprocessable Entity on Measure without Library"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Measure :id "0"
               :url #fhir/uri "url-182126"}]]]

      (let [{:keys [status body]}
            @(handler
              {:params {"measure" "url-182126"
                        "periodStart" "2014"
                        "periodEnd" "2015"}})]

        (is (= 422 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "not-supported"
          [:issue 0 :diagnostics] := #fhir/string "Missing primary library. Currently only CQL expressions together with one primary library are supported."
          [:issue 0 :expression first] := #fhir/string "Measure.library")))))

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
