(ns blaze.db.impl.index.patient-type-search-param-token-full-resource-spec
  (:require
   [blaze.byte-buffer-spec]
   [blaze.byte-string :refer [byte-string?]]
   [blaze.db.impl.codec.spec]
   [blaze.db.impl.index.patient-type-search-param-token-full-resource :as pt-sp-tfr]
   [blaze.db.kv.spec]
   [blaze.fhir.hash.spec]
   [blaze.fhir.spec.spec]
   [clojure.spec.alpha :as s]))

(s/fdef pt-sp-tfr/prefix-keys
  :args (s/cat :snapshot :blaze.db.kv/snapshot
               :patient-id :blaze.db/id-byte-string
               :tb :blaze.db/tb
               :search-param-code-id :blaze.db/search-param-code-id
               :value byte-string?
               :system-id :blaze.db/system-id))

(s/fdef pt-sp-tfr/index-entry
  :args (s/cat :patient-id :blaze.db/id-byte-string
               :tb :blaze.db/tb
               :search-param-code-id :blaze.db/search-param-code-id
               :value byte-string?
               :system-id :blaze.db/system-id
               :id :blaze.db/id-byte-string
               :hash :blaze.resource/hash)
  :ret :blaze.db.kv/put-entry)
