(ns blaze.db.kv.spec
  (:require
    [blaze.db.kv :as kv]
    [clojure.spec.alpha :as s])
  (:import
    [java.nio ByteBuffer]))


(s/def :blaze.db/kv-store
  #(satisfies? kv/KvStore %))


(s/def :blaze.db/kv-snapshot
  #(satisfies? kv/KvSnapshot %))


(s/def :blaze.db/kv-iterator
  #(satisfies? kv/KvIterator %))


(s/fdef kv/valid?
  :args (s/cat :iter :blaze.db/kv-iterator)
  :ret boolean?)


(s/fdef kv/seek-to-first!
  :args (s/cat :iter :blaze.db/kv-iterator))


(s/fdef kv/seek-to-last!
  :args (s/cat :iter :blaze.db/kv-iterator))


(s/fdef kv/seek!
  :args (s/cat :iter :blaze.db/kv-iterator :target bytes?))


(s/fdef kv/seek-for-prev!
  :args (s/cat :iter :blaze.db/kv-iterator :target bytes?))


(s/fdef kv/next!
  :args (s/cat :iter :blaze.db/kv-iterator))


(s/fdef kv/prev!
  :args (s/cat :iter :blaze.db/kv-iterator))


(s/fdef kv/key
  :args (s/cat :iter :blaze.db/kv-iterator :buf (s/? #(instance? ByteBuffer %))))


(s/fdef kv/value
  :args (s/cat :iter :blaze.db/kv-iterator :buf (s/? #(instance? ByteBuffer %))))


(s/fdef kv/new-iterator
  :args (s/cat :snapshot :blaze.db/kv-snapshot
               :column-family (s/? keyword?))
  :ret :blaze.db/kv-iterator)


(s/fdef kv/new-snapshot
  :args (s/cat :kv-store :blaze.db/kv-store)
  :ret :blaze.db/kv-snapshot)


(s/fdef kv/get
  :args (s/cat :kv-store :blaze.db/kv-store :column-family (s/? keyword?)
               :key bytes?)
  :ret bytes?)


(s/def :blaze.db.kv/put-entry-wo-cf
  (s/tuple bytes? bytes?))


(s/def :blaze.db.kv/put-entry-w-cf
  (s/tuple keyword? bytes? bytes?))


(s/def :blaze.db.kv/put-entry
  (s/or :kv :blaze.db.kv/put-entry-wo-cf
        :cf-kv :blaze.db.kv/put-entry-w-cf))


(s/fdef kv/put
  :args (s/alt
          :entries (s/cat :kv-store :blaze.db/kv-store :entries (s/coll-of :blaze.db.kv/put-entry))
          :kv (s/cat :kv-store :blaze.db/kv-store :key bytes? :value bytes?)))

