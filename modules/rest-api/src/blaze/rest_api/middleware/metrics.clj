(ns blaze.rest-api.middleware.metrics
  (:require
   [blaze.util :as u]
   [clojure.string :as str]
   [prometheus.alpha :as prom]))

(set! *warn-on-reflection* true)

(prom/defcounter requests-total
  "Number of requests to this service.
   Distinguishes between the returned status code, the handler being used to
   process the request together with the http method."
  {:namespace "http"
   :subsystem "fhir"}
  "code" "interaction" "method")

(prom/defhistogram request-duration-seconds
  "The HTTP request latencies in seconds."
  {:namespace "http"
   :subsystem "fhir"}
  (take 19 (iterate #(* 2 %) 0.0001))
  "interaction" "method")

(defn- handle-response
  [interaction
   {:keys [request-method] :blaze/keys [request-arrived]}
   {:keys [status] :as response}]
  (let [request-method (str/upper-case (name request-method))]
    (prom/inc! requests-total (str status) interaction request-method)
    (when request-arrived
      (prom/observe! request-duration-seconds interaction
                     request-method (u/duration-s request-arrived))))
  response)

(defn wrap-observe-request-duration-fn [interaction]
  (fn [handler]
    (fn [request respond raise]
      (handler request #(respond (handle-response interaction request %)) raise))))
