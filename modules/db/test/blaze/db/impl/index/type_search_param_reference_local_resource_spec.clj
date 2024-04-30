(ns blaze.db.impl.index.type-search-param-reference-local-resource-spec
  (:require
   [blaze.byte-buffer-spec]
   [blaze.db.impl.codec.spec]
   [blaze.db.impl.index.type-search-param-reference-local-resource :as t-sp-rlr]
   [blaze.db.kv.spec]
   [blaze.fhir.hash.spec]
   [blaze.fhir.spec.spec]
   [clojure.spec.alpha :as s]))

(s/fdef t-sp-rlr/prefix-keys
  :args (s/cat :snapshot :blaze.db.kv/snapshot
               :tb :blaze.db/tb
               :search-param-code-id :blaze.db/search-param-code-id
               :ref-id :blaze.db/id-byte-string
               :ref-tb (s/? :blaze.db/tb)
               :start-id (s/? :blaze.db/id-byte-string)))

(s/fdef t-sp-rlr/index-entry
  :args (s/cat :tb :blaze.db/tb
               :search-param-code-id :blaze.db/search-param-code-id
               :ref-id :blaze.db/id-byte-string
               :ref-tb :blaze.db/tb
               :id :blaze.db/id-byte-string
               :hash :blaze.resource/hash)
  :ret :blaze.db.kv/put-entry)
