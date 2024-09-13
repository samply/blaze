(ns blaze.db.node.tx-indexer.verify
  (:require
   [blaze.anomaly :as ba :refer [if-ok throw-anom when-ok]]
   [blaze.db.api :as d]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.patient-last-change :as plc]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.index.rts-as-of :as rts]
   [blaze.db.impl.index.system-stats :as system-stats]
   [blaze.db.impl.index.type-stats :as type-stats]
   [blaze.db.kv.spec]
   [blaze.db.search-param-registry :as sr]
   [blaze.fhir.hash :as hash]
   [blaze.util :as u]
   [clojure.string :as str]
   [cognitect.anomalies :as anom]
   [prometheus.alpha :as prom :refer [defhistogram]]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(defhistogram duration-seconds
  "Durations in transaction indexer."
  {:namespace "blaze"
   :subsystem "db"
   :name "tx_indexer_duration_seconds"}
  (take 16 (iterate #(* 2 %) 0.00001))
  "op")

(defhistogram transaction-sizes
  "Number of transaction commands per transaction."
  {:namespace "blaze"
   :subsystem "db_node"
   :name "transaction_sizes"}
  (take 16 (iterate #(* 2 %) 1)))

(defmulti expand-conditional-command
  "Expands possibly conditional `command` into possibly many non-conditional
  commands.

  Returns an anomaly on errors."
  {:arglists '([db-before command])}
  (fn [_ {:keys [op]}] op))

(defn- clauses->query-params [clauses]
  (->> clauses
       (map (fn [[param & values]] (str param "=" (str/join "," values))))
       (str/join "&")))

(defn- failing-conditional-create-query-msg [type clauses {::anom/keys [message]}]
  (format "Conditional create of a %s with query `%s` failed. Cause: %s"
          type (clauses->query-params clauses) message))

(defn- conditional-create-matches [db type clauses]
  (if-ok [resource-handles (d/type-query db type clauses)]
    (into [] (take 2) resource-handles)
    #(ba/incorrect (failing-conditional-create-query-msg type clauses %))))

(defn- format-handle [type {:keys [id t]}]
  (format "%s/%s/_history/%s" type id t))

(defn- multiple-existing-resources-msg [type clauses [h1 h2]]
  (format "Conditional create of a %s with query `%s` failed because at least the two matches `%s` and `%s` were found."
          type (clauses->query-params clauses) (format-handle type h1)
          (format-handle type h2)))

(defn- multiple-existing-resources-anom [type clauses handles]
  (ba/conflict
   (multiple-existing-resources-msg type clauses handles)
   :http/status 412))

(defmethod expand-conditional-command "create"
  [db-before {:keys [type if-none-exist] :as command}]
  (with-open [_ (prom/timer duration-seconds "expand-create")]
    (when-ok [[h1 h2] (some->> if-none-exist (conditional-create-matches db-before type))]
      (cond
        h2 (multiple-existing-resources-anom type if-none-exist [h1 h2])
        h1 [(assoc command :op "hold" :id (:id h1))]
        :else [command]))))

(def ^:private ^:const ^long max-multiple-deletes 10000)

(defn- too-many-multiple-matches-msg
  ([type]
   (format "Conditional delete of all %ss failed because more than %,d matches were found."
           type max-multiple-deletes))
  ([type clauses]
   (format "Conditional delete of %ss with query `%s` failed because more than %,d matches were found."
           type (clauses->query-params clauses) max-multiple-deletes)))

(defn- too-many-multiple-matches-anom [type clauses]
  (ba/conflict
   (if clauses
     (too-many-multiple-matches-msg type clauses)
     (too-many-multiple-matches-msg type))
   :fhir/issue "too-costly"))

(defn- failing-conditional-delete-query-msg [type clauses {::anom/keys [message]}]
  (format "Conditional delete of %ss with query `%s` failed. Cause: %s"
          type (clauses->query-params clauses) message))

(defn- conditional-delete-matches [db type clauses]
  (-> (d/type-query db type clauses)
      (ba/exceptionally #(ba/incorrect (failing-conditional-delete-query-msg type clauses %)))))

(defn- multiple-matches-msg
  ([type [h1 h2]]
   (format "Conditional delete of one single %s without a query failed because at least the two matches `%s` and `%s` were found."
           type (format-handle type h1) (format-handle type h2)))
  ([type clauses [h1 h2]]
   (format "Conditional delete of one single %s with query `%s` failed because at least the two matches `%s` and `%s` were found."
           type (clauses->query-params clauses) (format-handle type h1)
           (format-handle type h2))))

(defn- multiple-matches-anom [type clauses handles]
  (ba/conflict
   (if clauses
     (multiple-matches-msg type clauses handles)
     (multiple-matches-msg type handles))
   :http/status 412))

(defmethod expand-conditional-command "conditional-delete"
  [db-before {:keys [type clauses allow-multiple] :as command}]
  (with-open [_ (prom/timer duration-seconds "expand-conditional-delete")]
    (when-ok [matches (or (some->> clauses (conditional-delete-matches db-before type))
                          (d/type-list db-before type))]
      (if allow-multiple
        (let [handles (into [] (take (inc max-multiple-deletes)) matches)]
          (if (< max-multiple-deletes (count handles))
            (too-many-multiple-matches-anom type clauses)
            (mapv (fn [{:keys [id]}] (assoc command :op "delete" :id id)) handles)))
        (let [[h1 h2] (into [] (take 2) matches)]
          (cond
            h2 (multiple-matches-anom type clauses [h1 h2])
            h1 [(assoc command :op "delete" :id (:id h1))]
            :else []))))))

(defmethod expand-conditional-command :default
  [_ command]
  [command])

(defn- expand-conditional-commands
  "Expands all conditional `commands` into non-conditional commands.

  Returns an anomaly on errors."
  [db-before commands]
  (with-open [_ (prom/timer duration-seconds "expand-tx-cmds")]
    (transduce
     (comp (map (partial expand-conditional-command db-before))
           (halt-when ba/anomaly?)
           cat)
     conj
     commands)))

(defmulti format-command :op)

(defmethod format-command "hold" [{:keys [type id if-none-exist]}]
  (format "create %s?%s (resolved to id %s)" type
          (clauses->query-params if-none-exist) id))

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

(defmulti verify-tx-cmd
  "Verifies one transaction command. Returns `res` with added index entries and
  statistics of the transaction outcome.

  Throws an anomaly on conflicts."
  {:arglists '([search-param-registry db-before t res cmd])}
  (fn [_search-param-registry _db-before _t _res {:keys [op]}] op))

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

(defmethod verify-tx-cmd "create"
  [_search-param-registry db-before t res {:keys [type id hash refs]}]
  (log/trace (verify-tx-cmd-create-msg type id))
  (with-open [_ (prom/timer duration-seconds "verify-create")]
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

(defmethod verify-tx-cmd "put"
  [_search-param-registry db-before t res
   {:keys [type id hash if-match if-none-match refs] :as tx-cmd}]
  (log/trace (verify-tx-cmd-put-msg type id (u/to-seq if-match) if-none-match))
  (with-open [_ (prom/timer duration-seconds "verify-put")]
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

(defmethod verify-tx-cmd "keep"
  [_search-param-registry db-before _ res {:keys [type id hash if-match] :as tx-cmd}]
  (log/trace (verify-tx-cmd-keep-msg type id (u/to-seq if-match)))
  (with-open [_ (prom/timer duration-seconds "verify-keep")]
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
  [search-param-registry db type resource-handle]
  (into
   []
   (comp (mapcat #(d/include db resource-handle % "Patient"))
         (map (fn [{:keys [id]}] ["Patient" id])))
   (sr/compartment-resources search-param-registry "Patient" type)))

(defmethod verify-tx-cmd "delete"
  [search-param-registry db-before t res {:keys [type id]}]
  (log/trace "verify-tx-cmd :delete" (str type "/" id))
  (with-open [_ (prom/timer duration-seconds "verify-delete")]
    (let [tid (codec/tid type)
          {:keys [num-changes op] :or {num-changes 0} :as old-resource-handle}
          (d/resource-handle db-before type id)
          refs (some->> old-resource-handle (patient-refs search-param-registry db-before type))]
      (cond->
       (-> (update res :entries into (index-entries tid id t hash/deleted-hash (inc num-changes) :delete refs))
           (update :del-resources conj [type id])
           (update-in [:stats tid :num-changes] inc-0))
        (and op (not (identical? :delete op)))
        (update-in [:stats tid :total] (fnil dec 0))))))

(def ^:private ^:const ^long delete-history-max 100000)

(defn- too-many-history-entries-msg [type id]
  (format "Deleting the history of `%s/%s` failed because there are more than %,d history entries."
          type id delete-history-max))

(defn- too-many-history-entries-anom [type id]
  (ba/conflict
   (too-many-history-entries-msg type id)
   :fhir/issue "too-costly"))

(defn- instance-history [db type id]
  (into [] (comp (drop 1) (take delete-history-max)) (d/instance-history db type id)))

(defn- purge-entry [tid id t rh]
  (rts/index-entries tid id (rh/t rh) (rh/hash rh) (rh/num-changes rh) (rh/op rh) t))

(defn- purge-entries [tid id t instance-history]
  (into [] (mapcat (partial purge-entry tid id t)) instance-history))

(defn- add-delete-history-entries [entries tid id t instance-history]
  (-> (update entries :entries into (purge-entries tid (codec/id-byte-string id) t instance-history))
      (update-in [:stats tid :num-changes] minus-0 (count instance-history))))

(defmethod verify-tx-cmd "delete-history"
  [_ db-before t res {:keys [type id]}]
  (log/trace "verify-tx-cmd :delete-history" (str type "/" id))
  (with-open [_ (prom/timer duration-seconds "verify-delete-history")]
    (let [tid (codec/tid type)
          instance-history (instance-history db-before type id)]
      (cond
        (empty? instance-history) res

        (= delete-history-max (count instance-history))
        (throw-anom (too-many-history-entries-anom type id))

        :else
        (add-delete-history-entries res tid id t instance-history)))))

(defmethod verify-tx-cmd :default
  [_search-param-registry _db-before _t res _tx-cmd]
  res)

(defn- verify-tx-cmds** [search-param-registry db-before t tx-cmds]
  (reduce
   (partial verify-tx-cmd search-param-registry db-before t)
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
  (with-open [_ (prom/timer duration-seconds "type-stats")]
    (into entries (map (partial type-stat-entry snapshot t new-t)) stats)))

(defn- system-stats [{:keys [snapshot t]} new-t stats]
  (with-open [_ (prom/timer duration-seconds "system-stats")]
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

(defn- verify-tx-cmds* [search-param-registry db-before t cmds]
  (when-ok [cmds (expand-conditional-commands db-before cmds)]
    (prom/observe! transaction-sizes (count cmds))
    (ba/try-anomaly
     (detect-duplicate-commands! cmds)
     (let [res (verify-tx-cmds** search-param-registry db-before t cmds)]
       (check-referential-integrity! db-before res cmds)
       (post-process-res db-before t res)))))

(defn verify-tx-cmds
  "Verifies transaction commands. Returns index entries of the transaction
  outcome if it is successful or an anomaly if it fails.

  The `t` is for the new transaction to commit."
  [search-param-registry db-before t cmds]
  (with-open [_ (prom/timer duration-seconds "verify-tx-cmds")
              batch-db-before (d/new-batch-db db-before)]
    (verify-tx-cmds* search-param-registry batch-db-before t cmds)))
