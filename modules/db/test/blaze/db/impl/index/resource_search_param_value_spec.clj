(ns blaze.db.impl.index.resource-search-param-value-spec
  (:require
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

(s/fdef r-sp-v/value-prefix-exists?
  :args (s/cat :snapshot :blaze.db.kv/snapshot
               :resource-handle :blaze.db/resource-handle
               :c-hash :blaze.db/c-hash
               :value-prefix byte-string?)
  :ret boolean?)

(s/fdef r-sp-v/next-value-prev
  :args (s/cat :snapshot :blaze.db.kv/snapshot
               :resource-handle :blaze.db/resource-handle
               :c-hash :blaze.db/c-hash
               :value-prefix-length nat-int?
               :value byte-string?)
  :ret (s/nilable byte-string?))

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
