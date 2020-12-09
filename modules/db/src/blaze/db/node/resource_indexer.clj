(ns blaze.db.node.resource-indexer
  (:require
    [blaze.anomaly :refer [throw-anom]]
    [blaze.async.comp :as ac]
    [blaze.byte-string :as bs]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.compartment.resource :as cr]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.kv :as kv]
    [blaze.db.kv.spec]
    [blaze.db.resource-store :as rs]
    [blaze.db.search-param-registry :as sr]
    [blaze.executors :as ex]
    [blaze.fhir.spec :as fhir-spec]
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [prometheus.alpha :as prom :refer [defhistogram]]
    [taoensso.timbre :as log]))


(set! *warn-on-reflection* true)


(defhistogram duration-seconds
  "Durations in resource indexer."
  {:namespace "blaze"
   :subsystem "db"
   :name "resource_indexer_duration_seconds"}
  (take 14 (iterate #(* 2 %) 0.00001))
  "op")


(defn- compartment-resource-type-entry
  "Returns an entry into the :compartment-resource-type-index where `resource`
  is linked to `compartment`."
  {:arglists '([compartment resource])}
  [[comp-code comp-id] {:keys [id] :as resource}]
  (cr/index-entry
    [(codec/c-hash comp-code) (codec/id-byte-string comp-id)]
    (codec/tid (name (fhir-spec/fhir-type resource)))
    (codec/id-byte-string id)))


(defn- skip-indexing-msg [search-param resource cause-msg]
  (format "Skip indexing for search parameter `%s` on resource `%s/%s`. Cause: %s"
          (:url search-param) (name (fhir-spec/fhir-type resource))
          (:id resource) cause-msg))


(defn- index-entries [linked-compartments search-param hash resource]
  (let [res (search-param/index-entries search-param hash resource
                                        linked-compartments)]
    (if (::anom/category res)
      (log/warn (skip-indexing-msg search-param resource (::anom/message res)))
      res)))


(defn- calc-search-params [search-param-registry hash resource]
  (with-open [_ (prom/timer duration-seconds "calc-search-params")]
    (let [compartments (sr/linked-compartments search-param-registry resource)]
      (-> (into
            []
            (map #(compartment-resource-type-entry % resource))
            compartments)
          (into
            (mapcat #(index-entries compartments % hash resource))
            (sr/list-by-type search-param-registry
                             (name (fhir-spec/fhir-type resource))))))))


(defn- index-resource [search-param-registry [hash resource]]
  (log/trace "index-resource with hash" (bs/hex hash))
  (calc-search-params search-param-registry hash resource))


(defn- put [store entries]
  (with-open [_ (prom/timer duration-seconds "put")]
    (kv/put! store entries)))


(defn- missing-hashes-msg [missing-hashes]
  (format "Stop resource indexing because the resources with the following hashes are missing: %s"
          (str/join "," missing-hashes)))


(defn- index-resources**
  [{:keys [search-param-registry kv-store]} hashes resources]
  (let [missing-hashes (set/difference (set hashes) (set (keys resources)))]
    (if (empty? missing-hashes)
      (->> (into [] (mapcat #(index-resource search-param-registry %)) resources)
           (put kv-store))
      (throw-anom ::anom/fault (missing-hashes-msg missing-hashes)))))


(defn- index-resources*
  [{:keys [resource-lookup executor] :as indexer} hashes]
  (log/trace "index batch of" (count hashes) "resource(s)")
  (-> (rs/multi-get resource-lookup hashes)
      (ac/then-apply-async #(index-resources** indexer hashes %) executor)))


(defn- batch-index-resources [{:keys [batch-size] :as indexer} hashes]
  (into
    []
    (comp
      (partition-all batch-size)
      (map #(index-resources* indexer %)))
    hashes))


(defn index-resources
  "Returns a CompletableFuture that will complete after all resources with
  `hashes` are indexed."
  [resource-indexer hashes]
  (log/trace "index" (count hashes) "resource(s)")
  (ac/all-of (batch-index-resources resource-indexer hashes)))


(s/def :blaze.db.node/resource-indexer-executor
  ex/executor?)


(s/def :blaze.db.node/resource-indexer-batch-size
  nat-int?)


(defn new-resource-indexer
  [resource-lookup search-param-registry kv-store executor batch-size]
  {:resource-lookup resource-lookup
   :search-param-registry search-param-registry
   :kv-store kv-store
   :executor executor
   :batch-size batch-size})
