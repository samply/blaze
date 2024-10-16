(ns blaze.rest-api.async-status-handler-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.db.impl.search-param]
   [blaze.handler.util :as handler-util]
   [blaze.job.async-interaction :as job-async]
   [blaze.metrics.spec]
   [blaze.middleware.fhir.db :refer [wrap-db]]
   [blaze.rest-api :as-alias rest-api]
   [blaze.rest-api.async-status-handler]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [juxt.iota :refer [given]]
   [reitit.ring]
   [taoensso.timbre :as log])
  (:import
   [java.time OffsetDateTime]))

(set! *warn-on-reflection* true)
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

(defn ready-job [authored-on bundle-id t]
  (job-async/job authored-on bundle-id t))

(defn in-progress-job [authored-on bundle-id t]
  (assoc (job-async/job authored-on bundle-id t) :status #fhir/code"in-progress"))

(defn completed-job [authored-on request-bundle-id t response-bundle-id]
  (-> (job-async/job authored-on request-bundle-id t)
      (assoc :status #fhir/code"completed")
      (job-async/add-response-bundle-reference response-bundle-id)))

(defn failed-job [authored-on bundle-id t]
  (assoc (job-async/job authored-on bundle-id t) :status #fhir/code"failed"))

(defn cancelled-job [authored-on bundle-id t]
  (assoc (job-async/job authored-on bundle-id t) :status #fhir/code"cancelled"))

(deftest async-status-handler-test
  (testing "with ready job"
    (with-handler [handler]
      [[[:put (assoc (ready-job (OffsetDateTime/now) "0" 0) :id "0")]
        [:put (job-async/request-bundle "0" "GET" "Observation/0")]]]

      (let [{:keys [status headers]}
            @(handler {:path-params {:id "0"}})]

        (is (= 202 status))

        (given headers
          "X-Progress" := "ready"))))

  (testing "with in-progress job"
    (with-handler [handler]
      [[[:put (assoc (in-progress-job (OffsetDateTime/now) "0" 0) :id "0")]
        [:put (job-async/request-bundle "0" "GET" "Observation/0")]]]

      (let [{:keys [status headers]}
            @(handler {:path-params {:id "0"}})]

        (is (= 202 status))

        (given headers
          "X-Progress" := "in-progress"))))

  (testing "with completed job"
    (with-handler [handler]
      [[[:put (assoc (completed-job (OffsetDateTime/now) "0" 0 "1") :id "0")]
        [:put (job-async/request-bundle "0" "GET" "Observation/0")]
        [:put {:fhir/type :fhir/Bundle
               :id "1"
               :type #fhir/code"batch-response"
               :entry
               [{:fhir/type :fhir.Bundle/entry
                 :resource
                 {:fhir/type :fhir/Observation
                  :id "0"}}]}]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}})]

        (is (= 200 status))

        (given body
          :fhir/type := :fhir/Bundle))))

  (testing "with failed job"
    (with-handler [handler]
      [[[:put (assoc (failed-job (OffsetDateTime/now) "0" 0) :id "0")]
        [:put (job-async/request-bundle "0" "GET" "Observation/0")]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}})]

        (is (= 500 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code"error"
          [:issue 0 :code] := #fhir/code"exception"
          [:issue 0 :diagnostics] := "The asynchronous request with id `0` failed. Cause: null"))))

  (testing "with cancelled job"
    (with-handler [handler]
      [[[:put (assoc (cancelled-job (OffsetDateTime/now) "0" 0) :id "0")]
        [:put (job-async/request-bundle "0" "GET" "Observation/0")]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}})]

        (is (= 404 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code"error"
          [:issue 0 :code] := #fhir/code"not-found"
          [:issue 0 :diagnostics] := "The asynchronous request with id `0` is cancelled.")))))
