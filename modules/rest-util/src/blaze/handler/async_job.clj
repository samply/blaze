(ns blaze.handler.async-job
  "Handler for operations that create a job and always respond async according
  to the Asynchronous Interaction Request Pattern."
  (:require
   [blaze.anomaly :refer [if-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.fhir.util :as fu]
   [blaze.handler.util :as handler-util]
   [blaze.job-scheduler :as js]
   [blaze.time :as bt]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(defn handler
  "Returns a handler that coerces the Parameters body of a request according to
  `param-specs`, creates the job returned by calling `job-fn` with the current
  time as authored-on and the coerced params, and responds with 202 and the URL
  of the async status endpoint of the job.

  See blaze.fhir.util/coerce-params for the format of `param-specs`."
  {:arglists '([context param-specs job-fn])}
  [{:keys [clock] :as context} param-specs job-fn]
  (fn [{:keys [body] :blaze/keys [job-scheduler] :as request}]
    (if-ok [parameters (handler-util/parameters-body body)
            params (fu/coerce-params param-specs parameters)]
      (let [authored-on (bt/offset-date-time clock)]
        (log/debug "Initiate async response...")
        (do-sync [job (js/create-job job-scheduler (job-fn authored-on params))]
          (-> (ring/status 202)
              (handler-util/async-status-location context request job))))
      ac/completed-future)))
