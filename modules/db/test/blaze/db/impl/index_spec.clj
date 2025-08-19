(ns blaze.db.impl.index-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.byte-string-spec]
   [blaze.coll.core-spec]
   [blaze.coll.spec :as cs]
   [blaze.db.impl.batch-db.spec]
   [blaze.db.impl.codec-spec]
   [blaze.db.impl.index :as index]
   [blaze.db.impl.iterators-spec]
   [blaze.db.impl.search-param-spec]
   [blaze.db.impl.search-param.spec]
   [blaze.db.kv-spec]
   [blaze.db.search-param-registry.spec]
   [blaze.fhir.spec.spec]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/def :blaze.db.index.query/clause
  (s/tuple :blaze.db/search-param
           (s/nilable :blaze.db.search-param/modifier)
           (s/coll-of string?)
           (s/coll-of some?)))

(s/fdef index/resolve-search-params
  :args (s/cat :registry :blaze.db/search-param-registry
               :type :fhir.resource/type
               :clauses :blaze.db.query/clauses
               :lenient? boolean?)
  :ret (s/or :search-params (s/coll-of :blaze.db.index.query/clause :kind vector?)
             :anomaly ::anom/anomaly))

(s/def :blaze.db.index.query/clauses
  (s/coll-of :blaze.db.index.query/clause :min-count 1))

(s/fdef index/other-clauses-resource-handle-filter
  :args (s/cat :batch-db :blaze.db.impl/batch-db
               :clauses :blaze.db.index.query/clauses))

(s/fdef index/type-query
  :args (s/cat :batch-db :blaze.db.impl/batch-db
               :tid :blaze.db/tid
               :clauses :blaze.db.index.query/clauses
               :start-id (s/? :blaze.db/id-byte-string))
  :ret (cs/coll-of :blaze.db/resource-handle))

(s/fdef index/type-query-plan
  :args (s/cat :batch-db :blaze.db.impl/batch-db
               :tid :blaze.db/tid
               :clauses :blaze.db.index.query/clauses)
  :ret :blaze.db.query/plan)

(s/fdef index/type-query-total
  :args (s/cat :batch-db :blaze.db.impl/batch-db
               :tid :blaze.db/tid
               :clauses :blaze.db.index.query/clauses)
  :ret ac/completable-future?)

(s/fdef index/system-query
  :args (s/cat :batch-db :blaze.db.impl/batch-db
               :clauses :blaze.db.index.query/clauses)
  :ret (cs/coll-of :blaze.db/resource-handle))

(s/fdef index/compartment-query
  :args (s/cat :batch-db :blaze.db.impl/batch-db
               :compartment :blaze.db/compartment
               :tid :blaze.db/tid
               :clauses :blaze.db.index.query/clauses
               :start-id (s/? :blaze.db/id-byte-string))
  :ret (cs/coll-of :blaze.db/resource-handle))

(s/fdef index/compartment-query-plan
  :args (s/cat :clauses :blaze.db.index.query/clauses)
  :ret :blaze.db.query/plan)
