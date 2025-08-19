(ns blaze.db.api-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.async.comp-spec]
   [blaze.coll.core-spec]
   [blaze.coll.spec :as cs]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.db.tx-log.spec]
   [blaze.fhir.spec]
   [blaze.fhir.spec.type.system.spec]
   [blaze.spec]
   [blaze.util-spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef d/db
  :args (s/cat :node :blaze.db/node)
  :ret :blaze.db/db)

(s/fdef d/sync
  :args (s/cat :node :blaze.db/node :t (s/? :blaze.db/t))
  :ret ac/completable-future?)

(s/fdef d/transact
  :args (s/cat :node :blaze.db/node :tx-ops :blaze.db/tx-ops)
  :ret ac/completable-future?)

(s/fdef d/node
  :args (s/cat :db :blaze.db/db)
  :ret :blaze.db/node)

(s/fdef d/t
  :args (s/cat :db :blaze.db/db)
  :ret :blaze.db/t)

(s/fdef d/basis-t
  :args (s/cat :db :blaze.db/db)
  :ret :blaze.db/t)

(s/fdef d/tx
  :args (s/cat :node-or-db (s/or :node :blaze.db/node :db :blaze.db/db)
               :t :blaze.db/t)
  :ret (s/nilable :blaze.db/tx))

(s/fdef d/resource-handle
  :args (s/cat :db :blaze.db/db :type :fhir.resource/type :id :blaze.resource/id)
  :ret (s/nilable :blaze.db/resource-handle))

(s/fdef d/resource-handle?
  :args (s/cat :x any?)
  :ret boolean?)

(s/fdef d/deleted?
  :args (s/cat :resource-handle :blaze.db/resource-handle)
  :ret boolean?)

;; ---- Type-Level Functions --------------------------------------------------

(s/fdef d/type-list
  :args (s/cat :db :blaze.db/db :type :fhir.resource/type
               :start-id (s/? :blaze.resource/id))
  :ret (cs/coll-of :blaze.db/resource-handle))

(s/fdef d/type-total
  :args (s/cat :db :blaze.db/db :type :fhir.resource/type)
  :ret nat-int?)

(s/fdef d/type-query
  :args (s/cat :db :blaze.db/db :type :fhir.resource/type
               :clauses :blaze.db.query/clauses
               :start-id (s/? :blaze.resource/id))
  :ret (s/or :result (cs/coll-of :blaze.db/resource-handle)
             :anomaly ::anom/anomaly))

(s/fdef d/compile-type-query
  :args (s/cat :node-or-db (s/or :node :blaze.db/node :db :blaze.db/db)
               :type :fhir.resource/type
               :clauses :blaze.db.query/clauses)
  :ret (s/or :query :blaze.db/query :anomaly ::anom/anomaly))

(s/fdef d/compile-type-query-lenient
  :args (s/cat :node-or-db (s/or :node :blaze.db/node :db :blaze.db/db)
               :type :fhir.resource/type
               :clauses :blaze.db.query/clauses)
  :ret (s/or :query :blaze.db/query :anomaly ::anom/anomaly))

(s/fdef d/compile-type-matcher
  :args (s/cat :node-or-db (s/or :node :blaze.db/node :db :blaze.db/db)
               :type :fhir.resource/type
               :clauses :blaze.db.query/clauses)
  :ret (s/or :matcher :blaze.db/matcher :anomaly ::anom/anomaly))

;; ---- System-Level Functions ------------------------------------------------

(s/fdef d/system-list
  :args (s/cat
         :db :blaze.db/db
         :start (s/? (s/cat :start-type :fhir.resource/type
                            :start-id :blaze.resource/id)))
  :ret (cs/coll-of :blaze.db/resource-handle))

(s/fdef d/system-total
  :args (s/cat :db :blaze.db/db)
  :ret nat-int?)

(s/fdef d/system-query
  :args (s/cat :db :blaze.db/db
               :clauses :blaze.db.query/clauses)
  :ret (s/or :result (cs/coll-of :blaze.db/resource-handle)
             :anomaly ::anom/anomaly))

(s/fdef d/compile-system-query
  :args (s/cat :node-or-db (s/or :node :blaze.db/node :db :blaze.db/db)
               :clauses :blaze.db.query/clauses)
  :ret (s/or :query :blaze.db/query :anomaly ::anom/anomaly))

(s/fdef d/compile-system-matcher
  :args (s/cat :node-or-db (s/or :node :blaze.db/node :db :blaze.db/db)
               :clauses :blaze.db.query/clauses)
  :ret (s/or :matcher :blaze.db/matcher :anomaly ::anom/anomaly))

;; ---- Compartment-Level Functions -------------------------------------------

(s/fdef d/list-compartment-resource-handles
  :args (s/cat :db :blaze.db/db
               :code string?
               :id :blaze.resource/id
               :type :fhir.resource/type
               :start-id (s/? :blaze.resource/id))
  :ret (cs/coll-of :blaze.db/resource-handle))

(s/fdef d/compartment-query
  :args (s/cat :db :blaze.db/db
               :code string?
               :id :blaze.resource/id
               :type :fhir.resource/type
               :clauses :blaze.db.query/clauses)
  :ret (s/or :result (cs/coll-of :blaze.db/resource-handle)
             :anomaly ::anom/anomaly))

