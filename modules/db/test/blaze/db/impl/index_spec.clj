(ns blaze.db.impl.index-spec
  (:require
    [blaze.async.comp :as ac]
    [blaze.byte-string-spec]
    [blaze.coll.spec :as cs]
    [blaze.db.impl.batch-db.spec]
    [blaze.db.impl.codec-spec]
    [blaze.db.impl.index :as index]
    [blaze.db.impl.iterators-spec]
    [blaze.db.impl.search-param-spec]
    [blaze.db.impl.search-param.spec]
    [blaze.db.kv-spec]
    [clojure.spec.alpha :as s]))


(s/def :blaze.db.index.query/clause
  (s/tuple :blaze.db/search-param
           (s/nilable :blaze.db.search-param/modifier)
           (s/coll-of string?)
           (s/coll-of some?)))


(s/def :blaze.db.index.query/clauses
  (s/coll-of :blaze.db.index.query/clause :min-count 1))


(s/fdef index/type-query
  :args (s/cat :context :blaze.db.impl.batch-db/context
               :tid :blaze.db/tid
               :clauses :blaze.db.index.query/clauses
               :start-id (s/? :blaze.db/id-byte-string))
  :ret (cs/coll-of :blaze.db/resource-handle))


(s/fdef index/type-query-total
  :args (s/cat :context :blaze.db.impl.batch-db/context
               :tid :blaze.db/tid
               :clauses :blaze.db.index.query/clauses)
  :ret ac/completable-future?)


(s/fdef index/system-query
  :args (s/cat :context :blaze.db.impl.batch-db/context
               :clauses :blaze.db.index.query/clauses)
  :ret (cs/coll-of :blaze.db/resource-handle))


(s/fdef index/compartment-query
  :args (s/cat :context :blaze.db.impl.batch-db/context
               :compartment :blaze.db/compartment
               :tid :blaze.db/tid
               :clauses :blaze.db.index.query/clauses)
  :ret (cs/coll-of :blaze.db/resource-handle))


(s/fdef index/targets
  :args (s/cat :context :blaze.db.impl.batch-db/context
               :resource-handle :blaze.db/resource-handle
               :code :blaze.db/c-hash
               :target-tid (s/? :blaze.db/tid))
  :ret (cs/coll-of :blaze.db/resource-handle))
