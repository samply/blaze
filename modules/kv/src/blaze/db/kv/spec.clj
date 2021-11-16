(ns blaze.db.kv.spec
  (:require
    [blaze.db.kv :as kv]
    [clojure.spec.alpha :as s])
  (:import
    [java.lang AutoCloseable]))


(s/def :blaze.db/kv-store
  kv/store?)


(s/def :blaze.db/kv-snapshot
  (s/and #(satisfies? kv/KvSnapshot %)
         #(instance? AutoCloseable %)))


(s/def :blaze.db/kv-iterator
  (s/and #(satisfies? kv/KvIterator %)
         #(instance? AutoCloseable %)))


(s/def ::kv/put-entry-wo-cf
  (s/tuple bytes? bytes?))


(s/def ::kv/put-entry-w-cf
  (s/tuple keyword? bytes? bytes?))


(s/def ::kv/put-entry
  (s/or :kv ::kv/put-entry-wo-cf
        :cf-kv ::kv/put-entry-w-cf))


(s/def ::kv/column-families
  (s/map-of keyword (s/nilable map?)))