(s/fdef d/compile-compartment-query
  :args (s/cat :node-or-db (s/or :node :blaze.db/node :db :blaze.db/db)
               :code string?
               :type :fhir.resource/type
               :clauses :blaze.db.query/clauses)
  :ret (s/or :query :blaze.db/query :anomaly ::anom/anomaly))

(s/fdef d/patient-compartment-last-change-t
  :args (s/cat :db :blaze.db/db :patient-id :blaze.resource/id)
  :ret (s/nilable :blaze.db/t))

;; ---- Common Query Functions ------------------------------------------------

(s/fdef d/execute-query
  :args (s/cat :db :blaze.db/db :query :blaze.db/query :args (s/* any?))
  :ret (cs/coll-of :blaze.db/resource-handle))

(s/fdef d/explain-query
  :args (s/cat :db :blaze.db/db :query :blaze.db/query)
  :ret :blaze.db.query/plan)

(s/fdef d/query-clauses
  :args (s/cat :query :blaze.db/query)
  :ret (s/nilable :blaze.db.query/clauses))

;; ---- Common Matcher Functions ----------------------------------------------

(s/fdef d/matches?
  :args (s/cat :db :blaze.db/db :matcher :blaze.db/matcher
               :resource-handle :blaze.db/resource-handle)
  :ret boolean?)

(s/fdef d/matcher-transducer
  :args (s/cat :db :blaze.db/db :matcher :blaze.db/matcher)
  :ret fn?)

(s/fdef d/matcher-clauses
  :args (s/cat :matcher :blaze.db/matcher)
  :ret :blaze.db.query/clauses)

;; ---- History Functions -----------------------------------------------------

(s/fdef d/stop-history-at
  :args (s/cat :db :blaze.db/db :since inst?))

;; ---- Instance-Level History Functions --------------------------------------

(s/fdef d/instance-history
  :args (s/cat :db :blaze.db/db
               :type :fhir.resource/type
               :id :blaze.resource/id
               :start-t (s/? (s/nilable :blaze.db/t)))
  :ret (cs/coll-of :blaze.db/resource-handle))

(s/fdef d/total-num-of-instance-changes
  :args (s/cat :db :blaze.db/db
               :type :fhir.resource/type
               :id :blaze.resource/id
               :since (s/? (s/nilable inst?)))
  :ret nat-int?)

;; ---- Type-Level History Functions ------------------------------------------

(s/fdef d/type-history
  :args (s/cat :db :blaze.db/db
               :type :fhir.resource/type
               :start-t (s/? (s/nilable :blaze.db/t))
               :start-id (s/? (s/nilable :blaze.resource/id)))
  :ret (cs/coll-of :blaze.db/resource-handle))

(s/fdef d/total-num-of-type-changes
  :args (s/cat :db :blaze.db/db
               :type :fhir.resource/type
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
                     :start-type (s/nilable :fhir.resource/type)
                     :start-id (s/? (s/nilable :blaze.resource/id)))))))
  :ret (cs/coll-of :blaze.db/resource-handle))

(s/fdef d/total-num-of-system-changes
  :args (s/cat :db :blaze.db/db :since (s/? (s/nilable inst?)))
  :ret nat-int?)

(s/fdef d/include
  :args (s/cat :db :blaze.db/db :resource-handle :blaze.db/resource-handle
               :code string? :type (s/? :fhir.resource/type))
  :ret (cs/coll-of :blaze.db/resource-handle))

(s/fdef d/rev-include
  :args (s/cat :db :blaze.db/db :resource-handle :blaze.db/resource-handle
               :source-type (s/? :fhir.resource/type) :code (s/? string?))
  :ret (cs/coll-of :blaze.db/resource-handle))

(s/fdef d/patient-everything
  :args (s/cat :db :blaze.db/db :patient-handle :blaze.db/resource-handle
               :date-range (s/? (s/cat :start (s/nilable :system/date)
                                       :end (s/nilable :system/date))))
  :ret (cs/coll-of :blaze.db/resource-handle))

;; ---- Batch DB --------------------------------------------------------------

(s/fdef d/new-batch-db
  :args (s/cat :db :blaze.db/db)
  :ret :blaze.db/db)

;; ---- Pull ------------------------------------------------------------------

(s/fdef d/pull
  :args (s/cat :node-or-db (s/or :node :blaze.db/node :db :blaze.db/db)
               :resource-handle :blaze.db/resource-handle
               :variant (s/? :blaze.resource/variant))
  :ret ac/completable-future?)

(s/fdef d/pull-content
  :args (s/cat :node-or-db (s/or :node :blaze.db/node :db :blaze.db/db)
               :resource-handle :blaze.db/resource-handle)
  :ret ac/completable-future?)

(s/fdef d/pull-many
  :args (s/cat :node-or-db (s/or :node :blaze.db/node :db :blaze.db/db)
               :resource-handles (cs/coll-of :blaze.db/resource-handle)
               :variant (s/? (s/or :variant :blaze.resource/variant
                                   :elements (s/coll-of keyword?))))
  :ret ac/completable-future?)

(s/fdef d/re-index-total
  :args (s/cat :db :blaze.db/db :search-param-url string?)
  :ret (s/or :total nat-int? :anomaly ::anom/anomaly))

(s/fdef d/re-index
  :args (s/cat :db :blaze.db/db :search-param-url string?
               :start (s/? (s/cat :start-type :fhir.resource/type
                                  :start-id :blaze.resource/id)))
  :ret ac/completable-future?)
