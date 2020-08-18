(ns blaze.db.impl.index-spec
  (:require
    [blaze.db.impl.codec-spec]
    [blaze.db.impl.index :as index]
    [blaze.db.impl.iterators-spec]
    [blaze.db.impl.search-param-spec]
    [blaze.db.kv-spec]
    [clojure.spec.alpha :as s]))


(s/fdef index/t-by-instant
  :args (s/cat :snapshot :blaze.db/kv-snapshot :instant inst?)
  :ret (s/nilable :blaze.db/t))


(s/fdef index/compartment-list
  :args (s/cat :cri :blaze.db/kv-iterator
               :raoi :blaze.db/kv-iterator
               :compartment :blaze.db/compartment
               :tid :blaze.db/tid
               :start-id (s/nilable bytes?)
               :t :blaze.db/t)
  :ret (s/coll-of :blaze.db/resource-handle))


(s/def :blaze.db.index.query/clause
  (s/tuple :blaze.db/search-param (s/nilable :blaze.db.search-param/modifier) (s/coll-of some?)))


;; it's a bit faster to have the clauses as seq instead of an vector
(s/def :blaze.db.index.query/clauses
  (s/coll-of :blaze.db.index.query/clause :kind seq? :min-count 1))


(s/fdef index/type-query
  :args (s/cat :snapshot :blaze.db/kv-snapshot
               :svri :blaze.db/kv-iterator
               :rsvi  :blaze.db/kv-iterator
               :raoi :blaze.db/kv-iterator
               :tid :blaze.db/tid
               :clauses :blaze.db.index.query/clauses
               :t :blaze.db/t)
  :ret (s/coll-of :blaze.db/resource-handle))


(s/fdef index/system-query
  :args (s/cat :node :blaze.db/node
               :snapshot :blaze.db/kv-snapshot
               :svri :blaze.db/kv-iterator
               :rsvi  :blaze.db/kv-iterator
               :raoi :blaze.db/kv-iterator
               :clauses :blaze.db.index.query/clauses
               :t :blaze.db/t)
  :ret (s/coll-of :blaze.db/resource-handle))


(s/fdef index/compartment-query
  :args (s/cat :snapshot :blaze.db/kv-snapshot
               :csvri :blaze.db/kv-iterator
               :raoi :blaze.db/kv-iterator
               :compartment :blaze.db/compartment
               :tid :blaze.db/tid
               :clauses :blaze.db.index.query/clauses
               :t :blaze.db/t)
  :ret (s/coll-of :blaze.db/resource-handle))
