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
   [blaze.db.index.query :as-alias query]
   [blaze.db.kv-spec]
   [blaze.db.search-param-registry.spec]
   [blaze.fhir.spec.spec]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/def ::query/search-clause
  (s/tuple :blaze.db/search-param
           (s/nilable :blaze.db.search-param/modifier)
           (s/coll-of string?)
           (s/coll-of some?)))

(s/def ::query/disjunction
  (s/coll-of ::query/search-clause :kind vector? :min-count 1))

(s/def ::query/search-clauses
  (s/coll-of ::query/disjunction :kind vector? :min-count 1))

(s/def ::query/sort-clause
  ::query/search-clause)

(s/def ::query/clauses
  (s/keys :opt-un [::query/sort-clause ::query/search-clauses]))

(s/fdef index/resolve-search-params
  :args (s/cat :registry :blaze.db/search-param-registry
               :type :fhir.resource/type
               :clauses :blaze.db.query/clauses
               :lenient? boolean?)
  :ret (s/or :clauses ::query/clauses :anomaly ::anom/anomaly))

(s/fdef index/other-clauses-resource-handle-filter
  :args (s/cat :batch-db :blaze.db.impl/batch-db
               :conjunction ::query/search-clauses))

(s/fdef index/type-query
  :args (s/cat :batch-db :blaze.db.impl/batch-db
               :tid :blaze.db/tid
               :clauses ::query/clauses
               :start-id (s/? :blaze.db/id-byte-string))
  :ret (cs/coll-of :blaze.db/resource-handle))

(s/fdef index/type-query-plan
  :args (s/cat :batch-db :blaze.db.impl/batch-db
               :tid :blaze.db/tid
               :clauses ::query/clauses)
  :ret :blaze.db.query/plan)

(s/fdef index/type-query-total
  :args (s/cat :batch-db :blaze.db.impl/batch-db
               :tid :blaze.db/tid
               :scan-clauses (s/nilable ::query/search-clauses))
  :ret ac/completable-future?)

(s/fdef index/system-query
  :args (s/cat :batch-db :blaze.db.impl/batch-db
               :clauses ::query/clauses)
  :ret (cs/coll-of :blaze.db/resource-handle))

(s/fdef index/compartment-query
  :args (s/cat :batch-db :blaze.db.impl/batch-db
               :compartment :blaze.db/compartment
               :tid :blaze.db/tid
               :search-clauses ::query/search-clauses)
  :ret (cs/coll-of :blaze.db/resource-handle))

(s/fdef index/compartment-query*
  :args (s/cat :batch-db :blaze.db.impl/batch-db
               :compartment :blaze.db/compartment
               :tid :blaze.db/tid
               :scan-clauses ::query/search-clauses
               :other-clauses (s/nilable (s/coll-of ::query/disjunction :kind vector?))
               :start-id (s/? :blaze.db/id-byte-string))
  :ret (cs/coll-of :blaze.db/resource-handle))

(s/fdef index/compartment-query-plan
  :args (s/cat :search-clauses ::query/search-clauses)
  :ret :blaze.db.query/plan)
