(ns blaze.db.impl.index.resource-search-param-value-spec
  (:require
   [blaze.byte-buffer-spec]
   [blaze.byte-string :as bs :refer [byte-string?]]
   [blaze.byte-string-spec]
   [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
   [blaze.db.kv.spec]
   [blaze.fhir.hash-spec]
   [clojure.spec.alpha :as s]))

(s/fdef r-sp-v/next-value
  :args (s/cat :snapshot :blaze.db.kv/snapshot
               :resource-handle :blaze.db/resource-handle
               :c-hash :blaze.db/c-hash
               :value (s/? (s/cat :value-prefix-length nat-int?
                                  :value byte-string?)))
  :ret (s/nilable byte-string?))

(s/fdef r-sp-v/value-filter
  :args (s/cat :snapshot :blaze.db.kv/snapshot
               :seek (s/? fn?)
               :encode fn?
               :matches? fn?
               :value-prefix-length (s/? fn?)
               :values (s/coll-of some? :min-count 1))
  :ret fn?)

(s/fdef r-sp-v/value-prefix-filter
  :args (s/cat :snapshot :blaze.db.kv/snapshot
               :c-hash :blaze.db/c-hash
               :value-prefixes (s/coll-of byte-string? :min-count 1))
  :ret fn?)

(s/fdef r-sp-v/index-entry
  :args (s/cat :tid :blaze.db/tid
               :id :blaze.db/id-byte-string
               :hash :blaze.resource/hash
               :c-hash :blaze.db/c-hash
               :value byte-string?)
  :ret :blaze.db.kv/put-entry)

(s/fdef r-sp-v/prefix-keys
  :args (s/and (s/cat :snapshot :blaze.db.kv/snapshot
                      :tid :blaze.db/tid
                      :id :blaze.db/id-byte-string
                      :hash :blaze.resource/hash
                      :c-hash :blaze.db/c-hash
                      :start-value (s/? (s/cat :prefix-length nat-int?
                                               :value byte-string?)))
               (fn [{{:keys [prefix-length value] :as start-value} :start-value}]
                 (or (nil? start-value) (<= prefix-length (bs/size value))))))
