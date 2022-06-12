(ns blaze.db.impl.index.compartment.search-param-value-resource-spec
  (:require
    [blaze.byte-string :refer [byte-string?]]
    [blaze.byte-string-spec]
    [blaze.coll.core-spec]
    [blaze.db.impl.codec-spec]
    [blaze.db.impl.index.compartment.search-param-value-resource :as c-sp-vr]
    [blaze.db.impl.iterators-spec]
    [blaze.db.impl.search-param.spec]
    [blaze.db.kv.spec]
    [blaze.fhir.hash.spec]
    [clojure.spec.alpha :as s]))


(s/fdef c-sp-vr/keys!
  :args (s/cat :iter :blaze.db/kv-iterator :start-key byte-string?))


(s/fdef c-sp-vr/prefix-keys!
  :args (s/cat :iter :blaze.db/kv-iterator
               :compartment :blaze.db/compartment
               :c-hash :blaze.db/c-hash
               :tid :blaze.db/tid
               :value byte-string?))


(s/fdef c-sp-vr/encode-seek-key
  :args (s/cat :compartment :blaze.db/compartment
               :c-hash :blaze.db/c-hash
               :tid :blaze.db/tid
               :value byte-string?)
  :ret byte-string?)


(s/fdef c-sp-vr/index-entry
  :args (s/cat :compartment :blaze.db/compartment
               :c-hash :blaze.db/c-hash
               :tid :blaze.db/tid
               :value byte-string?
               :did :blaze.db/did
               :hash :blaze.resource/hash)
  :ret :blaze.db.kv/put-entry)
