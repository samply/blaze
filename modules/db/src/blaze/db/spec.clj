(ns blaze.db.spec
  (:require
    [blaze.db.impl.protocols :as p]
    [blaze.db.resource-store.spec]
    [blaze.db.tx-log.spec]
    [clojure.spec.alpha :as s]))


(s/def :blaze.db/node
  #(satisfies? p/Node %))


(s/def :blaze.db/resource-cache
  :blaze.db/resource-store)


(s/def :blaze.db/op
  #{:create :put :delete})


(s/def :blaze.db/num-changes
  nat-int?)


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
