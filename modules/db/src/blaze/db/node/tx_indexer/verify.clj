(ns blaze.db.node.tx-indexer.verify
  (:require
   [blaze.anomaly :as ba :refer [throw-anom]]
   [blaze.db.api :as d]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.patient-last-change :as plc]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.index.rts-as-of :as rts]
   [blaze.db.impl.index.system-stats :as system-stats]
   [blaze.db.impl.index.type-stats :as type-stats]
   [blaze.db.kv.spec]
   [blaze.db.node.tx-indexer.util :as tx-u]
   [blaze.db.search-param-registry :as sr]
   [blaze.fhir.hash :as hash]
   [blaze.util :as u]
   [clojure.string :as str]

   [prometheus.alpha :as prom :refer [defhistogram]]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(defhistogram transaction-sizes
  "Number of transaction commands per transaction."
  {:namespace "blaze"
   :subsystem "db_node"
   :name "transaction_sizes"}
  (take 16 (iterate #(* 2 %) 1)))

(defmulti format-command :op)

(defmethod format-command "hold" [{:keys [type id if-none-exist]}]
  (format "create %s?%s (resolved to id %s)" type
          (tx-u/clauses->query-params if-none-exist) id))

(defmethod format-command :default [{:keys [op type id]}]
  (format "%s %s/%s" op type id))

(defn- duplicate-tx-cmds-msg [cmd-a cmd-b]
  (format "Duplicate transaction commands `%s` and `%s`."
          (format-command cmd-a) (format-command cmd-b)))

(defn- detect-duplicate-commands! [cmds]
  (reduce
   (fn [index {:keys [type id] :as cmd}]
     (if-let [existing-cmd (get index [type id])]
       (throw-anom (ba/conflict (duplicate-tx-cmds-msg cmd existing-cmd)))
       (assoc index [type id] cmd)))
   {}
   cmds))

(defmulti verify
  "Verifies `command`. Returns `res` with added index entries and statistics of
  the transaction outcome.

  Throws an anomaly on conflicts."
  {:arglists '([db-before t res command])}
  (fn [_db-before _t _res {:keys [op]}] op))

(defn- verify-tx-cmd-create-msg [type id]
  (format "verify-tx-cmd :create %s/%s" type id))

(defn- id-collision-msg [type id t]
  (format "Resource `%s/%s` already exists in the database with t = %d and can't be created again." type id t))

(defn- check-id-collision! [db type id]
  (when (d/resource-handle db type id)
    (throw-anom (ba/conflict (id-collision-msg type id (d/t db))))))

(defn- index-entries
  "Creates index entries for the resource with `tid` and `id`.

  `refs` are used to update the PatientLastChange index in case a Patient is
  referenced."
  [tid id t hash num-changes op refs]
  (let [id (codec/id-byte-string id)]
    (into
     (rts/index-entries tid id t hash num-changes op)
     (keep (fn [[ref-type ref-id]]
             (when (= "Patient" ref-type)
               (plc/index-entry (codec/id-byte-string ref-id) t))))
     refs)))

(def ^:private inc-0 (fnil inc 0))
(def ^:private minus-0 (fnil - 0))

(defmethod verify "create" [db-before t res {:keys [type id hash refs]}]
  (log/trace (verify-tx-cmd-create-msg type id))
  (with-open [_ (prom/timer tx-u/duration-seconds "verify-create")]
    (check-id-collision! db-before type id)
    (let [tid (codec/tid type)]
      (-> (update res :entries into (index-entries tid id t hash 1 :create refs))
          (update :new-resources conj [type id])
          (update-in [:stats tid :num-changes] inc-0)
          (update-in [:stats tid :total] inc-0)))))

(defn- print-etags [ts]
  (str/join "," (map (partial format "W/\"%d\"") ts)))

(defn- verify-tx-cmd-put-msg [type id if-match if-none-match]
  (cond
    if-match
    (format "verify-tx-cmd :put %s/%s if-match: %s" type id (print-etags if-match))
    if-none-match
    (format "verify-tx-cmd :put %s/%s if-none-match: %s" type id if-none-match)
    :else
    (format "verify-tx-cmd :put %s/%s" type id)))

(defn- precondition-failed-msg [if-match type id]
  (format "Precondition `%s` failed on `%s/%s`." (print-etags if-match) type id))

(defn- precondition-failed-anomaly [if-match type id tx-cmd]
  (ba/conflict (precondition-failed-msg if-match type id)
               :http/status 412 :blaze.db/tx-cmd tx-cmd))

(defn- precondition-any-failed-msg [type id]
  (format "Resource `%s/%s` already exists." type id))

(defn- precondition-any-failed-anomaly [type id]
  (ba/conflict (precondition-any-failed-msg type id) :http/status 412))

(defn- precondition-version-failed-msg [type id if-none-match]
  (format "Resource `%s/%s` with version %d already exists." type id if-none-match))

(defn- precondition-version-failed-anomaly [type id if-none-match]
  (ba/conflict (precondition-version-failed-msg type id if-none-match) :http/status 412))

(defmethod verify "put"
  [db-before t res
   {:keys [type id hash if-match if-none-match refs] :as tx-cmd}]
  (log/trace (verify-tx-cmd-put-msg type id (u/to-seq if-match) if-none-match))
  (with-open [_ (prom/timer tx-u/duration-seconds "verify-put")]
    (let [tid (codec/tid type)
          if-match (u/to-seq if-match)
          {:keys [num-changes op] :or {num-changes 0} old-t :t old-hash :hash}
          (d/resource-handle db-before type id)]
      (cond
        (and if-match (not (some #{old-t} if-match)))
        (throw-anom (precondition-failed-anomaly if-match type id tx-cmd))

        (and (some? old-t) (= "*" if-none-match))
        (throw-anom (precondition-any-failed-anomaly type id))

        (and (some? old-t) (= if-none-match old-t))
        (throw-anom (precondition-version-failed-anomaly type id if-none-match))

        (= old-hash hash)
        res

        :else
        (cond->
         (-> (update res :entries into (index-entries tid id t hash (inc num-changes) :put refs))
             (update :new-resources conj [type id])
             (update-in [:stats tid :num-changes] inc-0))
          (or (nil? old-t) (identical? :delete op))
          (update-in [:stats tid :total] inc-0))))))

(defn- verify-tx-cmd-keep-msg [type id if-match]
  (if if-match
    (format "verify-tx-cmd :keep %s/%s if-match: %s" type id (print-etags if-match))
    (format "verify-tx-cmd :keep %s/%s" type id)))

(defmethod verify "keep"
  [db-before _ res {:keys [type id hash if-match] :as tx-cmd}]
  (log/trace (verify-tx-cmd-keep-msg type id (u/to-seq if-match)))
  (with-open [_ (prom/timer tx-u/duration-seconds "verify-keep")]
    (let [if-match (u/to-seq if-match)
          {old-hash :hash old-t :t} (d/resource-handle db-before type id)]
      (cond
        (and if-match (not (some #{old-t} if-match)))
        (throw-anom (precondition-failed-anomaly if-match type id tx-cmd))

        (not= hash old-hash)
        (let [msg (format "Keep failed on `%s/%s`." type id)]
          (log/trace msg)
          (throw-anom (ba/conflict msg :blaze.db/tx-cmd tx-cmd)))

        :else
        res))))

(defn- patient-refs
  "Returns references from `resource-handle` to Patient resources."
  [{{:keys [search-param-registry]} :node :as db} type resource-handle]
  (into
   []
   (comp (mapcat #(d/include db resource-handle % "Patient"))
         (map (fn [{:keys [id]}] ["Patient" id])))
   (sr/compartment-resources search-param-registry "Patient" type)))

(defmethod verify "delete"
  [db-before t res {:keys [type id]}]
  (log/trace "verify-tx-cmd :delete" (str type "/" id))
  (with-open [_ (prom/timer tx-u/duration-seconds "verify-delete")]
    (let [tid (codec/tid type)
          {:keys [num-changes op] :or {num-changes 0} :as old-resource-handle}
          (d/resource-handle db-before type id)
          refs (some->> old-resource-handle (patient-refs db-before type))]
      (if (identical? :delete op)
        res
        (cond->
         (-> (update res :entries into (index-entries tid id t hash/deleted-hash (inc num-changes) :delete refs))
             (update :del-resources conj [type id])
             (update-in [:stats tid :num-changes] inc-0))
          op
          (update-in [:stats tid :total] (fnil dec 0)))))))

(def ^:private ^:const ^long delete-history-max 100000)

(defn- too-many-history-entries-msg [type id]
  (format "Deleting the history of `%s/%s` failed because there are more than %,d history entries."
          type id delete-history-max))

(defn- too-many-history-entries-anom [type id]
  (ba/conflict
   (too-many-history-entries-msg type id)
   :fhir/issue "too-costly"))

(defn- purge-entry [tid id t rh]
  (rts/index-entries tid id (rh/t rh) (rh/hash rh) (rh/num-changes rh) (rh/op rh) t))

(defn- purge-entries [tid id t instance-history]
  (into [] (mapcat (partial purge-entry tid id t)) instance-history))

(defn- add-delete-history-entries [entries tid id t instance-history]
  (-> (update entries :entries into (purge-entries tid (codec/id-byte-string id) t instance-history))
      (update-in [:stats tid :num-changes] minus-0 (count instance-history))))

(defmethod verify "delete-history"
  [db-before t res {:keys [type id]}]
  (log/trace "verify-tx-cmd :delete-history" (str type "/" id))
  (with-open [_ (prom/timer tx-u/duration-seconds "verify-delete-history")]
    (let [tid (codec/tid type)
          instance-history (into [] (comp (drop 1) (take delete-history-max))
                                 (d/instance-history db-before type id))]
      (cond
        (empty? instance-history) res

        (= delete-history-max (count instance-history))
        (throw-anom (too-many-history-entries-anom type id))

        :else
        (add-delete-history-entries res tid id t instance-history)))))

(defmethod verify :default
  [_db-before _t res _tx-cmd]
  res)

(defn- verify-tx-cmds* [db-before t tx-cmds]
  (reduce
   (partial verify db-before t)
   {:entries []
    :new-resources #{}
    :del-resources #{}}
   tx-cmds))

(def ^:private empty-stats
  {:total 0 :num-changes 0})

(defn- type-stat-entry [snapshot t new-t [tid increments]]
  (let [current-stats (or (type-stats/seek-value snapshot tid t) empty-stats)]
    (type-stats/index-entry tid new-t (merge-with + current-stats increments))))

(defn- conj-type-stats [entries {:keys [snapshot t]} new-t stats]
  (with-open [_ (prom/timer tx-u/duration-seconds "type-stats")]
    (into entries (map (partial type-stat-entry snapshot t new-t)) stats)))

(defn- system-stats [{:keys [snapshot t]} new-t stats]
  (with-open [_ (prom/timer tx-u/duration-seconds "system-stats")]
    (let [current-stats (or (system-stats/seek-value snapshot t) empty-stats)]
      (system-stats/index-entry new-t (apply merge-with + current-stats (vals stats))))))

(defn- post-process-res [db-before t {:keys [entries stats]}]
  (cond-> (conj-type-stats entries db-before t stats)
    stats
    (conj (system-stats db-before t stats))))

(defn- resource-exists? [db type id]
  (when-let [{:keys [op]} (d/resource-handle db type id)]
    (not (identical? :delete op))))

(defn- ref-integrity-del-msg [src-type src-id type id more?]
  (format "Referential integrity violated. Resource `%s/%s` should be deleted but is referenced from `%s/%s`%s."
          type id src-type src-id (if more? " and others" "")))

(defn- ref-integrity-del-anom [src-type src-id type id more?]
  (ba/conflict (ref-integrity-del-msg src-type src-id type id more?)))

(defn- ref-integrity-msg [type id]
  (format "Referential integrity violated. Resource `%s/%s` doesn't exist."
          type id))

(defn- check-referential-integrity-write!
  [db new-resources del-resources src-type src-id references]
  (run!
   (fn [[type id :as reference]]
     (cond
       (contains? del-resources reference)
       (throw-anom (ref-integrity-del-anom src-type src-id type id false))

       (and (not (contains? new-resources reference))
            (not (resource-exists? db type id)))
       (throw-anom (ba/conflict (ref-integrity-msg type id)))))
   references))

(defn- check-referential-integrity-delete!
  [db del-resources type id]
  (when-let [resource-handle (d/resource-handle db type id)]
    (let [[[type-ref id-ref] second]
          (into
           []
           (comp (map rh/local-ref-tuple) (remove del-resources) (take 2))
           (d/rev-include db resource-handle))]
      (when type-ref
        (throw-anom (ref-integrity-del-anom type-ref id-ref type id second))))))

(defn- check-referential-integrity!
  [db {:keys [new-resources del-resources]} cmds]
  (run!
   (fn [{:keys [op type id refs check-refs]}]
     (if (= op "delete")
       (when check-refs
         (check-referential-integrity-delete!
          db del-resources type id))
       (when refs
         (check-referential-integrity-write!
          db new-resources del-resources type id refs))))
   cmds))

(defn verify-tx-cmds
  "Verifies transaction commands. Returns index entries of the transaction
  outcome if it is successful or an anomaly if it fails.

  The `t` is for the new transaction to commit."
  [db-before t tx-cmds]
  (prom/observe! transaction-sizes (count tx-cmds))
  (ba/try-anomaly
   (detect-duplicate-commands! tx-cmds)
   (let [res (verify-tx-cmds* db-before t tx-cmds)]
     (check-referential-integrity! db-before res tx-cmds)
     (post-process-res db-before t res))))
