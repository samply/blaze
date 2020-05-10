(ns blaze.db.api-spec
  (:require
    [blaze.db.api :as d]
    [blaze.db.impl.index]
    [blaze.db.impl.protocols :as p]
    [blaze.db.search-param-registry-spec]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [manifold.deferred :refer [deferred?]])
  (:import
    [clojure.lang IReduceInit]))


(s/def :blaze.db/op
  #{:create :put :delete})


(s/def :blaze.db/num-changes
  nat-int?)


(defmulti tx-op first)


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


(s/def :blaze.db/t
  nat-int?)


(s/def :blaze.db.tx/instant
  inst?)


(s/def :blaze.db/tx
  (s/keys :req [:blaze.db/t :blaze.db.tx/instant]))


(s/def :blaze.db/node
  #(satisfies? p/Node %))


(s/def :blaze.db/query
  some?)


(s/def :blaze.db.query/clause
  (s/coll-of string? :min-count 2))


(s/fdef d/db
  :args (s/cat :node :blaze.db/node)
  :ret :blaze.db/db)


(s/fdef d/sync
  :args (s/cat :node :blaze.db/node :t :blaze.db/t)
  :ret deferred?)


(s/fdef d/submit-tx
  :args (s/cat :node :blaze.db/node :tx-ops :blaze.db/tx-ops)
  :ret deferred?)


(s/fdef d/compile-type-query
  :args (s/cat :node-or-db (s/or :node :blaze.db/node :db :blaze.db/db)
               :type :blaze.resource/resourceType
               :clauses (s/coll-of :blaze.db.query/clause :min-count 1))
  :ret (s/or :query :blaze.db/query :anomaly ::anom/anomaly))


(s/fdef d/compile-compartment-query
  :args (s/cat :node-or-db (s/or :node :blaze.db/node :db :blaze.db/db)
               :code :blaze.db.compartment/code
               :type :blaze.resource/resourceType
               :clauses (s/coll-of :blaze.db.query/clause :min-count 1))
  :ret (s/or :query :blaze.db/query :anomaly ::anom/anomaly))


(s/fdef d/tx
  :args (s/cat :db :blaze.db/db :t :blaze.db/t)
  :ret (s/nilable :blaze.db/tx))


(s/fdef d/resource-exists?
  :args (s/cat :db :blaze.db/db :type :blaze.resource/resourceType :id :blaze.resource/id)
  :ret boolean?)


(s/fdef d/resource
  :args (s/cat :db :blaze.db/db :type :blaze.resource/resourceType :id :blaze.resource/id)
  :ret (s/nilable :blaze/resource))


(s/fdef d/deleted?
  :args (s/cat :resource :blaze/resource)
  :ret boolean?)


(s/fdef d/list-resources
  :args (s/cat :db :blaze.db/db :type :blaze.resource/resourceType
               :start-id (s/? :blaze.resource/id))
  :ret (s/coll-of :blaze/resource))


(s/fdef d/list-compartment-resources
  :args (s/cat :db :blaze.db/db
               :code :blaze.db.compartment/code :id :blaze.resource/id
               :type :blaze.resource/resourceType
               :start-id (s/? :blaze.resource/id))
  :ret (s/coll-of :blaze/resource))


(s/fdef d/execute-query
  :args (s/cat :db :blaze.db/db :query :blaze.db/query :args (s/* some?))
  :ret (s/coll-of :blaze/resource))


(s/fdef d/type-query
  :args (s/cat :db :blaze.db/db :type :blaze.resource/resourceType
               :clauses (s/coll-of :blaze.db.query/clause :min-count 1))
  :ret (s/or :result (s/coll-of :blaze/resource) :anomaly ::anom/anomaly))


(s/def :blaze.db.compartment/code
  string?)


(s/fdef d/compartment-query
  :args (s/cat :db :blaze.db/db
               :code :blaze.db.compartment/code :id :blaze.resource/id
               :type :blaze.resource/resourceType
               :clauses (s/coll-of :blaze.db.query/clause :min-count 1))
  :ret (s/or :result (s/coll-of :blaze/resource) :anomaly ::anom/anomaly))


(s/fdef d/type-total
  :args (s/cat :db :blaze.db/db :type :blaze.resource/resourceType)
  :ret nat-int?)


(s/fdef d/instance-history
  :args (s/cat :db :blaze.db/db
               :type :blaze.resource/resourceType
               :id :blaze.resource/id
               :start-t (s/nilable :blaze.db/t)
               :since (s/nilable inst?))
  :ret (s/coll-of :blaze/resource))


(s/fdef d/total-num-of-instance-changes
  :args (s/cat :db :blaze.db/db
               :type :blaze.resource/resourceType
               :id :blaze.resource/id
               :since (s/nilable inst?))
  :ret nat-int?)


(s/fdef d/type-history
  :args (s/cat :db :blaze.db/db
               :type :blaze.resource/resourceType
               :start-t (s/nilable :blaze.db/t)
               :start-id (s/nilable :blaze.resource/id)
               :since (s/nilable inst?))
  :ret (s/coll-of :blaze/resource))


(s/fdef d/total-num-of-type-changes
  :args (s/cat :db :blaze.db/db :type :blaze.resource/resourceType
               :since (s/? (s/nilable inst?)))
  :ret nat-int?)


(s/fdef d/system-history
  :args (s/cat :db :blaze.db/db
               :start-t (s/nilable :blaze.db/t)
               :start-type (s/nilable :blaze.resource/resourceType)
               :start-id (s/nilable :blaze.resource/id)
               :since (s/nilable inst?))
  :ret (s/coll-of :blaze/resource))


(s/fdef d/total-num-of-system-changes
  :args (s/cat :db :blaze.db/db :since (s/nilable inst?))
  :ret nat-int?)


(s/fdef d/ri-first
  :args (s/cat :coll (s/nilable #(instance? IReduceInit %)))
  :ret any?)
