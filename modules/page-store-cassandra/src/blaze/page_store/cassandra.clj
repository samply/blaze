(ns blaze.page-store.cassandra
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.byte-buffer :as bb]
   [blaze.cassandra :as cass]
   [blaze.cassandra.spec]
   [blaze.module :as m :refer [reg-collector]]
   [blaze.page-store :as-alias page-store]
   [blaze.page-store.cassandra.codec :as codec]
   [blaze.page-store.cassandra.statement :as statement]
   [blaze.page-store.protocols :as p]
   [blaze.page-store.spec]
   [blaze.page-store.token :as token]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [prometheus.alpha :as prom :refer [defhistogram]]
   [taoensso.timbre :as log])
  (:import
   [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)

(defhistogram duration-seconds
  "Durations in Cassandra page store."
  {:namespace "blaze"
   :subsystem "page_store_cassandra"}
  (take 12 (iterate #(* 2 %) 0.0001))
  "op")

(defhistogram clauses-bytes
  "Stored clauses sizes in bytes in Cassandra page store."
  {:namespace "blaze"
   :subsystem "page_store_cassandra"}
  (take 10 (iterate #(* 2 %) 128)))

(defn- execute [session op statement]
  (let [timer (prom/timer duration-seconds op)]
    (-> (cass/execute session statement)
        (ac/when-complete (fn [_ _] (prom/observe-duration! timer))))))

(defn- read-content [result-set token]
  (when-ok [row (cass/first-row result-set)]
    (codec/decode row token)))

(defn- map-execute-get-error [token e]
  (assoc e :op :get ::page-store/token token))

(defn- execute-get* [session statement token]
  (-> (execute session "get" (cass/bind statement token))
      (ac/then-apply-async #(read-content % token))
      (ac/exceptionally (partial map-execute-get-error token))))

(defn- execute-get [session statement token]
  (-> (ac/retry #(execute-get* session statement token) 5)
      (ac/exceptionally #(when-not (ba/not-found? %) %))))

(defn- bind-put [statement token clauses]
  (let [^bytes content (codec/encode clauses)]
    (prom/observe! clauses-bytes (alength content))
    (cass/bind statement token (bb/wrap content))))

(defn- map-execute-put-error [token clauses e]
  (assoc e
         :op :put
         ::page-store/token token
         :blaze.db.query/clauses clauses))

(defn- execute-put* [session statement token clauses]
  (-> (execute session "put" (bind-put statement token clauses))
      (ac/exceptionally (partial map-execute-put-error token clauses))))

(defn- execute-put [session statement token clauses]
  (ac/retry #(execute-put* session statement token clauses) 5))

(defrecord CassandraPageStore [session get-statement put-statement]
  p/PageStore
  (-get [_ token]
    (log/trace "get" token)
    (execute-get session get-statement token))

  (-put [_ clauses]
    (let [token (token/generate clauses)]
      (log/trace "put" token)
      (do-sync [_ (execute-put session put-statement token clauses)]
        token)))

  AutoCloseable
  (close [_]
    (cass/close session)))

(defmethod m/pre-init-spec ::page-store/cassandra [_]
  (s/keys :opt-un [::cass/contact-points ::cass/key-space
                   ::cass/username ::cass/password
                   ::cass/put-consistency-level
                   ::cass/max-concurrent-read-requests
                   ::cass/max-read-request-queue-size
                   ::cass/request-timeout]))

(defn- init-msg [config]
  (str "Open Cassandra page store with the following settings: "
       (cass/format-config config)))

(defmethod ig/init-key ::page-store/cassandra
  [_ {:keys [put-consistency-level]
      :or {put-consistency-level "TWO"} :as config}]
  (log/info (init-msg config))
  (let [session (cass/session config)]
    (->CassandraPageStore
     session
     (cass/prepare session statement/get-statement)
     (cass/prepare session (statement/put-statement put-consistency-level)))))

(defmethod ig/halt-key! ::page-store/cassandra
  [_ store]
  (log/info "Close Cassandra page store")
  (.close ^AutoCloseable store))

(derive ::page-store/cassandra :blaze/page-store)

(reg-collector ::duration-seconds
  duration-seconds)

(reg-collector ::resource-bytes
  clauses-bytes)
