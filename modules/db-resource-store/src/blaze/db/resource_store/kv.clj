(ns blaze.db.resource-store.kv
  "A resource store implementation that uses a kev-value store as backend."
  (:require
    [blaze.anomaly :as ba :refer [when-ok]]
    [blaze.anomaly-spec]
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


(defn- parse-msg [hash cause-msg]
  (format "Error while parsing resource content with hash `%s`: %s"
          (bs/hex hash) cause-msg))


(defn- parse-cbor [bytes hash]
  (-> (fhir-spec/parse-cbor bytes)
      (ba/exceptionally
        #(assoc %
           ::anom/message (parse-msg hash (::anom/message %))
           :blaze.resource/hash hash))))


(defn- conform-msg [hash]
  (format "Error while conforming resource content with hash `%s`."
          (bs/hex hash)))


(defn- conform-cbor [x hash]
  (-> (fhir-spec/conform-cbor x)
      (ba/exceptionally
        (fn [_]
          (ba/fault
            (conform-msg hash)
            :blaze.resource/hash hash)))))


(defn- parse-and-conform-cbor [bytes hash]
  (when-ok [x (parse-cbor bytes hash)]
    (conform-cbor x hash)))


(def ^:private entry-thawer
  (comp
    (map
      (fn [[k v]]
        (let [hash (bs/from-byte-array k)]
          (when-ok [resource (parse-and-conform-cbor v hash)]
            [hash resource]))))
    (halt-when ba/anomaly?)))


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
  rs/ResourceStore
  (-get [_ hash]
    (ac/supply-async
      #(some-> (get-content kv-store hash)
               (parse-and-conform-cbor hash))
      executor))

  (-multi-get [_ hashes]
    (log/trace "multi-get" (count hashes) "hash(es)")
    (ac/supply-async
      #(transduce entry-thawer conj {} (multi-get-content kv-store hashes))
      executor))

  (-put [_ entries]
    (ac/supply-async
      #(kv/put! kv-store (coll/eduction entry-freezer entries))
      executor)))


(defmethod ig/pre-init-spec ::rs/kv [_]
  (s/keys :req-un [:blaze.db/kv-store ::executor]))


(defmethod ig/init-key ::rs/kv
  [_ {:keys [kv-store executor]}]
  (log/info "Open key-value store backed resource store.")
  (->KvResourceStore kv-store executor))


(derive ::rs/kv :blaze.db/resource-store)


(defmethod ig/pre-init-spec ::executor [_]
  (s/keys :opt-un [::num-threads]))


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
