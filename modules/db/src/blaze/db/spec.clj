(ns blaze.db.spec
  (:require
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.protocols :as p]
   [blaze.db.node.protocols :as np]
   [blaze.db.query.spec]
   [blaze.db.resource-store.spec]
   [blaze.db.tx-log.spec]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as sg]
   [java-time.api :as time])
  (:import
   [com.github.benmanes.caffeine.cache LoadingCache]
   [java.time Instant]))

(s/def :blaze.db/node
  #(satisfies? np/Node %))

(s/def :blaze.db/tx-cache
  #(instance? LoadingCache %))

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
  (s/with-gen
    #(instance? Instant %)
    #(sg/fmap time/instant (sg/choose -5364662008000 7258118400000))))

(s/def :blaze.db/tx
  (s/keys :req [:blaze.db/t :blaze.db.tx/instant]))

(s/def :blaze.db/resource-handle
  rh/resource-handle?)

(s/def :blaze.db/query
  #(satisfies? p/Query %))

(s/def :blaze.db/matcher
  #(satisfies? p/Matcher %))

(defmulti tx-op "Transaction operator" first)

(defmethod tx-op :create [_]
  (s/cat :op #{:create}
         :resource :fhir/Resource
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
         :resource :fhir/Resource
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

(defmethod tx-op :conditional-delete [_]
  (s/cat :op #{:conditional-delete}
         :type :fhir.resource/type
         :clauses (s/? (s/coll-of :blaze.db.query/search-clause :kind vector? :min-count 1))))

(defmethod tx-op :delete-history [_]
  (s/cat :op #{:delete-history}
         :type :fhir.resource/type
         :id :blaze.resource/id))

(defmethod tx-op :patient-purge [_]
  (s/cat :op #{:patient-purge}
         :id :blaze.resource/id))

(s/def :blaze.db/tx-op
  (s/multi-spec tx-op first))

(s/def :blaze.db/tx-ops
  (s/coll-of :blaze.db/tx-op :kind vector? :min-count 1))

(s/def :blaze.db/enforce-referential-integrity
  boolean?)

(s/def :blaze.db/allow-multiple-delete
  boolean?)
