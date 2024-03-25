(ns blaze.db.resource-store.kv
  "A resource store implementation that uses a kev-value store as backend."
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.coll.core :as coll]
   [blaze.db.kv :as kv]
   [blaze.db.kv.spec]
   [blaze.db.resource-store :as rs]
   [blaze.db.resource-store.kv.spec]
   [blaze.executors :as ex]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.type.string-util :as su]
   [blaze.module :refer [reg-collector]]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [prometheus.alpha :as prom :refer [defhistogram]]
   [taoensso.timbre :as log])
  (:import
   [java.util.concurrent TimeUnit]))

(set! *warn-on-reflection* true)

(defhistogram resource-bytes
  "Stored resource sizes in key-value resource store."
  {:namespace "blaze"
   :subsystem "db"
   :name "resource_store_kv_resource_bytes"}
  (take 16 (iterate #(* 2 %) 32)))

(defhistogram duration-seconds
  "Durations in key-value resource store."
  {:namespace "blaze"
   :subsystem "db"
   :name "resource_store_kv_duration_seconds"}
  (take 16 (iterate #(* 2 %) 1e-6))
  "op")

(defn- parse-msg [hash cause-msg]
  (format "Error while parsing resource content with hash `%s`: %s"
          hash cause-msg))

(defn- parse-cbor [bytes hash]
  (-> (fhir-spec/parse-cbor bytes)
      (ba/exceptionally
       #(assoc %
               ::anom/message (parse-msg hash (::anom/message %))
               :blaze.resource/hash hash))))

(defn- conform-msg [hash]
  (format "Error while conforming resource content with hash `%s`." hash))

(defn- conform-cbor [x hash]
  (-> (fhir-spec/conform-cbor x)
      (ba/exceptionally
       (fn [_]
         (ba/fault
          (conform-msg hash)
          :blaze.resource/hash hash)))))

(defn- parse-and-conform-cbor [bytes hash]
  (with-open [_ (prom/timer duration-seconds "parse-resource")]
    (when-ok [x (parse-cbor bytes hash)]
      (conform-cbor x hash))))

(def ^:private entry-freezer
  (map
   (fn [[hash resource]]
     (let [content (fhir-spec/unform-cbor resource)]
       (prom/observe! resource-bytes (alength ^bytes content))
       [:default (hash/to-byte-array hash) content]))))

(defn- get-content [kv-store hash]
  (with-open [_ (prom/timer duration-seconds "get-resource")]
    (kv/get kv-store :default (hash/to-byte-array hash))))

(defn- get-and-parse [kv-store hash]
  (some-> (get-content kv-store hash) (parse-and-conform-cbor hash)))

(defn- get-and-parse-async [kv-store executor hash]
  (ac/supply-async #(get-and-parse kv-store hash) executor))

(defn- multi-get-and-parse-async [kv-store executor hashes]
  (mapv (partial get-and-parse-async kv-store executor) hashes))

(defn- zipmap-found [hashes resources]
  (loop [map (transient {})
         [hash & hashes] hashes
         [resource & resources] resources]
    (if hash
      (if resource
        (recur (assoc! map hash resource) hashes resources)
        (recur map hashes resources))
      (persistent! map))))

(deftype KvResourceStore [kv-store executor]
  rs/ResourceStore
  (-get [_ hash]
    (get-and-parse-async kv-store executor hash))

  (-multi-get [_ hashes]
    (log/trace "multi-get" (count hashes) "hash(es)")
    (let [futures (multi-get-and-parse-async kv-store executor hashes)]
      (do-sync [_ (ac/all-of futures)]
        (zipmap-found hashes (map ac/join futures)))))

  (-put [_ entries]
    (ac/supply-async
     #(with-open [_ (prom/timer duration-seconds "put-resources")]
        (kv/put! kv-store (coll/eduction entry-freezer entries)))
     executor)))

(defmethod ig/pre-init-spec ::rs/kv [_]
  (s/keys :req-un [:blaze.db/kv-store] :opt-un [::executor]))

(defmethod ig/init-key ::rs/kv
  [_ {:keys [kv-store executor]}]
  (log/info "Open key-value store backed resource store.")
  (->KvResourceStore kv-store (or executor (ex/single-thread-executor))))

(defmethod ig/pre-init-spec ::executor [_]
  (s/keys :opt-un [::num-threads]))

(defn- name-part [[_ key]]
  (-> key namespace (str/split #"\.") last))

(defn- executor-name [key]
  (cond->> "resource store key-value executor"
    (vector? key)
    (str (name-part key) " ")))

(defn- thread-name-template [key]
  (cond->> "resource-store-kv-%d"
    (vector? key)
    (str (name-part key) "-")))

(defmethod ig/init-key ::executor
  [key {:keys [num-threads] :or {num-threads 4}}]
  (log/info "Init" (executor-name key) "with" num-threads "threads")
  (ex/io-pool num-threads (thread-name-template key)))

(defmethod ig/halt-key! ::executor
  [key executor]
  (let [name (executor-name key)]
    (log/info "Stopping" name)
    (ex/shutdown! executor)
    (if (ex/await-termination executor 10 TimeUnit/SECONDS)
      (log/info (su/capital name) "was stopped successfully")
      (log/warn "Got timeout while stopping the" name))))

(derive ::executor :blaze.metrics/thread-pool-executor)

(reg-collector ::resource-bytes
  resource-bytes)

(reg-collector ::duration-seconds
  duration-seconds)
