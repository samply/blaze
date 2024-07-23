(ns blaze.rest-api.async-status-handler
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.fhir.spec.references :as fsr]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.fhir.util :as fhir-util]
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
             (let [[type id] (some-> job job-async/response-bundle-ref fsr/split-literal-ref)]
               (do-sync [response-bundle (fhir-util/pull db type id)]
                 (ring/response response-bundle)))
             "failed"
             (ac/completed-future
              (ba/fault
               (format "The asynchronous request with id `%s` failed. Cause: %s" id (job-util/error-msg job))))))))))

(defmethod ig/init-key ::rest-api/async-status-handler
  [_ _]
  (log/info "Init async status handler")
  (handler))
