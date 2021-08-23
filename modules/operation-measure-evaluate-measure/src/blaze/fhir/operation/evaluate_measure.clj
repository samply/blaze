(ns blaze.fhir.operation.evaluate-measure
  "Main entry point into the $evaluate-measure operation."
  (:require
    [blaze.anomaly :as ba]
    [blaze.async.comp :as ac]
    [blaze.coll.core :as coll]
    [blaze.db.api :as d]
    [blaze.executors :as ex]
    [blaze.fhir.operation.evaluate-measure.measure :as measure]
    [blaze.fhir.operation.evaluate-measure.measure.spec]
    [blaze.fhir.operation.evaluate-measure.middleware.params
     :refer [wrap-coerce-params]]
    [blaze.fhir.operation.evaluate-measure.spec]
    [blaze.fhir.response.create :as response]
    [blaze.handler.util :as handler-util]
    [blaze.luid :as luid]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [blaze.module :refer [reg-collector]]
    [blaze.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    [ring.util.response :as ring]
    [taoensso.timbre :as log])
  (:import
    [java.util.concurrent ExecutorService TimeUnit]))


(set! *warn-on-reflection* true)


(defn- luid [{:keys [clock rng-fn]}]
  (luid/luid clock (rng-fn)))


(defn- tx-ops [{:keys [tx-ops resource]} id]
  (conj (or tx-ops []) [:create (assoc resource :id id)]))


(defn- throw-when [x]
  (if (ba/anomaly? x)
    (throw (ba/ex-anom x))
    x))


(defn- handle
  [{:keys [node executor] :as context}
   {:blaze/keys [base-url]
    ::reitit/keys [router]
    :keys [request-method headers]
    ::keys [params]}
   measure]
  (-> (ac/supply-async
        #(throw-when (measure/evaluate-measure context base-url router
                                               measure params))
        executor)
      (ac/then-compose
        (fn process-result [result]
          (cond
            (= :get request-method)
            (ac/completed-future (ring/response (:resource result)))

            (= :post request-method)
            (let [id (luid context)
                  return-preference (handler-util/preference headers "return")]
              (-> (d/transact node (tx-ops result id))
                  ;; it's important to switch to the transaction
                  ;; executor here, because otherwise the central
                  ;; indexing thread would execute response building.
                  (ac/then-apply-async identity executor)
                  (ac/then-compose
                    #(response/build-response
                       base-url router return-preference % nil
                       (d/resource-handle % "MeasureReport" id))))))))))


(defn- find-measure-handle*
  [db {{:keys [id]} :path-params {:strs [measure]} :params}]
  (cond
    id
    (d/resource-handle db "Measure" id)

    measure
    (coll/first (d/type-query db "Measure" [["url" measure]]))))


(defn- find-measure-handle [db request]
  (if-let [{:keys [op] :as measure-handle} (find-measure-handle* db request)]
    (if (identical? :delete op)
      {::anom/category ::anom/not-found
       :http/status 410
       :fhir/issue "deleted"}
      measure-handle)
    {::anom/category ::anom/not-found
     :fhir/issue "not-found"}))


(defn- handler [context]
  (fn [{:blaze/keys [db] :as request}]
    (-> (ba/completion-stage (find-measure-handle db request))
        (ac/then-compose (partial d/pull db))
        (ac/then-compose (partial handle (assoc context :db db) request)))))


(defmethod ig/pre-init-spec ::handler [_]
  (s/keys :req-un [:blaze.db/node ::executor :blaze/clock :blaze/rng-fn]))


(defmethod ig/init-key ::handler [_ context]
  (log/info "Init FHIR $evaluate-measure operation handler")
  (-> (handler context)
      wrap-coerce-params
      (wrap-observe-request-duration "operation-evaluate-measure")))


(defn- executor-init-msg [num-threads]
  (format "Init $evaluate-measure operation executor with %d threads"
          num-threads))


(defmethod ig/init-key ::executor
  [_ {:keys [num-threads] :or {num-threads 4}}]
  (log/info (executor-init-msg num-threads))
  (ex/io-pool num-threads "operation-evaluate-measure-%d"))


(defmethod ig/halt-key! ::executor
  [_ ^ExecutorService executor]
  (log/info "Stopping $evaluate-measure operation executor...")
  (.shutdown executor)
  (if (.awaitTermination executor 10 TimeUnit/SECONDS)
    (log/info "$evaluate-measure operation executor was stopped successfully")
    (log/warn "Got timeout while stopping the $evaluate-measure operation executor")))


(derive ::executor :blaze.metrics/thread-pool-executor)


(reg-collector ::compile-duration-seconds
  measure/compile-duration-seconds)


(reg-collector ::evaluate-duration-seconds
  measure/evaluate-duration-seconds)
