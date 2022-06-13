(ns blaze.db.impl.search-param.quantity-spec
  (:require
    [blaze.db.impl.index.compartment.search-param-value-resource-spec]
    [blaze.db.impl.index.resource-search-param-value-spec]
    [blaze.db.impl.index.search-param-value-resource-spec]
    [blaze.db.impl.search-param.quantity :as spq]
    [blaze.db.impl.search-param.quantity.spec]
    [blaze.db.impl.search-param.util-spec]
    [blaze.db.spec]
    [blaze.fhir.spec.type.system-spec]
    [clojure.spec.alpha :as s]))


(s/fdef spq/resource-keys!
  :args (s/cat :context :blaze.db.impl.batch-db/context
               :c-hash :blaze.db/c-hash
               :tid :blaze.db/tid
               :prefix-length nat-int?
               :value ::spq/value
               :start-did (s/? :blaze.db/did)))


(s/fdef spq/matches?
  :args (s/cat :context :blaze.db.impl.batch-db/context
               :c-hash :blaze.db/c-hash
               :resource-handle :blaze.db/resource-handle
               :prefix-length nat-int?
               :value ::spq/value))
