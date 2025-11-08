(ns blaze.operation.graph
  "Main entry point into the Resource $graph operation.

  See: https://build.fhir.org/resource-operation-graph.html"
  (:refer-clojure :exclude [compile str])
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util :as fu]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.interaction.search.util :as search-util]
   [blaze.module :as m]
   [blaze.operation.graph.compiler :as c]
   [blaze.operation.graph.spec]
   [blaze.util :refer [str]]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [reitit.core :as reitit]
   [ring.util.response :as ring]
   [taoensso.timbre :as log])
  (:import
   [com.github.benmanes.caffeine.cache Cache Caffeine]
   [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)

(defn- graph-def-query [db uri]
  (d/type-query db "GraphDefinition" [["url" uri]]))

(defn- find-graph-def [db uri]
  (do-sync [graph-defs (d/pull-many db (vec (graph-def-query db uri)))]
    (or (first (fu/sort-by-priority graph-defs))
        (ba/not-found (format "The graph definition `%s` was not found." uri)
                      :http/status 400))))

(defn- compile
  [compiled-graph-cache {:keys [id] {version :versionId} :meta :as graph-def}]
  (let [key [id (type/value version)]]
    (.get ^Cache compiled-graph-cache key (fn [_] (c/compile graph-def)))))

(defn- find-node [{:keys [nodes]} id]
  (nodes id))

(defn- find-links [{:keys [links]} source-id]
  (links source-id))

(declare process-node)

(defn- process-link
  [db graph resource processed-resources {:keys [target-id resource-handles]}]
  (let [target-node (find-node graph target-id)]
    (reduce
     (partial process-node db graph target-node)
     processed-resources
     (resource-handles db resource target-node))))

(defn- process-node
  "Processes `resource-handle` on `node`.

  The future map `processed-resources` contains all already processed resources.
  Terminates if the resource-handle is already processed. Otherwise fetches the
  resource of `resource-handle` and adds it to processed resources and traverses
  all links of node in depth-first manner, possibly adding more resources.

  Returns a CompletableFuture that will complete with the final map of processed
  resources reachable from `node`."
  [db graph node processed-resources
   {:fhir/keys [type] :keys [id] :as resource-handle}]
  (-> processed-resources
      (ac/then-compose
       (fn [processed-resources]
         (let [resource-identity (str (name type) "/" id)]
           (if (contains? processed-resources resource-identity)
             (ac/completed-future processed-resources)
             (-> (d/pull db resource-handle)
                 (ac/then-compose
                  (fn [resource]
                    (reduce
                     (partial process-link db graph resource)
                     (ac/completed-future (assoc processed-resources resource-identity resource))
                     (find-links graph (:id node))))))))))))

(defn- process-start-node
  "Processes `resource-handle` on the node with `start-node-id` of `graph` in
  `db` and returns a CompletableFuture that will complete with the resulting
  resources."
  [db graph start-node-id resource-handle]
  (-> (process-node db graph (find-node graph start-node-id) (ac/completed-future {}) resource-handle)
      (ac/then-apply vals)))

(defn- handler [{:keys [compiled-graph-cache] :as context}]
  (fn [{:blaze/keys [db]
        {{:fhir.resource/keys [type]} :data} ::reitit/match
        {:keys [id]} :path-params
        {:strs [graph]} :query-params :as request}]
    (if-ok [resource-handle (fhir-util/resource-handle db type id)]
      (if graph
        (-> (find-graph-def db graph)
            (ac/then-compose
             (fn [graph-def]
               (when-let [{:keys [start-node-id] :as graph} (compile compiled-graph-cache graph-def)]
                 (do-sync [resources (process-start-node db graph start-node-id resource-handle)]
                   (ring/response
                    {:fhir/type :fhir/Bundle
                     :id (m/luid context)
                     :type #fhir/code "searchset"
                     :total (type/unsignedInt (count resources))
                     :entry (mapv (partial search-util/match-entry request) resources)}))))))
        (ac/completed-future (ba/incorrect "Missing param `graph`")))
      ac/completed-future)))

(defn- handle-close [stage db]
  (ac/handle
   stage
   (fn [resources e]
     (let [res (if e e resources)]
       (.close ^AutoCloseable db)
       res))))

(defn- wrap-batch-db [handler]
  (fn [{:blaze/keys [db] :as request}]
    (let [db (d/new-batch-db db)]
      (-> (handler (assoc request :blaze/db db))
          (handle-close db)))))

(defmethod m/pre-init-spec :blaze.operation/graph [_]
  (s/keys :req-un [::compiled-graph-cache :blaze/clock :blaze/rng-fn :blaze/page-id-cipher]))

(defmethod ig/init-key :blaze.operation/graph [_ context]
  (log/info "Init FHIR Resource $graph operation handler")
  (-> (handler context)
      (wrap-batch-db)))

(defmethod m/pre-init-spec ::compiled-graph-cache [_]
  (s/keys :opt-un [::num-concepts]))

(defmethod ig/init-key ::compiled-graph-cache
  [_ {:keys [num-nodes] :or {num-nodes 10000}}]
  (log/info "Init operation $graph graph cache with a size of" num-nodes "nodes")
  (-> (Caffeine/newBuilder)
      (.maximumWeight num-nodes)
      (.weigher (fn [_ {:keys [nodes]}] (count nodes)))
      (.recordStats)
      (.build)))
