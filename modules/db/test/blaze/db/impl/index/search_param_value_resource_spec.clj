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
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef sp-vr/keys
  :args (s/cat :snapshot :blaze.db.kv/snapshot :start-key byte-string?))

(s/fdef sp-vr/by-id-grouper
  :args (s/cat :rf ifn?)
  :ret ifn?)

(s/fdef sp-vr/index-handles
  :args (s/and (s/cat :snapshot :blaze.db.kv/snapshot
                      :c-hash :blaze.db/c-hash
                      :tid :blaze.db/tid
                      :prefix-length nat-int?
                      :start-value byte-string?
                      :start-id (s/? :blaze.resource/id))
               (fn [{:keys [prefix-length start-value]}]
                 (<= prefix-length (bs/size start-value)))))

(s/fdef sp-vr/encode-seek-key
  :args (s/cat :c-hash :blaze.db/c-hash
               :tid :blaze.db/tid
               :more (s/? (s/cat :value byte-string?
                                 :id (s/? :blaze.resource/id))))
  :ret byte-string?)

(s/fdef sp-vr/index-entry
  :args (s/cat :c-hash :blaze.db/c-hash
               :tid :blaze.db/tid
               :value byte-string?
               :id :blaze.resource/id
               :hash :blaze.resource/hash)
  :ret :blaze.db.kv/put-entry)

(s/fdef sp-vr/estimated-scan-size
  :args (s/cat :kv-store :blaze.db/kv-store
               :c-hash :blaze.db/c-hash
               :tid :blaze.db/tid
               :value byte-string?)
  :ret (s/or :estimate-storage-size nat-int? :anomaly ::anom/anomaly))
