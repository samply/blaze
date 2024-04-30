(ns blaze.db.impl.index.type-search-param-reference-url-resource-spec
  (:require
   [blaze.byte-buffer-spec]
   [blaze.byte-string :refer [byte-string?]]
   [blaze.db.impl.codec.spec]
   [blaze.db.impl.index.type-search-param-reference-url-resource :as t-sp-rur]
   [blaze.db.kv.spec]
   [blaze.fhir.hash.spec]
   [blaze.fhir.spec.spec]
   [clojure.spec.alpha :as s]))

(s/fdef t-sp-rur/prefix-keys
  :args (s/cat :snapshot :blaze.db.kv/snapshot
               :tb :blaze.db/tb
               :search-param-code-id :blaze.db/search-param-code-id
               :url byte-string?
               :version (s/? byte-string?)
               :start-id (s/? :blaze.db/id-byte-string)))

(s/fdef t-sp-rur/index-entry
  :args (s/cat :tb :blaze.db/tb
               :search-param-code-id :blaze.db/search-param-code-id
               :url byte-string?
               :version byte-string?
               :id :blaze.db/id-byte-string
               :hash :blaze.resource/hash)
  :ret :blaze.db.kv/put-entry)
