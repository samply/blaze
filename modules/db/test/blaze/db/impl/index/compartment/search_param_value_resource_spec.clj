(ns blaze.db.impl.index.compartment.search-param-value-resource-spec
  (:require
   [blaze.byte-buffer-spec]
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

(s/fdef c-sp-vr/index-handles
  :args (s/cat :snapshot :blaze.db.kv/snapshot
               :compartment :blaze.db/compartment
               :c-hash :blaze.db/c-hash
               :tid :blaze.db/tid
               :value byte-string?
               :start-id (s/? :blaze.db/id-byte-string)))

(s/fdef c-sp-vr/index-entry
  :args (s/cat :compartment :blaze.db/compartment
               :c-hash :blaze.db/c-hash
               :tid :blaze.db/tid
               :value byte-string?
               :id :blaze.db/id-byte-string
               :hash :blaze.resource/hash)
  :ret :blaze.db.kv/put-entry)
