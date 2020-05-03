(ns blaze.db.impl.index-spec
  (:require
    [blaze.db.impl.codec-spec]
    [blaze.db.impl.index :as index]
    [blaze.db.impl.search-param-spec]
    [clojure.spec.alpha :as s])
  (:import
    [blaze.db.impl.index Hash]
    [com.github.benmanes.caffeine.cache LoadingCache]))


(s/def :blaze.db/hash
  #(instance? Hash %))


(s/def :blaze.db/resource-cache
  #(instance? LoadingCache %))


(s/def :blaze.db.index/context
  (s/keys :req [:blaze.db/kv-store :blaze.db/resource-cache]))


(s/fdef index/tx-success-entries
  :args (s/cat :t :blaze.db/t :tx-instant inst?))


(s/fdef index/tx
  :args (s/cat :kv-store :blaze.db/kv-store :t :blaze.db/t)
  :ret (s/nilable :blaze.db/tx))


(s/fdef index/load-resource-content
  :args (s/cat :kv-store :blaze.db/kv-store :hash :blaze.db/hash))


(s/fdef index/state-t
  :args (s/cat :resource-as-of-iter :blaze.db/kv-iterator
               :tid :blaze.db/tid
               :id :blaze.db/id-bytes
               :t :blaze.db/t)
  :ret (s/nilable (s/tuple :blaze.db/state :blaze.db/t)))


(s/fdef index/resource
  :args (s/cat :context :blaze.db.index/context
               :tid :blaze.db/tid
               :id :blaze.db/id-bytes
               :t :blaze.db/t)
  :ret (s/nilable :blaze/resource))


(s/fdef index/num-of-instance-changes
  :args (s/cat :context :blaze.db.index/context :tid :blaze.db/tid
               :id :blaze.db/id-bytes
               :start-t :blaze.db/t
               :since-t :blaze.db/t)
  :ret nat-int?)


(s/fdef index/type-stats
  :args (s/cat :iter :blaze.db/kv-iterator :tid :blaze.db/tid :t :blaze.db/t)
  :ret (s/nilable bytes?))


(s/fdef index/num-of-type-changes
  :args (s/cat :context :blaze.db.index/context
               :tid :blaze.db/tid
               :start-t :blaze.db/t
               :since-t :blaze.db/t)
  :ret nat-int?)


(s/fdef index/num-of-system-changes
  :args (s/cat :context :blaze.db.index/context
               :start-t :blaze.db/t
               :since-t :blaze.db/t)
  :ret nat-int?)


(s/fdef index/type-list
  :args (s/cat :context :blaze.db.index/context
               :tid :blaze.db/tid
               :start-id (s/nilable bytes?)
               :t :blaze.db/t)
  :ret (s/coll-of :blaze/resource))


(s/fdef index/compartment-list
  :args (s/cat :context :blaze.db.index/context
               :compartment :blaze.db/compartment
               :tid :blaze.db/tid
               :start-id (s/nilable bytes?)
               :t :blaze.db/t)
  :ret (s/coll-of :blaze/resource))


(s/fdef index/instance-history
  :args (s/cat :context :blaze.db.index/context
               :tid :blaze.db/tid
               :id :blaze.db/id-bytes
               :start-t :blaze.db/t
               :since-t :blaze.db/t)
  :ret (s/coll-of :blaze/resource))


(s/fdef index/type-history
  :args (s/cat :context :blaze.db.index/context
               :tid :blaze.db/tid
               :start-t :blaze.db/t
               :start-id (s/nilable bytes?)
               :since-t :blaze.db/t)
  :ret (s/coll-of :blaze/resource))


(s/fdef index/system-history
  :args (s/cat :context :blaze.db.index/context
               :start-t :blaze.db/t
               :start-tid (s/nilable :blaze.db/tid)
               :start-id (s/nilable bytes?)
               :since-t :blaze.db/t)
  :ret (s/coll-of :blaze/resource))


(s/def :blaze.db.index.query/clause
  (s/tuple :blaze.db/search-param string?))


(s/fdef index/type-query
  :args (s/cat :context :blaze.db.index/context
               :tid :blaze.db/tid
               :clauses (s/coll-of :blaze.db.index.query/clause :min-count 1)
               :t :blaze.db/t)
  :ret (s/coll-of :blaze/resource))


(s/fdef index/compartment-query
  :args (s/cat :context :blaze.db.index/context
               :compartment :blaze.db/compartment
               :tid :blaze.db/tid
               :clauses (s/coll-of :blaze.db.index.query/clause :min-count 1)
               :t :blaze.db/t)
  :ret (s/coll-of :blaze/resource))


(s/fdef index/type-total
  :args (s/cat :context :blaze.db.index/context
               :tid :blaze.db/tid
               :t :blaze.db/t)
  :ret nat-int?)
