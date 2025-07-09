(ns blaze.db.impl.search-param.token-spec
  (:require
   [blaze.byte-string :refer [byte-string?]]
   [blaze.byte-string-spec]
   [blaze.db.impl.batch-db.spec]
   [blaze.db.impl.codec.spec]
   [blaze.db.impl.index.compartment.search-param-value-resource-spec]
   [blaze.db.impl.index.search-param-value-resource-spec]
   [blaze.db.impl.search-param.token :as spt]
   [blaze.db.kv.spec]
   [blaze.db.spec]
   [clojure.spec.alpha :as s]))

(s/fdef spt/index-handles
  :args (s/cat :batch-db :blaze.db.impl/batch-db
               :c-hash :blaze.db/c-hash
               :tid :blaze.db/tid
               :value byte-string?
               :start-id (s/? :blaze.db/id-byte-string)))
