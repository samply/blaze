(ns blaze.db.node.transaction
  (:require
   [blaze.anomaly :as ba]
   [blaze.db.impl.index.tx-error :as tx-error]
   [blaze.db.impl.index.tx-success :as tx-success]
   [blaze.fhir.canonical :as canonical]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.spec.type :as type]
   [taoensso.timbre :as log]))

(def ^:private access-control-systems
  (set (canonical/urls "CodeSystem/AccessControl")))

(defn- access-control-code [{:keys [system code]}]
  (when (contains? access-control-systems (:value system))
    (:value code)))

(defn- access-control-codes
  "Returns the distinct codes of the AccessControl CodeSystem, in the current
  and legacy system, `resource` carries in `meta.tag` and `meta.security`.

  The codes are extracted from both elements, because the permissions to write
  a code don't depend on whether it is carried as tag or as security label."
  {:arglists '([resource])}
  [{{:keys [tag security]} :meta}]
  (into [] (comp (keep access-control-code) (distinct)) (concat tag security)))

(defn- assoc-access-control
  "Associates the AccessControl codes of `resource` under :access-control to
  `tx-cmd` if there are any, so that they can be authorized while indexing,
  where the resource itself isn't available."
  [tx-cmd resource]
  (let [codes (access-control-codes resource)]
    (cond-> tx-cmd (seq codes) (assoc :access-control codes))))

(defmulti prepare-op (fn [_ [op]] op))

(defmethod prepare-op :create
  [{:keys [references-fn]} [op resource clauses]]
  (let [hash (hash/generate resource)
        refs (references-fn resource)]
    {:hash-resource
     [hash resource]
     :blaze.db/tx-cmd
     (-> (cond->
          {:op (name op)
           :type (name (:fhir/type resource))
           :id (:id resource)
           :hash hash}
           (seq refs)
           (assoc :refs refs)
           (seq clauses)
           (assoc :if-none-exist clauses))
         (assoc-access-control resource))}))

(defn- prepare-if-none-match [if-none-match]
  (if (= :any if-none-match)
    "*"
    if-none-match))

(defmethod prepare-op :put
  [{:keys [references-fn]} [op resource [precond-op & precond-vals]]]
  (let [hash (hash/generate resource)
        refs (references-fn resource)]
    {:hash-resource
     [hash resource]
     :blaze.db/tx-cmd
     (-> (cond->
          {:op (name op)
           :type (name (:fhir/type resource))
           :id (:id resource)
           :hash hash}
           (seq refs)
           (assoc :refs refs)
           (identical? :if-match precond-op)
           (assoc :if-match (vec precond-vals))
           (identical? :if-none-match precond-op)
           (assoc :if-none-match (prepare-if-none-match (first precond-vals))))
         (assoc-access-control resource))}))

(defmethod prepare-op :keep
  [_ [_ type id hash if-match]]
  {:blaze.db/tx-cmd
   (cond->
    {:op "keep"
     :type type
     :id id
     :hash hash}
     if-match
     (assoc :if-match if-match))})

(defmethod prepare-op :delete
  [{:blaze.db/keys [enforce-referential-integrity]} [_ type id]]
  {:blaze.db/tx-cmd
   (cond->
    {:op "delete"
     :type type
     :id id}
     enforce-referential-integrity
     (assoc :check-refs true))})

(defmethod prepare-op :conditional-delete
  [{:blaze.db/keys [enforce-referential-integrity allow-multiple-delete]}
   [_ type clauses]]
  {:blaze.db/tx-cmd
   (cond->
    {:op "conditional-delete"
     :type type}
     clauses
     (assoc :clauses clauses)
     enforce-referential-integrity
     (assoc :check-refs true)
     allow-multiple-delete
     (assoc :allow-multiple true))})

(defmethod prepare-op :delete-history
  [_ [_ type id]]
  {:blaze.db/tx-cmd
   {:op "delete-history"
    :type type
    :id id}})

(defmethod prepare-op :delete-history-version
  [_ [_ type id t]]
  {:blaze.db/tx-cmd
   {:op "delete-history-version"
    :type type
    :id id
    :t t}})

(defmethod prepare-op :patient-purge
  [{:blaze.db/keys [enforce-referential-integrity]} [_ id graph]]
  {:blaze.db/tx-cmd
   (cond->
    {:op "patient-purge"
     :id id}
     enforce-referential-integrity
     (assoc :check-refs true)
     graph
     (assoc :graph graph))})

(def ^:private split
  (juxt #(mapv :blaze.db/tx-cmd %) #(into {} (map :hash-resource) %)))

(defn- ctx
  [{:blaze.db/keys [enforce-referential-integrity]
    :or {enforce-referential-integrity true}
    :as context}]
  (assoc
   context
   :blaze.db/enforce-referential-integrity
   enforce-referential-integrity
   :references-fn
   (if enforce-referential-integrity
     type/references
     (constantly nil))))

(defn prepare-ops
  "Splits `tx-ops` into a tuple of :blaze.db/tx-cmds and a map of resource
  hashes to resource contents.

  Puts :refs into the returned :blaze.db/tx-cmds if
  :blaze.db/enforce-referential-integrity is true in `context` which is the
  default."
  [context tx-ops]
  (split (mapv (partial prepare-op (ctx context)) tx-ops)))

(defn- missing-tx-msg [t]
  (format "Can't find transaction result with point in time of %d." t))

(defn load-tx-result
  "Returns `t` if the transaction with `t` was successful or its anomaly
  otherwise."
  [{:keys [tx-cache kv-store]} t]
  (log/trace "load transaction result with t =" t)
  (if (tx-success/tx tx-cache t)
    t
    (if-let [anomaly (tx-error/tx-error kv-store t)]
      anomaly
      (ba/fault (missing-tx-msg t)))))
