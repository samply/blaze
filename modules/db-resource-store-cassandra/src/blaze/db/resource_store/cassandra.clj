(ns blaze.db.resource-store.cassandra
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.byte-buffer :as bb]
   [blaze.cassandra :as cass]
   [blaze.cassandra.spec]
   [blaze.db.resource-store :as rs]
   [blaze.db.resource-store.cassandra.statement :as statement]
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
   [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)

(defhistogram duration-seconds
  "Durations in Cassandra resource store."
  {:namespace "blaze"
   :subsystem "db_resource_store_cassandra"}
  (take 12 (iterate #(* 2 %) 0.0001))
  "op")

(defhistogram resource-bytes
  "Stored resource sizes in bytes in Cassandra resource store."
  {:namespace "blaze"
   :subsystem "db_resource_store_cassandra"}
  (take 14 (iterate #(* 2 %) 128)))

(defn- execute [session op statement]
  (let [timer (prom/timer duration-seconds op)]
    (-> (cass/execute session statement)
        (ac/when-complete (fn [_ _] (prom/observe-duration! timer))))))

(defn- parse-msg [hash cause-msg]
  (format "Error while parsing resource content with hash `%s`: %s"
          hash cause-msg))

(defn- parse-cbor [parsing-context row [type hash variant]]
  (-> (fhir-spec/parse-cbor parsing-context type row variant)
      (ba/exceptionally
       #(assoc %
               ::anom/message (parse-msg hash (::anom/message %))
               :blaze.resource/hash hash))))

(defn- read-content [parsing-context result-set key]
  (when-ok [row (cass/first-row result-set)]
    (parse-cbor parsing-context row key)))

(defn- map-execute-get-error [hash e]
  (assoc e :op :get :blaze.resource/hash hash))

(defn- execute-get* [session parsing-context statement [_ hash :as key]]
  (-> (execute session "get" (cass/bind statement (str hash)))
      (ac/then-apply-async #(read-content parsing-context % key))
      (ac/exceptionally (partial map-execute-get-error hash))))

(defn- execute-get [session parsing-context statement key]
  (-> (ac/retry #(execute-get* session parsing-context statement key) 5)
      (ac/exceptionally #(when-not (ba/not-found? %) %))))

(defn- execute-multi-get [session parsing-context get-statement keys]
  (mapv #(ac/->completable-future (execute-get session parsing-context get-statement %)) keys))

(defn- bind-put [writing-context statement hash resource]
  (let [content (bb/wrap (fhir-spec/write-cbor writing-context resource))]
    (prom/observe! resource-bytes (.capacity content))
    (cass/bind statement (str hash) content)))

(defn- map-execute-put-error [hash {:fhir/keys [type] :keys [id]} e]
  (assoc e
         :op :put
         :blaze.resource/hash hash
         :fhir/type type
         :blaze.resource/id id))

(defn- execute-put* [session writing-context statement [hash resource]]
  (-> (execute session "put" (bind-put writing-context statement hash resource))
      (ac/exceptionally (partial map-execute-put-error hash resource))))

(defn- execute-put [session writing-context statement entry]
  (ac/retry #(execute-put* session writing-context statement entry) 5))

(defn- execute-multi-put [session writing-context statement entries]
  (map #(ac/->completable-future (execute-put session writing-context statement %)) entries))

(defn- zipmap-found [hashes resources]
  (loop [map (transient {})
         [hash & hashes] hashes
         [resource & resources] resources]
    (if hash
      (if resource
        (recur (assoc! map hash resource) hashes resources)
        (recur map hashes resources))
      (persistent! map))))

(deftype CassandraResourceStore [session parsing-context writing-context
                                 get-statement put-statement]
  rs/ResourceStore
  (-get [_ [_ hash :as key]]
    (log/trace "get resource with hash:" hash)
    (execute-get session parsing-context get-statement key))

  (-multi-get [_ keys]
    (log/trace "multi-get" (count keys) "resource(s)")
    (let [futures (execute-multi-get session parsing-context get-statement keys)]
      (do-sync [_ (ac/all-of futures)]
        (zipmap-found keys (map ac/join futures)))))

  (-put [_ entries]
    (log/trace "put" (count entries) "entries")
    (ac/all-of (execute-multi-put session writing-context put-statement entries)))

  AutoCloseable
  (close [_]
    (cass/close session)))

(defmethod m/pre-init-spec ::rs/cassandra [_]
  (s/keys :req-un [:blaze.fhir/parsing-context
                   :blaze.fhir/writing-context]
          :opt-un [::cass/contact-points ::cass/key-space
                   ::cass/username ::cass/password
                   ::cass/put-consistency-level
                   ::cass/max-concurrent-read-requests
                   ::cass/max-read-request-queue-size
                   ::cass/request-timeout]))

(defn- init-msg [config]
  (str "Open Cassandra resource store with the following settings: "
       (cass/format-config config)))

(defmethod ig/init-key ::rs/cassandra
  [_ {:keys [parsing-context writing-context put-consistency-level]
      :or {put-consistency-level "TWO"} :as config}]
  (log/info (init-msg (dissoc config :parsing-context :writing-context)))
  (let [session (cass/session config)]
    (->CassandraResourceStore
     session
     parsing-context
     writing-context
     (cass/prepare session statement/get-statement)
     (cass/prepare session (statement/put-statement put-consistency-level)))))

(defmethod ig/halt-key! ::rs/cassandra
  [_ store]
  (log/info "Close Cassandra resource store")
  (.close ^AutoCloseable store))

(derive ::rs/cassandra :blaze.db/resource-store)

(reg-collector ::duration-seconds
  duration-seconds)

(reg-collector ::resource-bytes
  resource-bytes)
