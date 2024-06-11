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
   [blaze.job.async-interaction.request :as req]
   [blaze.luid :as luid]
   [blaze.module :as m :refer [reg-collector]]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [java-time.api :as time]
   [reitit.core :as reitit]
   [ring.util.response :as ring]
   [taoensso.timbre :as log])
  (:import
   [java.util.concurrent TimeUnit]))

(set! *warn-on-reflection* true)

(defn- luid [{:keys [clock rng-fn]}]
  (luid/luid clock (rng-fn)))

(defn- tx-ops [{:keys [tx-ops resource]} id]
  (conj (or tx-ops []) [:create (assoc resource :id id)]))

(defn- response-context [{:keys [headers] :as request} db-after]
  (let [return-preference (handler-util/preference headers "return")]
    (cond-> (assoc request :blaze/db db-after)
      return-preference
      (assoc :blaze.preference/return return-preference))))

(defn- handle
  [{:keys [node] :as context}
   {:blaze/keys [base-url cancelled?]
    ::reitit/keys [router]
    :keys [request-method headers]
    ::keys [params]
    :as request}
   measure]
  (if (handler-util/preference headers "respond-async")
    (req/handle-async context request)
    (let [context (cond-> (assoc context
                                 :blaze/base-url base-url
                                 ::reitit/router router)
                    cancelled?
                    (assoc :blaze/cancelled? cancelled?))]
      (-> (measure/evaluate-measure context measure params)
          (ac/then-compose
           (fn process-result [result]
             (cond
               (= :get request-method)
               (ac/completed-future (ring/response (:resource result)))

               (= :post request-method)
               (let [id (luid context)]
                 (-> (d/transact node (tx-ops result id))
                     (ac/then-compose
                      (fn [db-after]
                        (response/build-response
                         (response-context request db-after)
                         nil
                         nil
                         (d/resource-handle db-after "MeasureReport" id)))))))))))))

(defn- measure-with-id-not-found-msg [id]
  (format "The Measure resource with id `%s` was not found." id))

(defn- measure-with-reference-not-found-msg [reference]
  (format "The Measure resource with reference `%s` was not found." reference))

(defn- find-measure-handle*
  [db {{:keys [id]} :path-params {:keys [measure]} ::params}]
  (cond
    id
    (or (d/resource-handle db "Measure" id)
        (ba/not-found (measure-with-id-not-found-msg id)))

    measure
    (or (coll/first (d/type-query db "Measure" [["url" measure]]))
        (ba/not-found (measure-with-reference-not-found-msg measure)
                      :http/status 400))

    :else
    (ba/incorrect "The measure parameter is missing."
                  :fhir/issue "required")))

(defn- measure-deleted-msg [{:keys [id]}]
  (format "The Measure resource with the id `%s` was deleted." id))

(defn- find-measure-handle [db request]
  (let [{:keys [op] :as measure-handle} (find-measure-handle* db request)]
    (if (identical? :delete op)
      (ba/not-found (measure-deleted-msg measure-handle)
                    :http/status 410
                    :fhir/issue "deleted")
      measure-handle)))

(defn- handler [context]
  (fn [{:blaze/keys [db] :as request}]
    (-> (ac/completed-future (find-measure-handle db request))
        (ac/then-compose
         (fn [measure-handle]
           (-> (d/pull db measure-handle)
               (ac/exceptionally
                #(assoc %
                        ::anom/category ::anom/fault
                        :fhir/issue "incomplete")))))
        (ac/then-compose-async (partial handle (assoc context :db db) request)))))

(defmethod m/pre-init-spec ::handler [_]
  (s/keys :req-un [:blaze.db/node ::executor :blaze/clock :blaze/rng-fn]
          :opt-un [::timeout :blaze/context-path]))

(defmethod ig/init-key ::handler [_ context]
  (log/info "Init FHIR $evaluate-measure operation handler")
  (wrap-coerce-params (handler context)))

(defmethod m/pre-init-spec ::timeout [_]
  (s/keys :req-un [:blaze.fhir.operation.evaluate-measure.timeout/millis]))

(defmethod ig/init-key ::timeout [_ {:keys [millis]}]
  (time/millis millis))

(defn- executor-init-msg []
  (format "Init $evaluate-measure operation executor with %d threads"
          (.availableProcessors (Runtime/getRuntime))))

(defmethod ig/init-key ::executor
  [_ _]
  (log/info (executor-init-msg))
  (ex/cpu-bound-pool "operation-evaluate-measure-%d"))

(defmethod ig/halt-key! ::executor
  [_ executor]
  (log/info "Stopping $evaluate-measure operation executor...")
  (ex/shutdown! executor)
  (if (ex/await-termination executor 10 TimeUnit/SECONDS)
    (log/info "$evaluate-measure operation executor was stopped successfully")
    (log/warn "Got timeout while stopping the $evaluate-measure operation executor")))

(derive ::executor :blaze.metrics/thread-pool-executor)

(reg-collector ::compile-duration-seconds
  measure/compile-duration-seconds)

(reg-collector ::evaluate-duration-seconds
  measure/evaluate-duration-seconds)
