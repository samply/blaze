(ns blaze.db.impl.index.resource-search-param-reference-local-spec
  (:require
   [blaze.byte-buffer-spec]
   [blaze.byte-string-spec]
   [blaze.db.impl.codec.spec]
   [blaze.db.impl.index.resource-search-param-reference-local :as r-sp-rl]
   [blaze.db.kv.spec]
   [blaze.db.spec]
   [blaze.fhir.hash-spec]
   [clojure.spec.alpha :as s]))

(s/fdef r-sp-rl/value-filter
  :args (s/cat :snapshot :blaze.db.kv/snapshot
               :type-byte-index map?
               :search-param-code-id :blaze.db/search-param-code-id
               :values (s/coll-of map? :min-count 1))
  :ret fn?)

(s/fdef r-sp-rl/index-entry
  :args (s/cat :tb :blaze.db/tb
               :id :blaze.db/id-byte-string
               :hash :blaze.resource/hash
               :search-param-code-id :blaze.db/search-param-code-id
               :ref-id :blaze.db/id-byte-string
               :ref-tb :blaze.db/tb)
  :ret :blaze.db.kv/put-entry)
