(ns blaze.middleware.fhir.metrics
  (:require
    [clojure.spec.alpha :as s]
    [manifold.deferred :as md]
    [prometheus.alpha :as prom]))


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


(defn- inc-requests-total!
  [interaction-name {:keys [request-method]} {:keys [status]}]
  (when (and request-method status)
    (prom/inc! requests-total (str status) interaction-name
               (name request-method))))


(defn- duration-s
  "Returns the duration in seconds from a System/nanoTime start point."
  [start]
  (/ (double (- (System/nanoTime) start)) 1000000000.0))


(defn- observe-request-duration-seconds!
  [{:aleph/keys [request-arrived] :keys [request-method]} interaction-name]
  (when request-arrived
    (prom/observe! request-duration-seconds interaction-name
                   (name request-method)
                   (duration-s request-arrived))))


(s/fdef wrap-observe-request-duration
  :args (s/cat :handler fn? :interaction-name (s/? string?)))

(defn wrap-observe-request-duration
  ([handler]
   (fn [request]
     (-> (handler request)
         (md/chain'
           (fn [{:fhir/keys [interaction-name] :as response}]
             (when interaction-name
               (inc-requests-total! interaction-name request response)
               (observe-request-duration-seconds! request interaction-name))
             response)))))
  ([handler interaction-name]
   (fn [request]
     (-> (handler request)
         (md/chain'
           (fn [response]
             (inc-requests-total! interaction-name request response)
             (observe-request-duration-seconds! request interaction-name)
             response))))))
