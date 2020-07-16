(ns blaze.db.spec
  (:require
    [blaze.db.impl.protocols :as p]
    [blaze.db.indexer :as indexer]
    [blaze.db.tx-log :as tx-log]
    [clojure.spec.alpha :as s]))


(s/def :blaze.db/node
  #(satisfies? p/Node %))


(s/def :blaze.db/resource-cache
  #(satisfies? p/ResourceContentLookup %))


(s/def :blaze.db.indexer/resource-indexer
  #(satisfies? indexer/Resource %))


(s/def :blaze.db.indexer/tx-indexer
  #(satisfies? indexer/Tx %))


(s/def :blaze.db/tx-log
  #(satisfies? tx-log/TxLog %))


(s/def :blaze.db/t
  nat-int?)


(s/def :blaze.db/op
  #{:create :put :delete})


(s/def :blaze.db/num-changes
  nat-int?)


(defmulti tx-op "Transaction operator" first)


(defmethod tx-op :create
  [_]
  (s/cat :op #{:create} :resource :blaze/resource))


(defmethod tx-op :put
  [_]
  (s/cat :op #{:put}
         :resource :blaze/resource
         :matches (s/? :blaze.db/t)))


(defmethod tx-op :delete [_]
  (s/cat :op #{:delete}
         :type :blaze.resource/resourceType
         :id :blaze.resource/id))


(s/def :blaze.db/tx-op
  (s/multi-spec tx-op first))


(s/def :blaze.db/tx-ops
  (s/coll-of :blaze.db/tx-op :kind vector?))


(s/def :blaze.db/db
  #(satisfies? p/Db %))


(s/def :blaze.db.tx/instant
  inst?)


(s/def :blaze.db/tx
  (s/keys :req [:blaze.db/t :blaze.db.tx/instant]))


(s/def :blaze.db/query
  some?)


(s/def :blaze.db.query/clause
  (s/coll-of string? :min-count 2))


(s/def :blaze.db/local-ref
  (s/tuple :blaze.resource/resourceType :blaze.resource/id))
