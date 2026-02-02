(ns blaze.db.impl.batch-db-spec
  (:require
   [blaze.byte-string-spec]
   [blaze.db.impl.batch-db :as batch-db]
   [blaze.db.impl.batch-db.patient-everything-spec]
   [blaze.db.impl.codec.spec]
   [blaze.db.impl.index-spec]
   [blaze.db.impl.index.compartment.resource-spec]
   [blaze.db.impl.index.patient-last-change-spec]
   [blaze.db.impl.index.resource-as-of-spec]
   [blaze.db.impl.index.system-as-of-spec]
   [blaze.db.impl.index.type-as-of-spec]
   [blaze.db.impl.search-param.chained-spec]
   [blaze.db.index.query :as-alias query]
   [clojure.spec.alpha :as s]))

(s/fdef batch-db/->TypeQuery
  :args (s/cat :tid :blaze.db/tid :clauses ::query/clauses))

(s/fdef batch-db/patient-type-query
  :args (s/cat :tid :blaze.db/tid
               :patient-ids (s/coll-of :blaze.db/id-byte-string :kind vector? :min-count 1)
               :compartment-clause ::query/search-clause
               :scan-clauses ::query/search-clauses
               :other-clauses (s/nilable (s/coll-of ::query/disjunction :kind vector?))))

(s/fdef batch-db/->EmptyTypeQuery
  :args (s/cat :tid :blaze.db/tid))

(s/fdef batch-db/->CompartmentQuery
  :args (s/cat :c-hash :blaze.db/c-hash :tid :blaze.db/tid
               :search-clauses ::query/search-clauses))

(s/fdef batch-db/->EmptyCompartmentQuery
  :args (s/cat :c-hash :blaze.db/c-hash :tid :blaze.db/tid))

(s/fdef batch-db/->Matcher
  :args (s/cat :search-clauses ::query/search-clauses))

(s/fdef batch-db/new-batch-db
  :args (s/cat :node :blaze.db/node :basis-t :blaze.db/t :t :blaze.db/t
               :since-t :blaze.db/t))
