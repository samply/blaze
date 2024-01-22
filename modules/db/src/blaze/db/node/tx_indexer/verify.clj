(ns blaze.db.node.tx-indexer.verify
  (:require
   [blaze.anomaly :as ba :refer [throw-anom]]
   [blaze.db.api :as d]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.index.rts-as-of :as rts]
   [blaze.db.impl.index.system-stats :as system-stats]
   [blaze.db.impl.index.type-stats :as type-stats]
   [blaze.db.kv.spec]
   [blaze.fhir.hash :as hash]
   [blaze.util :as u]
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
  (fn [_db-before _t _res {:keys [op]}] op))

(defn- verify-tx-cmd-create-msg [type id]
  (format "verify-tx-cmd :create %s/%s" type id))

(defn- id-collision-msg [type id]
  (format "Resource `%s/%s` already exists and can't be created again." type id))

(defn- check-id-collision! [db type id]
  (when (d/resource-handle db type id)
    (throw-anom (ba/conflict (id-collision-msg type id)))))

(defn- index-entries [tid id t hash num-changes op]
  (rts/index-entries tid (codec/id-byte-string id) t hash num-changes op))

(def ^:private inc-0 (fnil inc 0))

(defmethod verify-tx-cmd "create"
  [db-before t res {:keys [type id hash]}]
  (log/trace (verify-tx-cmd-create-msg type id))
  (with-open [_ (prom/timer duration-seconds "verify-create")]
    (check-id-collision! db-before type id)
    (let [tid (codec/tid type)]
      (-> (update res :entries into (index-entries tid id t hash 1 :create))
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
  [db-before t res {:keys [type id hash if-match if-none-match] :as tx-cmd}]
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
         (-> (update res :entries into (index-entries tid id t hash (inc num-changes) :put))
             (update :new-resources conj [type id])
             (update-in [:stats tid :num-changes] inc-0))
          (or (nil? old-t) (identical? :delete op))
          (update-in [:stats tid :total] inc-0))))))

(defn- verify-tx-cmd-keep-msg [type id if-match]
  (if if-match
    (format "verify-tx-cmd :keep %s/%s if-match: %s" type id (print-etags if-match))
    (format "verify-tx-cmd :keep %s/%s" type id)))

(defmethod verify-tx-cmd "keep"
  [db-before _ res {:keys [type id hash if-match] :as tx-cmd}]
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

(defmethod verify-tx-cmd "delete"
  [db-before t res {:keys [type id]}]
  (log/trace "verify-tx-cmd :delete" (str type "/" id))
  (with-open [_ (prom/timer duration-seconds "verify-delete")]
    (let [tid (codec/tid type)
          {:keys [num-changes op] :or {num-changes 0}}
          (d/resource-handle db-before type id)]
      (cond->
       (-> (update res :entries into (index-entries tid id t hash/deleted-hash (inc num-changes) :delete))
           (update :del-resources conj [type id])
           (update-in [:stats tid :num-changes] inc-0))
        (and op (not (identical? :delete op)))
        (update-in [:stats tid :total] (fnil dec 0))))))

(defmethod verify-tx-cmd :default
  [_db-before _t res _tx-cmd]
  res)

(defn- verify-tx-cmds** [db-before t tx-cmds]
  (reduce
   (partial verify-tx-cmd db-before t)
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
