(ns blaze.db.impl.index.search-param-value-resource-spec
  (:require
    [blaze.byte-string :refer [byte-string?]]
    [blaze.byte-string-spec]
    [blaze.coll.core-spec]
    [blaze.db.impl.codec-spec]
    [blaze.db.impl.index.search-param-value-resource :as sp-vr]
    [blaze.db.impl.iterators-spec]
    [blaze.db.kv.spec]
    [blaze.fhir.hash.spec]
    [clojure.spec.alpha :as s]))


(s/fdef sp-vr/keys!
  :args (s/cat :iter :blaze.db/kv-iterator :start-key byte-string?))


(s/fdef sp-vr/prefix-keys!
  :args (s/cat :iter :blaze.db/kv-iterator
               :c-hash :blaze.db/c-hash
               :tid :blaze.db/tid
               :prefix-value byte-string?
               :start-value byte-string?
               :start-did (s/? :blaze.db/did)))


(s/fdef sp-vr/encode-seek-key
  :args (s/cat :c-hash :blaze.db/c-hash
               :tid :blaze.db/tid
               :value (s/? byte-string?)
               :did (s/? :blaze.db/did))
  :ret byte-string?)


(s/fdef sp-vr/encode-seek-key-for-prev
  :args (s/cat :c-hash :blaze.db/c-hash
               :tid :blaze.db/tid
               :value byte-string?
               :did (s/? :blaze.db/did))
  :ret byte-string?)


(s/fdef sp-vr/encode-key
  :args (s/cat :c-hash :blaze.db/c-hash
               :tid :blaze.db/tid
               :value byte-string?
               :did :blaze.db/did
               :hash :blaze.resource/hash)
  :ret bytes?)


(s/fdef sp-vr/index-entry
  :args (s/cat :c-hash :blaze.db/c-hash
               :tid :blaze.db/tid
               :value byte-string?
               :did :blaze.db/did
               :hash :blaze.resource/hash)
  :ret :blaze.db.kv/put-entry)
