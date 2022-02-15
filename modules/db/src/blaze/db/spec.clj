(ns blaze.db.spec
  (:require
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.db.impl.protocols :as p]
    [blaze.db.node.protocols :as np]
    [blaze.db.resource-store.spec]
    [blaze.db.tx-log.spec]
    [blaze.spec]
    [clojure.spec.alpha :as s])
  (:import
    [com.github.benmanes.caffeine.cache Cache LoadingCache]))


(defn node? [x]
  (satisfies? np/Node x))


(s/def :blaze.db/node
  node?)


(s/def :blaze.db/resource-handle-cache
  #(instance? Cache %))


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
         :if-none-exist (s/? :blaze.db.query/clauses)))


(defmethod tx-op :put [_]
  (s/cat :op #{:put}
         :resource :blaze/resource
         :matches (s/? :blaze.db/t)))


(defmethod tx-op :delete [_]
  (s/cat :op #{:delete}
         :type :fhir.resource/type
         :id :blaze.resource/id))


;; Transaction Operation
;;
;; Exapmples:
;;  * [:create {:fhir/type :fhir/Patient :id "0"}]
;;  * [:create {:fhir/type :fhir/Patient :id "0"} [["identifier" "foo"]]]
;;  * [:put {:fhir/type :fhir/Patient :id "0"}]
;;  * [:delete "Patient" "0"]
(s/def :blaze.db/tx-op
  (s/multi-spec tx-op first))


;; Transaction Operations
(s/def :blaze.db/tx-ops
  (s/coll-of :blaze.db/tx-op :kind vector? :min-count 1))


(s/def :blaze.db/enforce-referential-integrity
  boolean?)
