(ns blaze.db.impl.search-param.quantity-spec
  (:require
   [blaze.db.impl.db.spec]
   [blaze.db.impl.index.compartment.search-param-value-resource-spec]
   [blaze.db.impl.index.resource-search-param-value-spec]
   [blaze.db.impl.index.search-param-value-resource-spec]
   [blaze.db.impl.search-param.quantity :as spq]
   [blaze.db.impl.search-param.quantity.spec]
   [blaze.db.impl.search-param.util-spec]
   [blaze.db.kv.spec]
   [blaze.db.spec]
   [blaze.fhir.spec.type.system-spec]
   [clojure.spec.alpha :as s]))

(s/fdef spq/index-handles
  :args (s/cat :db :blaze.db.impl/db
               :c-hash :blaze.db/c-hash
               :tid :blaze.db/tid
               :prefix-length nat-int?
               :value ::spq/value
               :start-id (s/? :blaze.resource/id)))

(s/fdef spq/matcher
  :args (s/cat :db :blaze.db.impl/db
               :c-hash :blaze.db/c-hash
               :prefix-length nat-int?
               :values (s/coll-of ::spq/value :min-count 1)))
