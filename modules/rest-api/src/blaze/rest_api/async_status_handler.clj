(ns blaze.rest-api.async-status-handler
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.fhir.spec.references :as fsr]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.handler.util :as handler-util]
   [blaze.job.async-interaction :as job-async]
   [blaze.job.util :as job-util]
   [blaze.rest-api :as-alias rest-api]
   [blaze.rest-api.spec]
   [integrant.core :as ig]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(defn- handler []
  (fn [{{:keys [id]} :path-params :blaze/keys [db]}]
    (-> (fhir-util/pull db "Task" id)
        (ac/then-compose
         (fn [{:keys [status] :as job}]
           (case (type/value status)
             "ready"
             (-> (ring/status 202)
                 (ring/header "X-Progress" "ready")
                 (ac/completed-future))
             "in-progress"
             (-> (ring/status 202)
                 (ring/header "X-Progress" "in-progress")
                 (ac/completed-future))
             "cancelled"
             (ac/completed-future
              (ba/not-found
               (format "The asynchronous request with id `%s` is cancelled." id)))
             "completed"
             (if-let [[type id] (some-> job job-async/response-bundle-ref fsr/split-literal-ref)]
               (do-sync [response-bundle (fhir-util/pull db type id)]
                 (ring/response response-bundle))
               (ac/completed-future
                (ring/response
                 {:fhir/type :fhir/Bundle
                  :type #fhir/code "batch-response"
                  :entry
                  [{:fhir/type :fhir.Bundle/entry
                    :response {:fhir/type :fhir.Bundle.entry/response
                               :status #fhir/string "200"}}]})))
             "failed"
             (ac/completed-future
              (ring/response
               {:fhir/type :fhir/Bundle
                :type #fhir/code "batch-response"
                :entry
                [{:fhir/type :fhir.Bundle/entry
                  :response (handler-util/bundle-error-response
                             (job-util/error job))}]}))))))))

(defmethod ig/init-key ::rest-api/async-status-handler
  [_ _]
  (log/info "Init async status handler")
  (handler))
