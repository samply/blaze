(ns blaze.db.api-spec
  (:require
    [blaze.db.api :as d]
    [blaze.db.impl.index]
    [blaze.db.search-param-registry-spec]
    [blaze.db.spec]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [manifold.deferred :refer [deferred?]]))


(s/fdef d/db
  :args (s/cat :node :blaze.db/node)
  :ret :blaze.db/db)


(s/fdef d/sync
  :args (s/cat :node :blaze.db/node :t :blaze.db/t)
  :ret deferred?)


(s/fdef d/submit-tx
  :args (s/cat :node :blaze.db/node :tx-ops :blaze.db/tx-ops)
  :ret deferred?)


(s/fdef d/tx
  :args (s/cat :db :blaze.db/db :t :blaze.db/t)
  :ret (s/nilable :blaze.db/tx))


(s/fdef d/resource
  :args (s/cat :db :blaze.db/db :type :blaze.resource/resourceType :id :blaze.resource/id)
  :ret (s/nilable :blaze/resource))


(s/fdef d/deleted?
  :args (s/cat :resource :blaze/resource)
  :ret boolean?)



;; ---- Type-Level Functions --------------------------------------------------

(s/fdef d/list-resources
  :args (s/cat :db :blaze.db/db :type :blaze.resource/resourceType
               :start-id (s/? (s/nilable :blaze.resource/id)))
  :ret (s/coll-of :blaze/resource))


(s/fdef d/type-total
  :args (s/cat :db :blaze.db/db :type :blaze.resource/resourceType)
  :ret nat-int?)


(s/fdef d/type-query
  :args (s/cat :db :blaze.db/db :type :blaze.resource/resourceType
               :clauses (s/coll-of :blaze.db.query/clause :min-count 1))
  :ret (s/or :result (s/coll-of :blaze/resource) :anomaly ::anom/anomaly))


(s/fdef d/compile-type-query
  :args (s/cat :node-or-db (s/or :node :blaze.db/node :db :blaze.db/db)
               :type :blaze.resource/resourceType
               :clauses (s/coll-of :blaze.db.query/clause :min-count 1))
  :ret (s/or :query :blaze.db/query :anomaly ::anom/anomaly))



;; ---- System-Level Functions ------------------------------------------------

(s/fdef d/system-list
  :args (s/cat
          :db :blaze.db/db
          :more (s/? (s/cat
                       :start-type (s/nilable :blaze.resource/resourceType)
                       :start-id (s/? (s/nilable :blaze.resource/id)))))
  :ret (s/coll-of :blaze/resource))


(s/fdef d/system-total
  :args (s/cat :db :blaze.db/db)
  :ret nat-int?)


(s/fdef d/system-query
  :args (s/cat :db :blaze.db/db
               :clauses (s/coll-of :blaze.db.query/clause :min-count 1))
  :ret (s/or :result (s/coll-of :blaze/resource) :anomaly ::anom/anomaly))


(s/fdef d/compile-system-query
  :args (s/cat :node-or-db (s/or :node :blaze.db/node :db :blaze.db/db)
               :clauses (s/coll-of :blaze.db.query/clause :min-count 1))
  :ret (s/or :query :blaze.db/query :anomaly ::anom/anomaly))



;; ---- Compartment-Level Functions -------------------------------------------

(s/fdef d/list-compartment-resources
  :args (s/cat :db :blaze.db/db
               :code :blaze.db.compartment/code :id :blaze.resource/id
               :type :blaze.resource/resourceType
               :start-id (s/? :blaze.resource/id))
  :ret (s/coll-of :blaze/resource))


(s/def :blaze.db.compartment/code
  string?)


(s/fdef d/compartment-query
  :args (s/cat :db :blaze.db/db
               :code :blaze.db.compartment/code :id :blaze.resource/id
               :type :blaze.resource/resourceType
               :clauses (s/coll-of :blaze.db.query/clause :min-count 1))
  :ret (s/or :result (s/coll-of :blaze/resource) :anomaly ::anom/anomaly))


(s/fdef d/compile-compartment-query
  :args (s/cat :node-or-db (s/or :node :blaze.db/node :db :blaze.db/db)
               :code :blaze.db.compartment/code
               :type :blaze.resource/resourceType
               :clauses (s/coll-of :blaze.db.query/clause :min-count 1))
  :ret (s/or :query :blaze.db/query :anomaly ::anom/anomaly))



;; ---- Common Query Functions ------------------------------------------------

(s/fdef d/execute-query
  :args (s/cat :db :blaze.db/db :query :blaze.db/query :args (s/* some?))
  :ret (s/coll-of :blaze/resource))



;; ---- Instance-Level History Functions --------------------------------------

(s/fdef d/instance-history
  :args (s/cat :db :blaze.db/db
               :type :blaze.resource/resourceType
               :id :blaze.resource/id
               :start-t (s/? (s/nilable :blaze.db/t))
               :since (s/? (s/nilable inst?)))
  :ret (s/coll-of :blaze/resource))


(s/fdef d/total-num-of-instance-changes
  :args (s/cat :db :blaze.db/db
               :type :blaze.resource/resourceType
               :id :blaze.resource/id
               :since (s/? (s/nilable inst?)))
  :ret nat-int?)



;; ---- Type-Level History Functions ------------------------------------------

(s/fdef d/type-history
  :args (s/cat :db :blaze.db/db
               :type :blaze.resource/resourceType
               :start-t (s/? (s/nilable :blaze.db/t))
               :start-id (s/? (s/nilable :blaze.resource/id))
               :since (s/? (s/nilable inst?)))
  :ret (s/coll-of :blaze/resource))


(s/fdef d/total-num-of-type-changes
  :args (s/cat :db :blaze.db/db
               :type :blaze.resource/resourceType
               :since (s/? (s/nilable inst?)))
  :ret nat-int?)



;; ---- System-Level History Functions ----------------------------------------

(s/fdef d/system-history
  :args (s/cat
          :db :blaze.db/db
          :more
          (s/? (s/cat
                 :start-t (s/nilable :blaze.db/t)
                 :more
                 (s/? (s/cat
                        :start-type (s/nilable :blaze.resource/resourceType)
                        :more
                        (s/? (s/cat
                               :start-id (s/nilable :blaze.resource/id)
                               :since (s/? (s/nilable inst?)))))))))
  :ret (s/coll-of :blaze/resource))


(s/fdef d/total-num-of-system-changes
  :args (s/cat :db :blaze.db/db :since (s/? (s/nilable inst?)))
  :ret nat-int?)



;; ---- Batch DB --------------------------------------------------------------

(s/fdef d/new-batch-db
  :args (s/cat :db :blaze.db/db)
  :ret :blaze.db/db)
