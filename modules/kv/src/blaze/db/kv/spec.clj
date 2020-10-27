(ns blaze.db.kv.spec
  (:require
    [blaze.db.kv :as kv]
    [clojure.spec.alpha :as s]))


(s/def :blaze.db/kv-store
  #(satisfies? kv/KvStore %))


(s/def :blaze.db/kv-snapshot
  #(satisfies? kv/KvSnapshot %))


(s/def :blaze.db/kv-iterator
  #(satisfies? kv/KvIterator %))


(s/def :blaze.db.kv/put-entry-wo-cf
  (s/tuple bytes? bytes?))


(s/def :blaze.db.kv/put-entry-w-cf
  (s/tuple keyword? bytes? bytes?))


(s/def :blaze.db.kv/put-entry
  (s/or :kv :blaze.db.kv/put-entry-wo-cf
        :cf-kv :blaze.db.kv/put-entry-w-cf))
