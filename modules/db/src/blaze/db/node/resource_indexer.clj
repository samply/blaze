(ns blaze.db.node.resource-indexer
  (:require
    [blaze.async.comp :as ac]
    [blaze.byte-string :as bs]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.compartment.resource :as cr]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.kv :as kv]
    [blaze.db.kv.spec]
    [blaze.db.search-param-registry :as sr]
    [blaze.fhir.spec :as fhir-spec]
    [clojure.core.reducers :as r]
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


(def ^:private available-processors
  (.availableProcessors (Runtime/getRuntime)))


(def ^:private num-work-units
  "The target number of work units carried out in parallel while indexing a coll
  of resources.

  The idea is, that we want to have 4 work units per available processor.
  The number of 4 was chosen for the following reason. Individual work units
  will vary greatly in size because the number of index entries of a resource
  will vary based on the number of search parameters activated for this resource
  type and data available. Choosing one work unit per processor would mean that
  each processor gets one work unit and the biggest work unit will dominate the
  overall time needed."
  (* 4 available-processors))


(defn- batch-size [num-resources]
  (max (quot num-resources num-work-units) 1))


(defn- compartment-resource-type-entry
  "Returns an entry into the :compartment-resource-type-index where `resource`
  is linked to `compartment`."
  {:arglists '([compartment resource])}
  [[comp-code comp-id] {:keys [id] :as resource}]
  (cr/index-entry
    [(codec/c-hash comp-code) (codec/id-byte-string comp-id)]
    (codec/tid (name (fhir-spec/fhir-type resource)))
    (codec/id-byte-string id)))


(defn- conj-compartment-resource-type-entries! [res resource compartments]
  (transduce
    (map #(compartment-resource-type-entry % resource))
    conj!
    res
    compartments))


(defn- skip-indexing-msg [search-param resource cause-msg]
  (format "Skip indexing for search parameter `%s` on resource `%s/%s`. Cause: %s"
          (:url search-param) (name (fhir-spec/fhir-type resource))
          (:id resource) cause-msg))


(defn- index-entries [search-param linked-compartments hash resource]
  (let [res (search-param/index-entries search-param linked-compartments
                                        hash resource)]
    (if (::anom/category res)
      (log/warn (skip-indexing-msg search-param resource (::anom/message res)))
      res)))


(defn- conj-index-entries!
  [search-param-registry last-updated res [hash resource]]
  (log/trace "index-resource with hash" (bs/hex hash))
  (with-open [_ (prom/timer duration-seconds "calc-search-params")]
    (let [resource (update resource :meta (fnil assoc #fhir/Meta{}) :lastUpdated last-updated)
          compartments (sr/linked-compartments search-param-registry resource)]
      (transduce
        (mapcat #(index-entries % compartments hash resource))
        conj!
        (conj-compartment-resource-type-entries! res resource compartments)
        (sr/list-by-type search-param-registry (name (:fhir/type resource)))))))


(defn- put! [store entries]
  (with-open [_ (prom/timer duration-seconds "put")]
    (kv/put! store entries)))


(defn- batch-index-resources
  [{:keys [kv-store search-param-registry]} last-updated entries]
  (->> entries
       (r/fold
         (batch-size (count entries))
         (fn combine
           ([] (transient []))
           ([index-entries-a index-entries-b]
            (put! kv-store (persistent! index-entries-a))
            (put! kv-store (persistent! index-entries-b))
            (transient [])))
         (partial conj-index-entries! search-param-registry last-updated))
       (persistent!)
       (put! kv-store)))


(defn index-resources
  "Returns a CompletableFuture that will complete after all resources of
   `entries` are indexed.

   `last-updated` is used to index the _lastUpdated search parameter because the
   property doesn't exist in the resource itself."
  [resource-indexer last-updated entries]
  (log/trace "index" (count entries) "resource(s)")
  (ac/supply-async
    #(batch-index-resources resource-indexer last-updated (vec entries))))


(defn new-resource-indexer
  [search-param-registry kv-store]
  {:search-param-registry search-param-registry
   :kv-store kv-store})
