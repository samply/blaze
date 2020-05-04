(ns blaze.db.indexer-spec
  (:require
    [blaze.fhir.spec]
    [blaze.db.impl.codec :as codec]
    [blaze.db.indexer :as indexer]
    [clojure.spec.alpha :as s]
    [manifold.deferred :refer [deferred?]]))


(s/def ::indexer/resource
  #(satisfies? indexer/Resource %))


(s/def :blaze.resource/hash
  (s/and bytes? #(= codec/hash-size (alength %))))


(s/fdef indexer/index-resources
  :args (s/cat :indexer ::indexer/resource
               :hash-and-resources (s/coll-of (s/tuple :blaze.resource/hash :blaze/resource)))
  :ret deferred?)


(s/def ::indexer/tx
  #(satisfies? indexer/Tx %))


(defmulti tx-cmd first)


(defmethod tx-cmd :create
  [_]
  (s/cat :cmd #{:create}
         :type :blaze.resource/resourceType
         :id :blaze.resource/id
         :hash :blaze.resource/hash))


(defmethod tx-cmd :put
  [_]
  (s/cat :cmd #{:put}
         :type :blaze.resource/resourceType
         :id :blaze.resource/id
         :hash :blaze.resource/hash
         :matches (s/? :blaze.db/t)))


(defmethod tx-cmd :delete [_]
  (s/cat :cmd #{:delete}
         :type :blaze.resource/resourceType
         :id :blaze.resource/id
         :hash :blaze.resource/hash
         :matches (s/? :blaze.db/t)))


(s/def :blaze.db/tx-cmd
  (s/multi-spec tx-cmd first))


(s/def :blaze.db/tx-cmds
  (s/coll-of :blaze.db/tx-cmd :kind vector?))


(s/fdef indexer/last-t
  :args (s/cat :indexer ::indexer/tx)
  :ret :blaze.db/t)


(s/fdef indexer/index-tx
  :args (s/cat :indexer ::indexer/tx
               :t :blaze.db/t
               :tx-instant :blaze.db.tx/instant
               :tx-cmds :blaze.db/tx-cmds))
