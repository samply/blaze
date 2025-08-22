(ns blaze.db.impl.batch-db-spec
  (:require
   [blaze.byte-string-spec]
   [blaze.db.impl.batch-db :as batch-db]
   [blaze.db.impl.batch-db.patient-everything-spec]
   [blaze.db.impl.index.compartment.resource-spec]
   [blaze.db.impl.index.patient-last-change-spec]
   [blaze.db.impl.index.resource-as-of-spec]
   [blaze.db.impl.index.system-as-of-spec]
   [blaze.db.impl.index.type-as-of-spec]
   [blaze.db.impl.search-param.chained-spec]
   [clojure.spec.alpha :as s]))

(s/fdef batch-db/new-batch-db
  :args (s/cat :node :blaze.db/node :basis-t :blaze.db/t :t :blaze.db/t
               :since-t :blaze.db/t))
