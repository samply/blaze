(ns blaze.operation.graph
  "Main entry point into the Resource $graph operation.

  See: https://build.fhir.org/resource-operation-graph.html"
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util :as fu]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.interaction.search.util :as search-util]
   [blaze.module :as m]
   [blaze.operation.graph.cache :as cache]
   [blaze.operation.graph.compiler :as c]
   [blaze.operation.graph.spec]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [reitit.core :as reitit]
   [ring.util.response :as ring]
   [taoensso.timbre :as log])
  (:import
   [com.github.benmanes.caffeine.cache Caffeine]))

(set! *warn-on-reflection* true)

(defn- find-graph-def [db uri]
  (-> (d/type-query db "GraphDefinition" [["url" uri]])
      (ac/then-compose
       (fn [handles]
         (do-sync [graph-defs (d/pull-many db (vec handles))]
           (or (first (fu/sort-by-priority graph-defs))
               (ba/not-found (format "The graph definition `%s` was not found." uri)
                             :http/status 400)))))))

(defn- find-node [{:keys [nodes]} id]
  (nodes id))

(defn- find-links [{:keys [links]} source-id]
  (links source-id))

(declare process-node)

(defn- process-link
  [db graph resource state {:keys [target-id resource-handles]}]
  (let [target-node (find-node graph target-id)]
    (-> (resource-handles db resource target-node)
        (ac/then-compose
         (fn [handles]
           (reduce
            (partial process-node db graph target-node)
            state
            handles))))))

(defn- pull-resource
  "Returns a CompletableFuture that will complete with the resource of
  `resource-handle`, taken from `resources` if already pulled."
  [db resources resource-handle]
  (if-let [resource (resources resource-handle)]
    (ac/completed-future resource)
    (d/pull db resource-handle)))

(defn- add-visit
  "Marks the resource of `resource-handle` as visited on the node with
  `node-id` and adds `resource` to the resources of `state`."
  [state node-id resource-handle resource]
  (-> (update state :visits conj [node-id resource-handle])
      (update :resources assoc resource-handle resource)))

(defn- process-node
  "Processes `resource-handle` on `node`.

  The future `state` contains the `:resources` already found, keyed by their
  resource handle, and the `:visits` already done as tuples of node id and
  resource handle. All resource handles come from `db`, so handles of the same
  resource are equal. Terminates if the resource was already visited on `node`.
  Otherwise adds the resource to the resources found, marks the visit as done
  and traverses all links of `node` in depth-first manner, possibly adding more
  resources.

  A resource reached through different nodes is returned only once, but the
  links of all these nodes are followed.

  Returns a CompletableFuture that will complete with the final state."
  {:arglists '([db graph node state resource-handle])}
  [db graph {node-id :id} state resource-handle]
  (-> state
      (ac/then-compose
       (fn [{:keys [visits resources] :as state}]
         (if (contains? visits [node-id resource-handle])
           (ac/completed-future state)
           (-> (pull-resource db resources resource-handle)
               (ac/then-compose
                (fn [resource]
                  (reduce
                   (partial process-link db graph resource)
                   (ac/completed-future (add-visit state node-id resource-handle resource))
                   (find-links graph node-id))))))))))

(def ^:private initial-state
  {:resources {} :visits #{}})

(defn- process-start-node
  "Processes `resource-handle` on the node with `start-node-id` of `graph` in
  `db` and returns a CompletableFuture that will complete with the resulting
  resources."
  [db graph start-node-id resource-handle]
  (do-sync [{:keys [resources]} (process-node db graph (find-node graph start-node-id)
                                              (ac/completed-future initial-state)
                                              resource-handle)]
    (vals resources)))

(defn- check-start-node-type
  "Returns `graph` if a resource of `type` can be the resource of its start
  node or an anomaly naming both types otherwise."
  [{:keys [start-node-id] :as graph} type id]
  (let [{node-type :type} (find-node graph start-node-id)]
    (if (c/node-type-matches? node-type type)
      graph
      (ba/incorrect
       (format "The type `%s` of the resource `%s/%s` doesn't match the type `%s` of the start node `%s`."
               type type id node-type start-node-id)))))

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
               (if-ok [graph (cache/compile compiled-graph-cache graph-def)
                       {:keys [start-node-id] :as graph} (check-start-node-type graph type id)]
                 (do-sync [resources (process-start-node db graph start-node-id resource-handle)]
                   (ring/response
                    {:fhir/type :fhir/Bundle
                     :id (m/luid context)
                     :type #fhir/code "searchset"
                     :total (type/unsignedInt (count resources))
                     :entry (mapv (partial search-util/match-entry request) resources)}))
                 ac/completed-future))))
        (ac/completed-future (ba/incorrect "Missing param `graph`")))
      ac/completed-future)))

(defmethod m/pre-init-spec :blaze.operation/graph [_]
  (s/keys :req-un [::compiled-graph-cache :blaze/clock :blaze/rng-fn :blaze/page-id-cipher]))

(defmethod ig/init-key :blaze.operation/graph [_ context]
  (log/info "Init FHIR Resource $graph operation handler")
  (handler context))

(defmethod m/pre-init-spec ::compiled-graph-cache [_]
  (s/keys :opt-un [::num-nodes]))

(defn- weight
  "Returns the weight of a compiled graph or anomaly in the compiled graph
  cache, which is the number of nodes, but at least one, because Caffeine
  never evicts entries with a weight of zero."
  [_ {:keys [nodes]}]
  (max 1 (count nodes)))

(defmethod ig/init-key ::compiled-graph-cache
  [_ {:keys [num-nodes] :or {num-nodes 10000}}]
  (log/info "Init operation $graph graph cache with a size of" num-nodes "nodes")
  (-> (Caffeine/newBuilder)
      (.maximumWeight num-nodes)
      (.weigher weight)
      (.recordStats)
      (.build)))
