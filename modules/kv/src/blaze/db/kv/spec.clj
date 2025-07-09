(ns blaze.db.kv.spec
  (:require
   [blaze.byte-string :as bs]
   [blaze.db.kv :as kv]
   [blaze.db.kv.protocols :as p]
   [clojure.spec.alpha :as s])
  (:import
   [java.lang AutoCloseable]))

(s/def :blaze.db/kv-store
  #(satisfies? p/KvStore %))

(s/def ::kv/snapshot
  (s/and #(satisfies? p/KvSnapshot %)
         #(instance? AutoCloseable %)))

(s/def ::kv/iterator
  (s/and #(satisfies? p/KvIterator %)
         #(instance? AutoCloseable %)))

(s/def ::kv/put-entry
  (s/tuple keyword? bytes? bytes?))

(s/def ::kv/delete-entry
  (s/tuple keyword? bytes?))

(defmulti write-entry first)

(defmethod write-entry :put [_]
  (s/cat :op #{:put} :column-family simple-keyword? :key bytes? :val bytes?))

(defmethod write-entry :merge [_]
  (s/cat :op #{:merge} :column-family simple-keyword? :key bytes? :val bytes?))

(defmethod write-entry :delete [_]
  (s/cat :op #{:delete} :column-family simple-keyword? :key bytes?))

(s/def ::kv/write-entry
  (s/multi-spec write-entry first))

(s/def ::kv/column-families
  (s/map-of keyword (s/nilable map?)))

(s/def ::kv/key-range
  (s/tuple bs/byte-string? bs/byte-string?))
