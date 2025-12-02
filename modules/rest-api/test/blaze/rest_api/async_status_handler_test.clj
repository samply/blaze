(ns blaze.rest-api.async-status-handler-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.db.impl.search-param]
   [blaze.handler.util :as handler-util]
   [blaze.job.async-interaction :as job-async]
   [blaze.job.compact :as job-compact]
   [blaze.job.compact-spec]
   [blaze.job.util :as job-util]
   [blaze.metrics.spec]
   [blaze.middleware.fhir.db :refer [wrap-db]]
   [blaze.rest-api :as-alias rest-api]
   [blaze.rest-api.async-status-handler]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [java-time.api :as time]
   [juxt.iota :refer [given]]
   [reitit.ring]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private config
  (assoc api-stub/mem-node-config ::rest-api/async-status-handler {}))

(defn wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))

(defmacro with-handler [[handler-binding] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         handler# ::rest-api/async-status-handler} config]
       ~txs
       (let [~handler-binding (-> handler# (wrap-db node# 100)
                                  wrap-error)]
         ~@body))))

(defn- ready-job [authored-on bundle-id t]
  (job-async/job authored-on bundle-id t))

(defn- in-progress-job [authored-on bundle-id t]
  (assoc (job-async/job authored-on bundle-id t) :status #fhir/code "in-progress"))

(defn- completed-job [authored-on request-bundle-id t response-bundle-id]
  (-> (job-async/job authored-on request-bundle-id t)
      (assoc :status #fhir/code "completed")
      (job-async/add-response-bundle-reference response-bundle-id)))

(defn- completed-compact-job [authored-on]
  (-> (job-compact/job authored-on "index" "resource-as-of-index")
      (assoc :status #fhir/code "completed")))

(defn- failed-job [authored-on bundle-id t error-msg]
  (-> (job-async/job authored-on bundle-id t)
      (job-util/fail-job (ba/fault error-msg))))

(defn- cancelled-job [authored-on bundle-id t]
  (assoc (job-async/job authored-on bundle-id t) :status #fhir/code "cancelled"))

(deftest async-status-handler-test
  (testing "with ready job"
    (with-handler [handler]
      [[[:put (assoc (ready-job (time/offset-date-time) "0" 0) :id "0")]
        [:put (job-async/request-bundle "0" "GET" "Observation/0")]]]

      (let [{:keys [status headers]}
            @(handler {:path-params {:id "0"}})]

        (is (= 202 status))

        (given headers
          "X-Progress" := "ready"))))

  (testing "with in-progress job"
    (with-handler [handler]
      [[[:put (assoc (in-progress-job (time/offset-date-time) "0" 0) :id "0")]
        [:put (job-async/request-bundle "0" "GET" "Observation/0")]]]

      (let [{:keys [status headers]}
            @(handler {:path-params {:id "0"}})]

        (is (= 202 status))

        (given headers
          "X-Progress" := "in-progress"))))

  (testing "with completed job"
    (with-handler [handler]
      [[[:put (assoc (completed-job (time/offset-date-time) "0" 0 "1") :id "0")]
        [:put (job-async/request-bundle "0" "GET" "Observation/0")]
        [:put {:fhir/type :fhir/Bundle
               :id "1"
               :type #fhir/code "batch-response"
               :entry
               [{:fhir/type :fhir.Bundle/entry
                 :resource
                 {:fhir/type :fhir/Observation
                  :id "0"}}]}]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/Bundle
          :type := #fhir/code "batch-response"))))

  (testing "with completed compact job"
    (with-handler [handler]
      [[[:put (assoc (completed-compact-job (time/offset-date-time)) :id "0")]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/Bundle
          :type := #fhir/code "batch-response"
          [:entry 0 :response :status] := #fhir/string "200"))))

  (testing "with failed job"
    (with-handler [handler]
      [[[:put (assoc (failed-job (time/offset-date-time) "0" 0 "msg-181242") :id "0")]
        [:put (job-async/request-bundle "0" "GET" "Observation/0")]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/Bundle
          :type := #fhir/code "batch-response"
          [:entry 0 :response :status] := #fhir/string "500"
          [:entry 0 :response :outcome :fhir/type] := :fhir/OperationOutcome
          [:entry 0 :response :outcome :issue 0 :severity] := #fhir/code "error"
          [:entry 0 :response :outcome :issue 0 :code] := #fhir/code "exception"
          [:entry 0 :response :outcome :issue 0 :diagnostics] := #fhir/string "msg-181242"))))

  (testing "with cancelled job"
    (with-handler [handler]
      [[[:put (assoc (cancelled-job (time/offset-date-time) "0" 0) :id "0")]
        [:put (job-async/request-bundle "0" "GET" "Observation/0")]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}})]

        (is (= 404 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "not-found"
          [:issue 0 :diagnostics] := #fhir/string "The asynchronous request with id `0` is cancelled.")))))
