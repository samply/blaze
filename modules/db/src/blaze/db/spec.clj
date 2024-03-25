(ns blaze.db.spec
  (:require
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.protocols :as p]
   [blaze.db.node :refer [node?]]
   [blaze.db.resource-store.spec]
   [blaze.db.tx-log.spec]
   [blaze.spec]
   [clojure.spec.alpha :as s])
  (:import
   [com.github.benmanes.caffeine.cache LoadingCache]))

(s/def :blaze.db/node
  node?)

(defn loading-cache? [x]
  (instance? LoadingCache x))

(s/def :blaze.db/tx-cache
  loading-cache?)

(s/def :blaze.db/resource-cache
  :blaze.db/resource-store)

(s/def :blaze.db/op
  #{:create :put :delete})

(s/def :blaze.db/num-changes
  nat-int?)

(s/def :blaze.db/db
  #(satisfies? p/Db %))

(s/def :blaze/db
  :blaze.db/db)

(s/def :blaze.db.tx/instant
  inst?)

(s/def :blaze.db/tx
  (s/keys :req [:blaze.db/t :blaze.db.tx/instant]))

(s/def :blaze.db/resource-handle
  rh/resource-handle?)

(s/def :blaze.db/query
  some?)

(defmulti tx-op "Transaction operator" first)

(defmethod tx-op :create [_]
  (s/cat :op #{:create}
         :resource :blaze/resource
         :if-none-exist (s/? :blaze.db.tx-cmd/if-none-exist)))

(defmulti put-precond-op "Put precondition operator" first)

(defmethod put-precond-op :if-match [_]
  (s/cat :op #{:if-match}
         :ts (s/+ :blaze.db/t)))

(defmethod put-precond-op :if-none-match [_]
  (s/cat :op #{:if-none-match}
         :val (s/or :any #{:any} :t :blaze.db/t)))

(s/def :blaze.db.tx-op.put/precondition
  (s/multi-spec put-precond-op first))

(defmethod tx-op :put [_]
  (s/cat :op #{:put}
         :resource :blaze/resource
         :precondition (s/? :blaze.db.tx-op.put/precondition)))

(defmethod tx-op :keep [_]
  (s/cat :op #{:keep}
         :type :fhir.resource/type
         :id :blaze.resource/id
         :hash :blaze.resource/hash
         :if-match (s/? (s/coll-of :blaze.db/t :kind vector? :min-count 1))))

(defmethod tx-op :delete [_]
  (s/cat :op #{:delete}
         :type :fhir.resource/type
         :id :blaze.resource/id))

(s/def :blaze.db/tx-op
  (s/multi-spec tx-op first))

(s/def :blaze.db/tx-ops
  (s/coll-of :blaze.db/tx-op :kind vector? :min-count 1))

(s/def :blaze.db/enforce-referential-integrity
  boolean?)
