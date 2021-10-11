(ns blaze.db.resource-store.cassandra
  (:require
    [blaze.anomaly :as ba :refer [when-ok]]
    [blaze.anomaly-spec]
    [blaze.async.comp :as ac :refer [do-sync]]
    [blaze.byte-string :as bs]
    [blaze.cassandra :as cass]
    [blaze.cassandra.spec]
    [blaze.db.resource-store :as rs]
    [blaze.db.resource-store.cassandra.statement :as statement]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.module :refer [reg-collector]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [prometheus.alpha :as prom :refer [defhistogram]]
    [taoensso.timbre :as log])
  (:import
    [java.io Closeable]
    [java.nio ByteBuffer]))


(set! *warn-on-reflection* true)


(defhistogram duration-seconds
  "Durations in Cassandra resource store."
  {:namespace "blaze"
   :subsystem "db"
   :name "resource_store_cassandra_duration_seconds"}
  (take 12 (iterate #(* 2 %) 0.0001))
  "op")


(defhistogram resource-bytes
  "Stored resource sizes in bytes in Cassandra resource store."
  {:namespace "blaze"
   :subsystem "db"
   :name "resource_store_cassandra_resource_bytes"}
  (take 14 (iterate #(* 2 %) 128)))


(defn- execute [session op statement]
  (let [timer (prom/timer duration-seconds op)]
    (-> (cass/execute session statement)
        (ac/when-complete (fn [_ _] (prom/observe-duration! timer))))))


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


(defn- read-content [result-set hash]
  (when-ok [row (cass/first-row result-set)
            x (parse-cbor row hash)]
    (conform-cbor x hash)))


(defn- map-execute-get-error [hash e]
  (assoc e :op :get :blaze.resource/hash hash))


(defn- execute-get* [session statement hash]
  (-> (execute session "get" (cass/bind statement (bs/hex hash)))
      (ac/then-apply-async #(read-content % hash))
      (ac/exceptionally (partial map-execute-get-error hash))))


(defn- execute-get [session statement hash]
  (-> (ac/retry #(execute-get* session statement hash) 5)
      (ac/exceptionally #(when-not (ba/not-found? %) %))))


(defn- execute-multi-get [session get-statement hashes]
  (mapv #(ac/->completable-future (execute-get session get-statement %)) hashes))


(defn- bind-put [statement hash resource]
  (let [content (ByteBuffer/wrap (fhir-spec/unform-cbor resource))]
    (prom/observe! resource-bytes (.capacity content))
    (cass/bind statement (bs/hex hash) content)))


(defn- map-execute-put-error [hash {:fhir/keys [type] :keys [id]} e]
  (assoc e
    :op :put
    :blaze.resource/hash hash
    :fhir/type type
    :blaze.resource/id id))


(defn- execute-put* [session statement [hash resource]]
  (-> (execute session "put" (bind-put statement hash resource))
      (ac/exceptionally (partial map-execute-put-error hash resource))))


(defn- execute-put [session statement entry]
  (ac/retry #(execute-put* session statement entry) 5))


(defn- execute-multi-put [session statement entries]
  (map #(ac/->completable-future (execute-put session statement %)) entries))


(defn- zipmap-found [hashes resources]
  (loop [map {}
         [hash & hashes] hashes
         [resource & resources] resources]
    (if hash
      (if resource
        (recur (assoc map hash resource) hashes resources)
        (recur map hashes resources))
      map)))


(deftype CassandraResourceStore [session get-statement put-statement]
  rs/ResourceLookup
  (-get [_ hash]
    (log/trace "get" hash)
    (execute-get session get-statement hash))

  (-multi-get [_ hashes]
    (log/trace "multi-get" (count hashes) "resource(s)")
    (let [futures (execute-multi-get session get-statement hashes)]
      (do-sync [_ (ac/all-of futures)]
        (zipmap-found hashes (map deref futures)))))

  rs/ResourceStore
  (-put [_ entries]
    (log/trace "put" (count entries) "entries")
    (ac/all-of (execute-multi-put session put-statement entries)))

  Closeable
  (close [_]
    (cass/close session)))


(defmethod ig/pre-init-spec ::rs/cassandra [_]
  (s/keys :opt-un [::cass/contact-points ::cass/key-space
                   ::cass/username ::cass/password
                   ::cass/put-consistency-level
                   ::cass/max-concurrent-read-requests
                   ::cass/max-read-request-queue-size
                   ::cass/request-timeout]))


(defn init-msg [config]
  (str "Open Cassandra resource store with the following settings: "
       (cass/format-config config)))


(defmethod ig/init-key ::rs/cassandra
  [_ {:keys [put-consistency-level]
      :or {put-consistency-level "TWO"} :as config}]
  (log/info (init-msg config))
  (let [options (cass/options config)
        session (cass/session config options)]
    (->CassandraResourceStore
      session
      (cass/prepare session statement/get-statement)
      (cass/prepare session (statement/put-statement put-consistency-level)))))


(defmethod ig/halt-key! ::rs/cassandra
  [_ store]
  (log/info "Close Cassandra resource store")
  (.close ^Closeable store))


(derive ::rs/cassandra :blaze.db/resource-store)


(reg-collector ::duration-seconds
  duration-seconds)


(reg-collector ::resource-bytes
  resource-bytes)
