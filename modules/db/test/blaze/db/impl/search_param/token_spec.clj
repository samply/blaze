(ns blaze.db.impl.search-param.token-spec
  (:require
    [blaze.byte-string :refer [byte-string?]]
    [blaze.byte-string-spec]
    [blaze.db.impl.index.compartment.search-param-value-resource-spec]
    [blaze.db.impl.index.search-param-value-resource-spec]
    [blaze.db.impl.search-param.token :as spt]
    [clojure.spec.alpha :as s]))


(s/fdef spt/resource-keys!
  :args (s/cat :context :blaze.db.impl.batch-db/context
               :c-hash :blaze.db/c-hash
               :tid :blaze.db/tid
               :value byte-string?
               :start-id (s/? :blaze.db/id-byte-string)))
