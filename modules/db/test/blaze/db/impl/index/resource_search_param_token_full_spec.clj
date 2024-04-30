(ns blaze.db.impl.index.resource-search-param-token-full-spec
  (:require
   [blaze.byte-buffer-spec]
   [blaze.byte-string :refer [byte-string?]]
   [blaze.byte-string-spec]
   [blaze.db.impl.codec.spec]
   [blaze.db.impl.index.resource-search-param-token-full :as r-sp-tf]
   [blaze.db.kv.spec]
   [blaze.db.spec]
   [blaze.fhir.hash-spec]
   [clojure.spec.alpha :as s]))

(s/fdef r-sp-tf/system-id
  :args (s/cat :snapshot :blaze.db.kv/snapshot
               :type-byte-index map?
               :resource-handle :blaze.db/resource-handle
               :search-param-code-id :blaze.db/search-param-code-id
               :value byte-string?)
  :ret (s/nilable :blaze.db/system-id))

(s/fdef r-sp-tf/value-filter
  :args (s/cat :snapshot :blaze.db.kv/snapshot
               :type-byte-index map?
               :search-param-code-id :blaze.db/search-param-code-id
               :values (s/coll-of map? :min-count 1))
  :ret fn?)

(s/fdef r-sp-tf/index-entry
  :args (s/cat :tb :blaze.db/tb
               :id :blaze.db/id-byte-string
               :hash :blaze.resource/hash
               :search-param-code-id :blaze.db/search-param-code-id
               :value byte-string?
               :system-id :blaze.db/system-id)
  :ret :blaze.db.kv/put-entry)
