(ns blaze.fhir.operation.re-index-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.fhir.canonical :as canonical]
   [blaze.fhir.operation.re-index]
   [blaze.fhir.util :as fu]
   [blaze.handler.async-job-spec]
   [blaze.handler.util :as handler-util]
   [blaze.job.util :as job-util]
   [blaze.module.test-util :refer [given-failed-system]]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private base-url "base-url-130959")

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.fhir.operation/re-index nil}
      :key := :blaze.fhir.operation/re-index
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.fhir.operation/re-index {}}
      :key := :blaze.fhir.operation/re-index
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :search-param-registry))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :clock))))

  (testing "invalid search-param-registry"
    (given-failed-system {:blaze.fhir.operation/re-index {:search-param-registry ::invalid}}
      :key := :blaze.fhir.operation/re-index
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 1 :via] := [:blaze.db/search-param-registry]
      [:cause-data ::s/problems 1 :val] := ::invalid))

  (testing "invalid clock"
    (given-failed-system {:blaze.fhir.operation/re-index {:clock ::invalid}}
      :key := :blaze.fhir.operation/re-index
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :search-param-registry))
      [:cause-data ::s/problems 1 :via] := [:blaze/clock]
      [:cause-data ::s/problems 1 :val] := ::invalid)))

(def ^:private config
  (assoc
   api-stub/mem-node-config
   :blaze.fhir.operation/re-index
   {:search-param-registry (ig/ref :blaze.db/search-param-registry)
    :clock (ig/ref :blaze.test/fixed-clock)
    :context-path "/fhir"}
   :blaze/job-scheduler
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}))

(defn- wrap-defaults [handler]
  (fn [request]
    (handler
     (assoc request
            :blaze/base-url base-url))))

(defn- wrap-job-scheduler [handler job-scheduler]
  (fn [request]
    (handler (assoc request :blaze/job-scheduler job-scheduler))))

(defn- wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))

(defmacro with-handler [[handler-binding & [node-binding]] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         job-scheduler# :blaze/job-scheduler
                         handler# :blaze.fhir.operation/re-index} config]
       ~txs
       (let [~handler-binding (-> handler#
                                  wrap-defaults
                                  (wrap-job-scheduler job-scheduler#)
                                  wrap-error)
             ~(or node-binding '_) node#]
         ~@body))))

(defn- search-param-url-input [job]
  (job-util/input-value job (canonical/url "CodeSystem/ReIndexJobParameter")
                        "search-param-url"))

(deftest handler-test
  (testing "wrong resource type"
    (with-handler [handler]
      (let [{:keys [status body]} @(handler {:body {:fhir/type :fhir/Patient}})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "invalid"
          [:issue 0 :diagnostics] := #fhir/string "Expected Parameters resource but was `Patient` resource."))))

  (testing "missing body"
    (with-handler [handler]
      (let [{:keys [status body]} @(handler {})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "invalid"
          [:issue 0 :diagnostics] := #fhir/string "Missing required parameter `search-param-url`."))))

  (testing "missing search-param-url parameter"
    (with-handler [handler]
      (let [{:keys [status body]} @(handler {:body {:fhir/type :fhir/Parameters}})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "invalid"
          [:issue 0 :diagnostics] := #fhir/string "Missing required parameter `search-param-url`."))))

  (testing "search-param-url parameter of wrong type"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler {:body (fu/parameters "search-param-url" #fhir/boolean true)})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "invalid"
          [:issue 0 :diagnostics] := #fhir/string "Invalid value for parameter `search-param-url`. Has to be a canonical."))))

  (testing "search-param-url parameter without value"
    (doseq [value [#fhir/canonical{:extension [#fhir/Extension{:url "foo"}]}
                   #fhir/uri{:extension [#fhir/Extension{:url "foo"}]}]]
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler {:body (fu/parameters "search-param-url" value)})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "invalid"
            [:issue 0 :diagnostics] := #fhir/string "Invalid value for parameter `search-param-url`. Missing value.")))))

  (testing "unknown search param"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler {:body (fu/parameters "search-param-url" #fhir/canonical "unknown-161712")})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "invalid"
          [:issue 0 :diagnostics] := #fhir/string "Invalid value for parameter `search-param-url`. Search parameter with URL `unknown-161712` not found."))))

  (testing "versioned search param"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler {:body (fu/parameters "search-param-url" #fhir/canonical "http://hl7.org/fhir/SearchParameter/clinical-code|4.0.1")})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "invalid"
          [:issue 0 :diagnostics] := #fhir/string "Invalid value for parameter `search-param-url`. Versioned canonical `http://hl7.org/fhir/SearchParameter/clinical-code|4.0.1` is not supported. Please omit the version."))))

  (testing "success"
    (doseq [value [#fhir/canonical "http://hl7.org/fhir/SearchParameter/clinical-code"
                   #fhir/uri "http://hl7.org/fhir/SearchParameter/clinical-code"]]
      (with-handler [handler node]
        (let [{:keys [status headers]}
              @(handler {:body (fu/parameters "search-param-url" value)})]

          (is (= 202 status))

          (testing "the Content-Location header contains the status endpoint URL"
            (is (= (get headers "Content-Location")
                   (str base-url "/fhir/__async-status/AAAAAAAAAAAAAAAA"))))

          (testing "a re-index job is created"
            (given @(d/pull node (d/resource-handle (d/db node) "Task" "AAAAAAAAAAAAAAAA"))
              :fhir/type := :fhir/Task
              [:code :coding 0 :code] := #fhir/code "re-index"
              search-param-url-input := #fhir/canonical "http://hl7.org/fhir/SearchParameter/clinical-code")))))))
