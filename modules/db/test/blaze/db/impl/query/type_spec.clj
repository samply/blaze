(ns blaze.db.impl.query.type-spec
  (:require
   [blaze.db.impl.codec.spec]
   [blaze.db.impl.index-spec]
   [blaze.db.impl.query.type :as qt]
   [blaze.db.index.query :as-alias query]
   [clojure.spec.alpha :as s]))

(s/fdef qt/->TypeQuery
  :args (s/cat :tid :blaze.db/tid :clauses ::query/clauses))

(s/fdef qt/patient-type-query
  :args (s/cat :tid :blaze.db/tid
               :patient-ids (s/coll-of :blaze.db/id-byte-string :kind vector? :min-count 1)
               :compartment-clause ::query/search-clause
               :scan-clauses ::query/search-clauses
               :other-clauses (s/nilable (s/coll-of ::query/disjunction :kind vector?))))

(s/fdef qt/->EmptyTypeQuery
  :args (s/cat :tid :blaze.db/tid))
