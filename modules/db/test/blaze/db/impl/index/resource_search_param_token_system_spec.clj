(ns blaze.db.impl.index.resource-search-param-token-system-spec
  (:require
   [blaze.byte-buffer-spec]
   [blaze.byte-string-spec]
   [blaze.db.impl.codec.spec]
   [blaze.db.impl.index.resource-search-param-token-system :as r-sp-ts]
   [blaze.db.kv.spec]
   [blaze.fhir.hash-spec]
   [clojure.spec.alpha :as s]))

(s/fdef r-sp-ts/value-filter
  :args (s/cat :snapshot :blaze.db.kv/snapshot
               :type-byte-index map?
               :search-param-code-id :blaze.db/search-param-code-id
               :values (s/coll-of (s/keys :req-un [:blaze.db/system-id]) :min-count 1))
  :ret fn?)

(s/fdef r-sp-ts/index-entry
  :args (s/cat :tb :blaze.db/tb
               :id :blaze.db/id-byte-string
               :hash :blaze.resource/hash
               :search-param-code-id :blaze.db/search-param-code-id
               :system-id :blaze.db/system-id)
  :ret :blaze.db.kv/put-entry)
