(ns blaze.db.resource-store.kv
  (:require
    [blaze.anomaly :refer [ex-anom]]
    [blaze.async.comp :as ac]
    [blaze.byte-string :as bs]
    [blaze.db.kv :as kv]
    [blaze.db.kv.spec]
    [blaze.db.resource-store :as rs]
    [blaze.fhir.spec :as fhir-spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [taoensso.timbre :as log]))


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
      [(bs/to-byte-array k) (fhir-spec/unform-cbor v)])))


(defn- get-content [kv-store hash]
  (kv/get kv-store (bs/to-byte-array hash)))


(defn- multi-get-content [kv-store hashes]
  (kv/multi-get kv-store (mapv bs/to-byte-array hashes)))


(deftype KvResourceStore [kv-store]
  rs/ResourceLookup
  (-get [_ hash]
    (ac/supply
      (some-> (get-content kv-store hash)
              (conform-cbor hash))))

  (-multi-get [_ hashes]
    (log/trace "multi-get" (count hashes) "hash(es)")
    (ac/supply (into {} entry-thawer (multi-get-content kv-store hashes))))

  rs/ResourceStore
  (-put [_ entries]
    (ac/supply (kv/put! kv-store (into [] entry-freezer entries)))))


(defn new-kv-resource-store [kv-store]
  (->KvResourceStore kv-store))


(defmethod ig/pre-init-spec ::rs/kv [_]
  (s/keys :req-un [:blaze.db/kv-store]))


(defmethod ig/init-key ::rs/kv
  [_ {:keys [kv-store]}]
  (log/info "Open key-value store backed resource store.")
  (new-kv-resource-store kv-store))


(derive ::rs/kv :blaze.db/resource-store)
