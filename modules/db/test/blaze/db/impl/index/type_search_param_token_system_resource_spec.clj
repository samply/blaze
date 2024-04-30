(ns blaze.db.impl.index.type-search-param-token-system-resource-spec
  (:require
   [blaze.byte-buffer-spec]
   [blaze.db.impl.codec.spec]
   [blaze.db.impl.index.type-search-param-token-system-resource :as t-sp-tsr]
   [blaze.db.kv.spec]
   [blaze.fhir.hash.spec]
   [blaze.fhir.spec.spec]
   [clojure.spec.alpha :as s]))

(s/fdef t-sp-tsr/prefix-keys
  :args (s/cat :snapshot :blaze.db.kv/snapshot
               :tb :blaze.db/tb
               :search-param-code-id :blaze.db/search-param-code-id
               :system-id :blaze.db/system-id
               :start-id (s/? :blaze.db/id-byte-string)))

(s/fdef t-sp-tsr/index-entry
  :args (s/cat :tb :blaze.db/tb
               :search-param-code-id :blaze.db/search-param-code-id
               :system-id :blaze.db/system-id
               :id :blaze.db/id-byte-string
               :hash :blaze.resource/hash)
  :ret :blaze.db.kv/put-entry)
