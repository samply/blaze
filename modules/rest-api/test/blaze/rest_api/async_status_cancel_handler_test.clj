(ns blaze.rest-api.async-status-cancel-handler-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.db.impl.search-param]
   [blaze.handler.util :as handler-util]
   [blaze.job-scheduler :as js]
   [blaze.job-scheduler.protocols :as p]
   [blaze.job.async-interaction :as job-async]
   [blaze.metrics.spec]
   [blaze.middleware.fhir.db :refer [wrap-db]]
   [blaze.module.test-util :refer [given-failed-system]]
   [blaze.rest-api :as-alias rest-api]
   [blaze.rest-api.async-status-cancel-handler]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]]
   [reitit.ring]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest init-test
  (testing "nil config"
    (given-failed-system {::rest-api/async-status-cancel-handler nil}
      :key := ::rest-api/async-status-cancel-handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {::rest-api/async-status-cancel-handler {}}
      :key := ::rest-api/async-status-cancel-handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :job-scheduler))))

  (testing "invalid job-scheduler"
    (given-failed-system {::rest-api/async-status-cancel-handler {:job-scheduler ::invalid}}
      :key := ::rest-api/async-status-cancel-handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(defmethod ig/init-key :blaze.job/no-op
  [_ _]
  (reify p/JobHandler
    (-on-start [_ job] (ac/completed-future job))
    (-on-resume [_ job] (ac/completed-future job))))

(def ^:private config
  (assoc
   api-stub/mem-node-config
   :blaze/job-scheduler
   {:node (ig/ref :blaze.db/node)
    :handlers {:blaze.job/async-interaction (ig/ref :blaze.job/no-op)}
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}

   ::rest-api/async-status-cancel-handler
   {:job-scheduler (ig/ref :blaze/job-scheduler)}

   :blaze.job/no-op {}
   :blaze.test/fixed-rng-fn {}))

(defn wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))

(defmacro with-handler [[handler-binding] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         handler# ::rest-api/async-status-cancel-handler} config]
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

(deftest async-status-cancel-handler-test
  (testing "with ready job"
    (with-handler [handler]
      [[[:put (assoc (ready-job (time/offset-date-time) "0" 0) :id "0")]
        [:put (job-async/request-bundle "0" "GET" "Observation/0")]]]

      (let [{:keys [status]}
            @(handler {:path-params {:id "0"}})]

        (is (= 202 status)))))

  (testing "with in-progress job"
    (with-handler [handler]
      [[[:put (assoc (in-progress-job (time/offset-date-time) "0" 0) :id "0")]
        [:put (job-async/request-bundle "0" "GET" "Observation/0")]]]

      (let [{:keys [status]}
            @(handler {:path-params {:id "0"}})]

        (is (= 202 status)))))

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

        (is (= 409 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "conflict"
          [:issue 0 :diagnostics] := #fhir/string "The asynchronous request with id `0` can't be cancelled because it's already completed."))))

  (testing "with other conflict anomaly"
    (with-redefs [js/cancel-job
                  (constantly (ac/completed-future (ba/conflict "msg-171718")))]

      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler {:path-params {:id "0"}})]

          (is (= 409 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "conflict"
            [:issue 0 :diagnostics] := #fhir/string "msg-171718")))))

  (testing "with other fault anomaly"
    (with-redefs [js/cancel-job
                  (constantly (ac/completed-future (ba/fault "msg-171957")))]

      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler {:path-params {:id "0"}})]

          (is (= 500 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "exception"
            [:issue 0 :diagnostics] := #fhir/string "msg-171957"))))))
