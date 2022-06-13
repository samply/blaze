(ns blaze.db.node.tx-indexer.verify
  (:require
    [blaze.anomaly :as ba :refer [throw-anom]]
    [blaze.db.api :as d]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.db.impl.index.resource-id :as ri]
    [blaze.db.impl.index.rts-as-of :as rts]
    [blaze.db.impl.index.system-stats :as system-stats]
    [blaze.db.impl.index.type-stats :as type-stats]
    [blaze.db.kv.spec]
    [blaze.fhir.hash :as hash]
    [clojure.string :as str]
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


(defmulti resolve-id
  "Resolves all identifiers a conditional transaction command.

  Throws an anomaly on conflicts."
  {:arglists '([db-before cmd])}
  (fn [_ {:keys [op]}] op))


(defn- existing-resource-handles [db type clauses]
  (into [] (take 2) (d/type-query db type clauses)))


(defn- clauses->query-params [clauses]
  (->> clauses
       (map (fn [[param & values]] (str param "=" (str/join "," values))))
       (str/join "&")))


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


(defmethod resolve-id "create"
  [db-before {:keys [type if-none-exist] :as cmd}]
  (let [[h1 h2] (some->> if-none-exist (existing-resource-handles db-before type))]
    (cond
      h2 (throw-anom (multiple-existing-resources-anom type if-none-exist [h1 h2]))
      h1 (assoc cmd :op "hold" :id (rh/id h1))
      :else cmd)))


(defmethod resolve-id :default
  [_ cmd]
  cmd)


(defn resolve-ids
  "Resolves all identifiers from conditional transaction commands.

  Throws an anomaly on conflicts."
  [db-before cmds]
  (mapv #(resolve-id db-before %) cmds))


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
  {:arglists '([db-before t res cmd])}
  (fn [_db-before _t _idx _res {:keys [op]}] op))


(defn- verify-tx-cmd-create-msg [type id]
  (format "verify-tx-cmd :create %s/%s" type id))


(defn- id-collision-msg [type id]
  (format "Resource `%s/%s` already exists and can't be created again." type id))


(defn- check-id-collision! [db type id]
  (when (d/resource-handle db type id)
    (throw-anom (ba/conflict (id-collision-msg type id)))))


(def ^:private inc-0 (fnil inc 0))


(defmethod verify-tx-cmd "create"
  [db-before t idx res {:keys [type id hash] :as cmd}]
  (log/trace (verify-tx-cmd-create-msg type id))
  (with-open [_ (prom/timer duration-seconds "verify-create")]
    (check-id-collision! db-before type id)
    (let [tid (codec/tid type)
          did (codec/did t idx)]
      (-> (update res :entries into (rts/index-entries tid did t hash 1 :create id))
          (update :entries conj (ri/index-entry tid id did))
          (update :new-resources conj [type id])
          (update :cmds conj (assoc cmd :did did))
          (update-in [:stats tid :num-changes] inc-0)
          (update-in [:stats tid :total] inc-0)))))


(defn- verify-tx-cmd-put-msg [type id matches]
  (if matches
    (format "verify-tx-cmd :put %s/%s matches-t: %d" type id matches)
    (format "verify-tx-cmd :put %s/%s" type id)))


(defn- precondition-failed-msg [if-match type id]
  (format "Precondition `W/\"%d\"` failed on `%s/%s`." if-match type id))


(defn- precondition-failed-anomaly [if-match type id]
  (ba/conflict (precondition-failed-msg if-match type id) :http/status 412))


(defmethod verify-tx-cmd "put"
  [db-before t idx res {:keys [type id hash if-match] :as cmd}]
  (log/trace (verify-tx-cmd-put-msg type id if-match))
  (with-open [_ (prom/timer duration-seconds "verify-put")]
    (let [tid (codec/tid type)
          {:keys [did num-changes op] :or {did (codec/did t idx) num-changes 0}
           old-t :t} (d/resource-handle db-before type id)]
      (if (or (nil? if-match) (= if-match old-t))
        (cond->
          (-> (update res :entries into (rts/index-entries tid did t hash (inc num-changes) :put id))
              (update :new-resources conj [type id])
              (update :cmds conj (assoc cmd :did did))
              (update-in [:stats tid :num-changes] inc-0))
          (nil? old-t)
          (update :entries conj (ri/index-entry tid id did))
          (or (nil? old-t) (identical? :delete op))
          (update-in [:stats tid :total] inc-0))
        (throw-anom (precondition-failed-anomaly if-match type id))))))


(defmethod verify-tx-cmd "delete"
  [db-before t idx res {:keys [type id]}]
  (log/trace "verify-tx-cmd :delete" (str type "/" id))
  (with-open [_ (prom/timer duration-seconds "verify-delete")]
    (let [tid (codec/tid type)
          {:keys [did num-changes op] :or {did (codec/did t idx) num-changes 0}}
          (d/resource-handle db-before type id)]
      (cond->
        (-> (update res :entries into (rts/index-entries tid did t hash/deleted-hash (inc num-changes) :delete id))
            (update :del-resources conj [type id])
            (update-in [:stats tid :num-changes] inc-0))
        (nil? op)
        (update :entries conj (ri/index-entry tid id did))
        (and op (not (identical? :delete op)))
        (update-in [:stats tid :total] (fnil dec 0))))))


(defmethod verify-tx-cmd :default
  [_db-before _t _idx res _tx-cmd]
  res)


(defn- verify-tx-cmds** [db-before t tx-cmds]
  (let [idx (volatile! -1)]
    (reduce
      (fn [res tx-cmd]
        (verify-tx-cmd db-before t (vswap! idx inc) res tx-cmd))
      {:entries []
       :cmds []
       :new-resources #{}
       :del-resources #{}}
      tx-cmds)))


(def ^:private empty-stats
  {:total 0 :num-changes 0})


(defn- type-stat-entry!
  [iter t new-t [tid increments]]
  (let [current-stats (or (type-stats/get! iter tid t) empty-stats)]
    (type-stats/index-entry tid new-t (merge-with + current-stats increments))))


(defn- conj-type-stats [entries {{:keys [snapshot t]} :context} new-t stats]
  (with-open [_ (prom/timer duration-seconds "type-stats")
              iter (type-stats/new-iterator snapshot)]
    (into entries (map #(type-stat-entry! iter t new-t %)) stats)))


(defn- system-stats [{{:keys [snapshot t]} :context} new-t stats]
  (with-open [_ (prom/timer duration-seconds "system-stats")
              iter (system-stats/new-iterator snapshot)]
    (let [current-stats (or (system-stats/get! iter t) empty-stats)]
      (system-stats/index-entry new-t (apply merge-with + current-stats (vals stats))))))


(defn- post-process-res [db-before t {:keys [entries stats cmds]}]
  [(cond-> (conj-type-stats entries db-before t stats)
     stats
     (conj (system-stats db-before t stats)))
   cmds])


(defn- resource-exists? [db type id]
  (when-let [{:keys [op]} (d/resource-handle db type id)]
    (not (identical? :delete op))))


(defn- ref-integrity-del-msg [src-type src-id type id]
  (format "Referential integrity violated. Resource `%s/%s` should be deleted but is referenced from `%s/%s`."
          type id src-type src-id))


(defn- ref-integrity-del-anom [src-type src-id type id]
  (ba/conflict (ref-integrity-del-msg src-type src-id type id)))


(defn- ref-integrity-msg [type id]
  (format "Referential integrity violated. Resource `%s/%s` doesn't exist."
          type id))


(defn- check-referential-integrity*!
  [db new-resources del-resources src-type src-id references]
  (run!
    (fn [[type id :as reference]]
      (cond
        (contains? del-resources reference)
        (throw-anom (ref-integrity-del-anom src-type src-id type id))

        (and (not (contains? new-resources reference))
             (not (resource-exists? db type id)))
        (throw-anom (ba/conflict (ref-integrity-msg type id)))))
    references))


(defn- check-referential-integrity!
  [db {:keys [new-resources del-resources]} cmds]
  (run!
    (fn [{:keys [type id refs]}]
      (when refs
        (check-referential-integrity*!
          db new-resources del-resources type id refs)))
    cmds))


(defn- verify-tx-cmds* [db-before t cmds]
  (ba/try-anomaly
    (let [cmds (resolve-ids db-before cmds)]
      (detect-duplicate-commands! cmds)
      (let [res (verify-tx-cmds** db-before t cmds)]
        (check-referential-integrity! db-before res cmds)
        (post-process-res db-before t res)))))


(defn verify-tx-cmds
  "Verifies transaction commands. Returns index entries of the transaction
  outcome if it is successful or an anomaly if it fails.

  The `t` is for the new transaction to commit."
  [db-before t cmds]
  (with-open [_ (prom/timer duration-seconds "verify-tx-cmds")
              batch-db-before (d/new-batch-db db-before)]
    (verify-tx-cmds* batch-db-before t cmds)))
