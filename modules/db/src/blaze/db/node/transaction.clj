(ns blaze.db.node.transaction
  (:require
    [blaze.anomaly :as ba]
    [blaze.db.impl.db :as db]
    [blaze.db.impl.index.tx-error :as tx-error]
    [blaze.db.impl.index.tx-success :as tx-success]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type :as type]))


(defmulti prepare-op first)


(defmethod prepare-op :create
  [[op resource clauses]]
  (let [hash (hash/generate resource)
        refs (type/references resource)]
    {:hash-resource
     [hash resource]
     :blaze.db/tx-cmd
     (cond->
       {:op (name op)
        :type (name (fhir-spec/fhir-type resource))
        :id (:id resource)
        :hash hash}
       (seq refs)
       (assoc :refs refs)
       (seq clauses)
       (assoc :if-none-exist clauses))}))


(defmethod prepare-op :put
  [[op resource matches]]
  (let [hash (hash/generate resource)
        refs (type/references resource)]
    {:hash-resource
     [hash resource]
     :blaze.db/tx-cmd
     (cond->
       {:op (name op)
        :type (name (fhir-spec/fhir-type resource))
        :id (:id resource)
        :hash hash}
       (seq refs)
       (assoc :refs refs)
       matches
       (assoc :if-match matches))}))


(defmethod prepare-op :delete
  [[_ type id]]
  {:blaze.db/tx-cmd
   {:op "delete"
    :type type
    :id id}})


(def ^:private split
  (juxt #(mapv :blaze.db/tx-cmd %) #(into {} (map :hash-resource) %)))


(defn prepare-ops
  "Splits `tx-ops` into a tuple of :blaze.db/tx-cmds and a map of resource
  hashes to resource contents."
  [tx-ops]
  (split (mapv prepare-op tx-ops)))


(defn- missing-tx-msg [t]
  (format "Can't find transaction result with point in time of %d." t))


(defn load-tx-result [{:keys [tx-cache kv-store] :as node} t]
  (if (tx-success/tx tx-cache t)
    (db/db node t)
    (if-let [anomaly (tx-error/tx-error kv-store t)]
      anomaly
      (ba/fault (missing-tx-msg t)))))
