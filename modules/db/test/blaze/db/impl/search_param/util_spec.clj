(ns blaze.db.impl.search-param.util-spec
  (:require
    [blaze.byte-string-spec]
    [blaze.db.impl.batch-db.spec]
    [blaze.db.impl.codec.spec]
    [blaze.db.impl.index.resource-search-param-value-spec]
    [blaze.db.impl.iterators-spec]
    [blaze.db.impl.search-param.util :as u]
    [blaze.db.kv.spec]
    [blaze.db.spec]
    [blaze.fhir.spec.spec]
    [clojure.spec.alpha :as s]))


(s/fdef u/separate-op
  :args (s/cat :value string?)
  :ret (s/tuple keyword? string?))


(s/fdef u/non-deleted-resource-handle
  :args (s/cat :resource-handle fn? :tid :blaze.db/tid :did :blaze.db/did)
  :ret (s/nilable :blaze.db/resource-handle))


(s/fdef u/resource-handle-mapper
  :args (s/cat :context :blaze.db.impl.batch-db/context :tid :blaze.db/tid))


(s/fdef u/eq-value
  :args (s/cat :f ifn? :decimal-value decimal?)
  :ret map?)
