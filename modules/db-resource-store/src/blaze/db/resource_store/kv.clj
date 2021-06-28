(ns blaze.db.resource-store.kv
  (:require
    [blaze.anomaly :refer [ex-anom]]
    [blaze.async.comp :as ac]
    [blaze.byte-string :as bs]
    [blaze.coll.core :as coll]
    [blaze.db.kv :as kv]
    [blaze.db.kv.spec]
    [blaze.db.resource-store :as rs]
    [blaze.db.resource-store.kv.spec]
    [blaze.executors :as ex]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.module :refer [reg-collector]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [prometheus.alpha :as prom :refer [defhistogram]]
    [taoensso.timbre :as log])
  (:import
    [java.util.concurrent ExecutorService TimeUnit]))


(defhistogram resource-bytes
  "Stored resource sizes in bytes in key-value resource store."
  {:namespace "blaze"
   :subsystem "db"
   :name "resource_store_kv_resource_bytes"}
  (take 16 (iterate #(* 2 %) 32)))


(defn- parse-msg [hash e]
  (format "Error while parsing resource content with hash `%s`: %s"
          (bs/hex hash) (ex-message e)))


(defn- parse-anom [hash e]
  (ex-anom #::anom{:category ::anom/fault :message (parse-msg hash e)}))


(defn- conform-cbor [bytes hash]
  (try
    (fhir-spec/conform-cbor (fhir-spec/parse-cbor bytes))
    (catch Exception e
      (throw (parse-anom hash e)))))


(def ^:private entry-thawer
  (map
    (fn [[k v]]
      (let [hash (bs/from-byte-array k)]
        [hash (conform-cbor v hash)]))))


(def ^:private entry-freezer
  (map
    (fn [[k v]]
      (let [content (fhir-spec/unform-cbor v)]
        (prom/observe! resource-bytes (alength content))
        [(bs/to-byte-array k) content]))))


(defn- get-content [kv-store hash]
  (kv/get kv-store (bs/to-byte-array hash)))


(defn- multi-get-content [kv-store hashes]
  (kv/multi-get kv-store (mapv bs/to-byte-array hashes)))


(deftype KvResourceStore [kv-store executor]
  rs/ResourceLookup
  (-get [_ hash]
    (ac/supply-async
      #(some-> (get-content kv-store hash)
               (conform-cbor hash))
      executor))

  (-multi-get [_ hashes]
    (log/trace "multi-get" (count hashes) "hash(es)")
    (ac/supply-async
      #(into {} entry-thawer (multi-get-content kv-store hashes))
      executor))

  rs/ResourceStore
  (-put [_ entries]
    (ac/supply (kv/put! kv-store (coll/eduction entry-freezer entries)))))


(defn new-kv-resource-store [kv-store executor]
  (->KvResourceStore kv-store executor))


(defmethod ig/pre-init-spec ::rs/kv [_]
  (s/keys :req-un [:blaze.db/kv-store ::executor]))


(defmethod ig/init-key ::rs/kv
  [_ {:keys [kv-store executor]}]
  (log/info "Open key-value store backed resource store.")
  (new-kv-resource-store kv-store executor))


(derive ::rs/kv :blaze.db/resource-store)


(defn- executor-init-msg [num-threads]
  (format "Init resource store key-value executor with %d threads" num-threads))


(defmethod ig/init-key ::executor
  [_ {:keys [num-threads] :or {num-threads 4}}]
  (log/info (executor-init-msg num-threads))
  (ex/io-pool num-threads "resource-store-kv-%d"))


(defmethod ig/halt-key! ::executor
  [_ ^ExecutorService executor]
  (log/info "Stopping resource store key-value executor...")
  (.shutdown executor)
  (if (.awaitTermination executor 10 TimeUnit/SECONDS)
    (log/info "Resource store key-value executor was stopped successfully")
    (log/warn "Got timeout while stopping the resource store key-value executor")))


(derive ::executor :blaze.metrics/thread-pool-executor)


(reg-collector ::resource-bytes
  resource-bytes)
