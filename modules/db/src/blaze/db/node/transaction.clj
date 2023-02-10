(ns blaze.db.node.transaction
  (:require
    [blaze.anomaly :as ba]
    [blaze.db.impl.db :as db]
    [blaze.db.impl.index.tx-error :as tx-error]
    [blaze.db.impl.index.tx-success :as tx-success]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type :as type]))


(defmulti prepare-op (fn [_ [op]] op))


(defmethod prepare-op :create
  [{:keys [references-fn]} [op resource clauses]]
  (let [refs (references-fn resource)]
    (cond->
      {:op (name op)
       :type (name (fhir-spec/fhir-type resource))
       :id (:id resource)
       :resource resource}
      (seq refs)
      (assoc :refs refs)
      (seq clauses)
      (assoc :if-none-exist clauses))))


(defn- prepare-if-none-match [if-none-match]
  (if (= :any if-none-match)
    "*"
    if-none-match))


(defmethod prepare-op :put
  [{:keys [references-fn]} [op resource [precond-op precond]]]
  (let [refs (references-fn resource)]
    (cond->
      {:op (name op)
       :type (name (fhir-spec/fhir-type resource))
       :id (:id resource)
       :resource resource}
      (seq refs)
      (assoc :refs refs)
      (identical? :if-match precond-op)
      (assoc :if-match precond)
      (identical? :if-none-match precond-op)
      (assoc :if-none-match (prepare-if-none-match precond)))))


(defmethod prepare-op :delete
  [_ [_ type id]]
  {:op "delete"
   :type type
   :id id})


(defn- ctx
  [{:blaze.db/keys [enforce-referential-integrity]
    :or {enforce-referential-integrity true}}]
  {:references-fn
   (if enforce-referential-integrity
     type/references
     (constantly nil))})


(defn prepare-ops
  "Converts transaction operators into transaction commands.

  Puts :refs into the returned transaction commands if
  :blaze.db/enforce-referential-integrity is true in `context` which is the
  default."
  [context tx-ops]
  (mapv (partial prepare-op (ctx context)) tx-ops))


(defn- missing-tx-msg [t]
  (format "Can't find transaction result with point in time of %d." t))


(defn load-tx-result [{:keys [tx-cache kv-store] :as node} t]
  (if (tx-success/tx tx-cache t)
    (db/db node t)
    (if-let [anomaly (tx-error/tx-error kv-store t)]
      anomaly
      (ba/fault (missing-tx-msg t)))))
