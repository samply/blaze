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
   [blaze.db.impl.query.compartment-spec]
   [blaze.db.impl.query.type-spec]
   [blaze.db.impl.search-param.chained-spec]
   [blaze.db.index.query :as-alias query]
   [clojure.spec.alpha :as s]))

(s/fdef batch-db/->Matcher
  :args (s/cat :search-clauses ::query/search-clauses))

(s/fdef batch-db/new-batch-db
  :args (s/cat :node :blaze.db/node :basis-t :blaze.db/t :t :blaze.db/t
               :since-t :blaze.db/t))
