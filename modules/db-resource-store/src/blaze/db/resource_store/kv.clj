(ns blaze.db.resource-store.kv
  "A resource store implementation that uses a kev-value store as backend."
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac :refer [do-async do-sync]]
   [blaze.coll.core :as coll]
   [blaze.db.kv :as kv]
   [blaze.db.kv.spec]
   [blaze.db.resource-store :as rs]
   [blaze.db.resource-store.kv.spec]
   [blaze.executors :as ex]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.parsing-context.spec]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.writing-context.spec]
   [blaze.module :as m :refer [reg-collector]]
   [clojure.spec.alpha :as s]
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
   :subsystem "db_resource_store_kv"}
  (take 16 (iterate #(* 2 %) 32)))

(defhistogram duration-seconds
  "Durations in key-value resource store."
  {:namespace "blaze"
   :subsystem "db_resource_store_kv"}
  (take 16 (iterate #(* 2 %) 1e-6))
  "op")

(defn- parse-msg [cause-msg hash]
  (format "Error while parsing resource content with hash `%s`: %s"
          hash cause-msg))

(defn- parse-anom [e hash]
  (-> (update e ::anom/message parse-msg hash)
      (assoc :blaze.resource/hash hash)))

(defn- parse-cbor [parsing-context bytes [type hash variant]]
  (with-open [_ (prom/timer duration-seconds "parse-resource")]
    (-> (fhir-spec/parse-cbor parsing-context type bytes variant)
        (ba/exceptionally #(parse-anom % hash)))))

(defn- get-content [kv-store hash]
  (with-open [_ (prom/timer duration-seconds "get-resource")]
    (kv/get kv-store :default (hash/to-byte-array hash))))

(defn- get-content-async [kv-store executor hash]
  (ac/supply-async #(get-content kv-store hash) executor))

(defn- get-and-parse-async [kv-store parsing-context executor [_ hash :as key]]
  (do-async [bytes (get-content-async kv-store executor hash)]
    (when bytes
      (parse-cbor parsing-context bytes key))))

(defn- multi-get-and-parse-async [kv-store parsing-context executor keys]
  (mapv (partial get-and-parse-async kv-store parsing-context executor) keys))

(defn- zipmap-found [hashes resources]
  (loop [map (transient {})
         [hash & hashes] hashes
         [resource & resources] resources]
    (if hash
      (if resource
        (recur (assoc! map hash resource) hashes resources)
        (recur map hashes resources))
      (persistent! map))))

(deftype KvResourceStore [kv-store parsing-context entry-freezer executor]
  rs/ResourceStore
  (-get [_ key]
    (get-and-parse-async kv-store parsing-context executor key))

  (-multi-get [_ keys]
    (log/trace "multi-get" (count keys) "hash(es)")
    (let [futures (multi-get-and-parse-async kv-store parsing-context executor keys)]
      (do-sync [_ (ac/all-of futures)]
        (zipmap-found keys (map ac/join futures)))))

  (-put [_ entries]
    (ac/supply-async
     #(with-open [_ (prom/timer duration-seconds "put-resources")]
        (kv/put! kv-store (coll/eduction entry-freezer entries)))
     executor)))

(defmethod m/pre-init-spec ::rs/kv [_]
  (s/keys :req-un [:blaze.db/kv-store :blaze.fhir/parsing-context
                   :blaze.fhir/writing-context ::executor]))

(defn- entry-freezer [writing-context]
  (map
   (fn [[hash resource]]
     (let [content (fhir-spec/write-cbor writing-context resource)]
       (prom/observe! resource-bytes (alength ^bytes content))
       [:default (hash/to-byte-array hash) content]))))

(defmethod ig/init-key ::rs/kv
  [_ {:keys [kv-store parsing-context writing-context executor]}]
  (log/info "Open key-value store backed resource store.")
  (->KvResourceStore kv-store parsing-context (entry-freezer writing-context)
                     executor))

(derive ::rs/kv :blaze.db/resource-store)

(defmethod m/pre-init-spec ::executor [_]
  (s/keys :opt-un [::num-threads]))

(defn- executor-init-msg [num-threads]
  (format "Init resource store key-value executor with %d threads" num-threads))

(defmethod ig/init-key ::executor
  [_ {:keys [num-threads] :or {num-threads 4}}]
  (log/info (executor-init-msg num-threads))
  (ex/io-pool num-threads "resource-store-kv-%d"))

(defmethod ig/halt-key! ::executor
  [_ executor]
  (log/info "Stopping resource store key-value executor...")
  (ex/shutdown! executor)
  (if (ex/await-termination executor 10 TimeUnit/SECONDS)
    (log/info "Resource store key-value executor was stopped successfully")
    (log/warn "Got timeout while stopping the resource store key-value executor")))

(derive ::executor :blaze.metrics/thread-pool-executor)

(reg-collector ::resource-bytes
  resource-bytes)

(reg-collector ::duration-seconds
  duration-seconds)
