(ns blaze.db.impl.index.search-param-value-resource-spec
  (:require
   [blaze.byte-buffer-spec]
   [blaze.byte-string :as bs :refer [byte-string?]]
   [blaze.byte-string-spec]
   [blaze.coll.core-spec]
   [blaze.db.impl.codec-spec]
   [blaze.db.impl.index.search-param-value-resource :as sp-vr]
   [blaze.db.impl.iterators-spec]
   [blaze.db.kv.spec]
   [blaze.fhir.hash.spec]
   [clojure.spec.alpha :as s]))

(s/fdef sp-vr/keys
  :args (s/cat :snapshot :blaze.db.kv/snapshot :start-key byte-string?))

(s/fdef sp-vr/index-handles
  :args (s/and (s/cat :snapshot :blaze.db.kv/snapshot
                      :c-hash :blaze.db/c-hash
                      :tid :blaze.db/tid
                      :prefix-length nat-int?
                      :start-value byte-string?
                      :start-id (s/? :blaze.db/id-byte-string))
               (fn [{:keys [prefix-length start-value]}]
                 (<= prefix-length (bs/size start-value)))))

(s/fdef sp-vr/encode-seek-key
  :args (s/cat :c-hash :blaze.db/c-hash
               :tid :blaze.db/tid
               :more (s/? (s/cat :value byte-string?
                                 :id (s/? :blaze.db/id-byte-string))))
  :ret byte-string?)

(s/fdef sp-vr/index-entry
  :args (s/cat :c-hash :blaze.db/c-hash
               :tid :blaze.db/tid
               :value byte-string?
               :id :blaze.db/id-byte-string
               :hash :blaze.resource/hash)
  :ret :blaze.db.kv/put-entry)
