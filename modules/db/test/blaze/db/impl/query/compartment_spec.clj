(ns blaze.db.impl.query.compartment-spec
  (:require
   [blaze.db.impl.codec.spec]
   [blaze.db.impl.index-spec]
   [blaze.db.impl.query.compartment :as qc]
   [blaze.db.index.query :as-alias query]
   [clojure.spec.alpha :as s]))

(s/fdef qc/->CompartmentListQuery
  :args (s/cat :code string? :clauses ::query/search-clauses :tid :blaze.db/tid))

(s/fdef qc/->CompartmentSeekQuery
  :args (s/cat :code string? :clauses ::query/search-clauses :tid :blaze.db/tid
               :other-clauses ::query/search-clauses))

(s/fdef qc/->CompartmentQuery
  :args (s/cat :c-hash :blaze.db/c-hash :tid :blaze.db/tid
               :scan-clauses ::query/search-clauses
               :other-clauses (s/nilable (s/coll-of ::query/disjunction :kind vector?))))
