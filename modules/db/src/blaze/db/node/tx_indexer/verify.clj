(ns blaze.db.node.tx-indexer.verify
  (:require
    [blaze.anomaly :refer [throw-anom]]
    [blaze.db.api :as d]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.rts-as-of :as rts]
    [blaze.db.impl.index.system-stats :as system-stats]
    [blaze.db.impl.index.type-stats :as type-stats]
    [blaze.db.kv.spec]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [loom.alg]
    [loom.graph]
    [prometheus.alpha :as prom :refer [defhistogram]]
    [taoensso.timbre :as log])
  (:import
    [clojure.lang ExceptionInfo]))


(defhistogram duration-seconds
  "Durations in transaction indexer."
  {:namespace "blaze"
   :subsystem "db"
   :name "tx_indexer_duration_seconds"}
  (take 16 (iterate #(* 2 %) 0.00001))
  "op")


(defmulti verify-tx-cmd
  "Verifies one transaction command. Returns index entries and statistics of the
  transaction outcome.

  Should either update index entries and statistics or return a reduced value of
  an entry into the :tx-error-index."
  {:arglists '([db-before t res cmd])}
  (fn [_ _ _ {:keys [op]}] op))


(defn- resource-exists? [db-before type id]
  (when-let [{:keys [op]} (d/resource-handle db-before type id)]
    (not (identical? :delete op))))


(defn- throw-referential-integrity-anomaly-delete [[src-type src-id] type id]
  (throw-anom
    ::anom/conflict
    (format "Referential integrity violated. Resource `%s/%s` should be deleted but is referenced from `%s/%s`." type id src-type src-id)))


(defn- throw-referential-integrity-anomaly [type id]
  (throw-anom
    ::anom/conflict
    (format "Referential integrity violated. Resource `%s/%s` doesn't exist." type id)))


(defn- check-referential-integrity
  [db-before {:keys [new-resources del-resources]} source references]
  (doseq [[type id :as reference] references]
    (cond
      (contains? del-resources reference)
      (throw-referential-integrity-anomaly-delete source type id)

      (and (not (contains? new-resources reference))
           (not (resource-exists? db-before type id)))
      (throw-referential-integrity-anomaly type id))))


(defn- format-references [references]
  (->> references
       (map (fn [[type id]] (format "%s/%s" type id)))
       (str/join ", ")))


(defn- verify-tx-cmd-create-msg [type id references]
  (if (seq references)
    (format "verify-tx-cmd :create %s/%s references: %s" type id
            (format-references references))
    (format "verify-tx-cmd :create %s/%s" type id)))


(defn- throw-id-collision-anomaly [type id]
  (throw-anom
    ::anom/conflict
    (format "Resource `%s/%s` already exists and can't be created again." type id)))


(defn- check-id-collision [db-before type id]
  (when (d/resource-handle db-before type id)
    (throw-id-collision-anomaly type id)))


(defn- index-entries [tid id t hash num-changes op]
  (rts/index-entries tid (codec/id-byte-string id) t hash num-changes op))


(defmethod verify-tx-cmd "create"
  [db-before t res {:keys [type id hash refs]}]
  (log/trace (verify-tx-cmd-create-msg type id refs))
  (with-open [_ (prom/timer duration-seconds "verify-create")]
    (check-id-collision db-before type id)
    (check-referential-integrity db-before res [type id] refs)
    (let [tid (codec/tid type)]
      (-> res
          (update :entries into (index-entries tid id t hash 1 :create))
          (update :new-resources conj [type id])
          (update-in [:stats tid :num-changes] (fnil inc 0))
          (update-in [:stats tid :total] (fnil inc 0))))))


(defn- verify-tx-cmd-put-msg [type id matches]
  (if matches
    (format "verify-tx-cmd :put %s/%s matches-t: %d" type id matches)
    (format "verify-tx-cmd :put %s/%s" type id)))


(defn- throw-precondition-failed [if-match type id]
  (throw-anom
    ::anom/conflict
    (format "Precondition `W/\"%d\"` failed on `%s/%s`." if-match type id)
    :http/status 412))


(defmethod verify-tx-cmd "put"
  [db-before t res {:keys [type id hash refs if-match]}]
  (log/trace (verify-tx-cmd-put-msg type id if-match))
  (with-open [_ (prom/timer duration-seconds "verify-put")]
    (check-referential-integrity db-before res [type id] refs)
    (let [tid (codec/tid type)
          {:keys [num-changes] :or {num-changes 0} old-t :t}
          (d/resource-handle db-before type id)]
      (if (or (nil? if-match) (= if-match old-t))
        (cond->
          (-> res
              (update :entries into (index-entries tid id t hash (inc num-changes) :put))
              (update :new-resources conj [type id])
              (update-in [:stats tid :num-changes] (fnil inc 0)))
          (nil? old-t)
          (update-in [:stats tid :total] (fnil inc 0)))
        (throw-precondition-failed if-match type id)))))


(defmethod verify-tx-cmd "delete"
  [db-before t res {:keys [type id hash]}]
  (log/trace "verify-tx-cmd :delete" (str type "/" id))
  (with-open [_ (prom/timer duration-seconds "verify-delete")]
    (let [tid (codec/tid type)
          {:keys [num-changes] :or {num-changes 0}}
          (d/resource-handle db-before type id)]
      (-> res
          (update :entries into (index-entries tid id t hash (inc num-changes) :delete))
          (update :del-resources conj [type id])
          (update-in [:stats tid :num-changes] (fnil inc 0))
          (update-in [:stats tid :total] (fnil dec 0))))))


(defn- verify-tx-cmds** [db-before t tx-cmds]
  (reduce
    (partial verify-tx-cmd db-before t)
    {:entries []
     :new-resources #{}
     :del-resources #{}}
    tx-cmds))


(defn- type-stat-entry!
  [iter t new-t [tid increments]]
  (let [current-stats (type-stats/get! iter tid t)]
    (type-stats/index-entry tid new-t (merge-with + current-stats increments))))


(defn- conj-type-stats [entries {{:keys [snapshot t]} :context} new-t stats]
  (with-open [_ (prom/timer duration-seconds "type-stats")
              iter (type-stats/new-iterator snapshot)]
    (into entries (map #(type-stat-entry! iter t new-t %)) stats)))


(defn- system-stats [{{:keys [snapshot t]} :context} new-t stats]
  (with-open [_ (prom/timer duration-seconds "system-stats")
              iter (system-stats/new-iterator snapshot)]
    (let [current-stats (system-stats/get! iter t)]
      (system-stats/index-entry new-t (apply merge-with + current-stats (vals stats))))))


(defn- post-process-res [db-before t {:keys [entries stats]}]
  (cond-> (conj-type-stats entries db-before t stats)
    stats
    (conj (system-stats db-before t stats))))


(defn- verify-tx-cmds* [db-before t tx-cmds]
  (try
    (let [res (verify-tx-cmds** db-before t tx-cmds)]
      (post-process-res db-before t res))
    (catch ExceptionInfo e
      (if-let [ex-data (ex-data e)]
        (if (::anom/category ex-data)
          ex-data
          (throw e))
        (throw e)))))


(defn verify-tx-cmds
  "Verifies transaction commands. Returns index entries of the transaction
  outcome which can be successful or fail. Can be put into `kv-store` without
  further processing.

  The `t` and `tx-instant` are for the new transaction to commit."
  [db-before t tx-cmds]
  (with-open [_ (prom/timer duration-seconds "verify-tx-cmds")
              batch-db-before (d/new-batch-db db-before)]
    (verify-tx-cmds* batch-db-before t tx-cmds)))
