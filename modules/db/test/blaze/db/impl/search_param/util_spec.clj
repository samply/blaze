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
   [clojure.spec.alpha :as s]))

(s/fdef u/separate-op
  :args (s/cat :value string?)
  :ret (s/tuple keyword? string?))

(s/fdef u/reference-resource-handle-mapper
  :args (s/cat :batch-db :blaze.db.impl/batch-db))

(s/fdef u/eq-value
  :args (s/cat :f ifn? :decimal-value decimal?)
  :ret map?)

(s/fdef u/soundex
  :args (s/cat :s string?)
  :ret (s/nilable string?))

(s/fdef u/canonical-parts
  :args (s/cat :canonical string?)
  :ret (s/tuple string? (s/coll-of string?)))
