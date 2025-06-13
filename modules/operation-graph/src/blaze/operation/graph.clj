(ns blaze.operation.graph
  "Main entry point into the Resource $graph operation.

  See: https://build.fhir.org/resource-operation-graph.html"
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.fhir-path :as fhir-path]
   [blaze.fhir.spec.references :as fsr]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util :as fu]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.interaction.search.util :as search-util]
   [blaze.module :as m]
   [blaze.operation.graph.spec]
   [blaze.util :refer [str]]
   [blaze.util.clauses :as uc]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [integrant.core :as ig]
   [reitit.core :as reitit]
   [ring.util.codec :as ring-codec]
   [ring.util.response :as ring]
   [taoensso.timbre :as log])
  (:import
   [com.github.benmanes.caffeine.cache Cache Caffeine]
   [com.google.common.base CaseFormat]
   [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)

(defn- find-graph-def [db uri]
  (do-sync [graph-defs (d/pull-many db (d/type-query db "GraphDefinition" [["url" uri]]))]
    (or (first (fu/sort-by-priority graph-defs))
        (ba/not-found (format "The graph definition `%s` was not found." uri)
                      :http/status 400))))

(def ^:private extension-base
  "http://hl7.org/fhir/5.0/StructureDefinition/extension-GraphDefinition")

(defn- extension-value [url]
  #(when (= url (:url %)) (type/value (:value %))))

(defn- camel->kebab [s]
  (.to CaseFormat/LOWER_CAMEL CaseFormat/LOWER_HYPHEN s))

(defn- extension-key [url]
  (let [idx (str/last-index-of url \.)
        name (cond-> url idx (subs (inc idx)))]
    (keyword (camel->kebab name))))

(defn assoc-extension-value
  ([m url extensions]
   (assoc-extension-value m url (extension-key url) extensions))
  ([m url key extensions]
   (let [value (some (extension-value url) extensions)]
     (cond-> m value (assoc key value)))))

(defn- start [{extensions :extension}]
  (some (extension-value (str extension-base ".start")) extensions))

(defn- nodes [{extensions :extension}]
  (into
   {}
   (comp
    (filter
     (fn [{:keys [url]}]
       (= (str extension-base ".node") url)))
    (map
     (fn [{extensions :extension}]
       (let [id (some (extension-value "nodeId") extensions)]
         [id (assoc-extension-value {:id id} "type" extensions)]))))
   extensions))

(defn- base-link [extensions]
  (-> (assoc-extension-value {} (str extension-base ".link.sourceId") extensions)
      (assoc-extension-value (str extension-base ".link.targetId") extensions)))

(def ^:private noop-resolver
  (reify fhir-path/Resolver (-resolve [_ _])))

(defn- link-path-resource-handles [path]
  (fn [db source-resource _target-node]
    (let [[res & more] (fhir-path/eval noop-resolver path source-resource)]
      (when (and (nil? more) (= :fhir/Reference (type/type res)))
        (when-let [ref (:reference res)]
          (when-let [[type id] (fsr/split-literal-ref ref)]
            (ba/map (fhir-util/resource-handle db type id) vector)))))))

(defn- clauses [params]
  (->> (ring-codec/form-decode params)
       (uc/search-clauses)
       (mapv
        (fn [[code value & more :as clause]]
          (if (and (= "{ref}" value) (nil? more))
            (fn [ref]
              [code ref])
            (fn [_ref]
              clause))))))

(defn- link-params-resource-handles [clauses]
  (fn [db {:fhir/keys [type] :keys [id]} target-node]
    (let [ref (str (name type) "/" id)]
      (d/type-query db (:type target-node) (mapv #(% ref) clauses)))))

(defn- link [{:keys [path] extensions :extension}]
  (let [link (base-link extensions)
        params (some (extension-value (str extension-base ".link.params")) extensions)]
    (cond
      (and path params)
      (ba/incorrect "Invalid link with path and params.")
      path
      (when-ok [path (fhir-path/compile path)]
        (assoc link :resource-handles (link-path-resource-handles path)))
      params
      (assoc link :resource-handles (link-params-resource-handles (clauses params)))
      :else
      (ba/incorrect "Invalid link without path and params."))))

(defn- links [{links :link}]
  (group-by :source-id (map link links)))

(defn- build-graph [graph-def]
  {:start-node-id (start graph-def)
   :nodes (nodes graph-def)
   :links (links graph-def)})

(defn- get-graph
  [{:keys [graph-cache]} {:keys [id] {version :versionId} :meta :as graph-def}]
  (let [key [id (type/value version)]]
    (.get ^Cache graph-cache key (fn [_] (build-graph graph-def)))))

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
  all links of node in breadth-first manner, possibly adding more resources.

  Returns the final future of a map of processed resources reachable from node."
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

(defn- process-start-node [db graph start-node-id resource-handle]
  (-> (process-node db graph (find-node graph start-node-id) (ac/completed-future {}) resource-handle)
      (ac/then-apply vals)))

(defn- handler [context]
  (fn [{:blaze/keys [db]
        {{:fhir.resource/keys [type]} :data} ::reitit/match
        {:keys [id]} :path-params
        {:strs [graph]} :query-params :as request}]
    (if-ok [resource-handle (fhir-util/resource-handle db type id)]
      (if graph
        (-> (find-graph-def db graph)
            (ac/then-compose
             (fn [graph-def]
               (when-let [{:keys [start-node-id] :as graph} (get-graph context graph-def)]
                 (do-sync [resources (process-start-node db graph start-node-id resource-handle)]
                   (ring/response
                    {:fhir/type :fhir/Bundle
                     :id (m/luid context)
                     :type #fhir/code"searchset"
                     :total (type/unsignedInt (count resources))
                     :entry (mapv (partial search-util/entry request) resources)}))))))
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
  (s/keys :req-un [::graph-cache :blaze/clock :blaze/rng-fn :blaze/page-id-cipher]))

(defmethod ig/init-key :blaze.operation/graph [_ context]
  (log/info "Init FHIR Resource $graph operation handler")
  (-> (handler context)
      (wrap-batch-db)))

(defmethod m/pre-init-spec ::graph-cache [_]
  (s/keys :opt-un [::num-concepts]))

(defmethod ig/init-key ::graph-cache
  [_ {:keys [num-nodes] :or {num-nodes 10000}}]
  (log/info "Init operation $graph graph cache with a size of" num-nodes "nodes")
  (-> (Caffeine/newBuilder)
      (.maximumWeight num-nodes)
      (.weigher (fn [_ {:keys [nodes]}] (count nodes)))
      (.recordStats)
      (.build)))
