(ns blaze.db.node.tx-indexer.verify
  (:require
    [blaze.anomaly :as ba :refer [when-ok]]
    [blaze.anomaly-spec]
    [blaze.byte-string :as bs]
    [blaze.db.api :as d]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.rts-as-of :as rts]
    [blaze.db.impl.index.system-stats :as system-stats]
    [blaze.db.impl.index.type-stats :as type-stats]
    [blaze.db.impl.protocols :as p]
    [blaze.db.kv.spec]
    [blaze.db.node.tx-indexer.verify.impl :as impl]
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


(defmulti verify-tx-cmd
  "Verifies one transaction command. Returns `res` with added index entries and
  statistics of the transaction outcome.

  Throws an anomaly on conflicts."
  {:arglists '([db-before t res cmd])}
  (fn [_ _ _ {:keys [op]}] op))


(defn- verify-tx-cmd-create-msg [type id]
  (format "verify-tx-cmd :create %s/%s" type id))


(defn- index-entries [tid id t hash num-changes op]
  (rts/index-entries tid (codec/id-byte-string id) t hash num-changes op))


(defmethod verify-tx-cmd "create"
  [_ t res {:keys [type id hash]}]
  (log/trace (verify-tx-cmd-create-msg type id))
  (with-open [_ (prom/timer duration-seconds "verify-create")]
    (let [tid (codec/tid type)]
      (-> (update res :entries into (index-entries tid id t hash 1 :create))
          (update-in [:stats tid :num-changes] (fnil inc 0))
          (update-in [:stats tid :total] (fnil inc 0))))))


(defn- verify-tx-cmd-put-msg [type id matches]
  (if matches
    (format "verify-tx-cmd :put %s/%s matches-t: %d" type id matches)
    (format "verify-tx-cmd :put %s/%s" type id)))


(defmethod verify-tx-cmd "put"
  [db-before t res {:keys [type id hash if-match]}]
  (log/trace (verify-tx-cmd-put-msg type id if-match))
  (with-open [_ (prom/timer duration-seconds "verify-put")]
    (let [tid (codec/tid type)
          {:keys [num-changes op] :or {num-changes 0} old-t :t}
          (d/resource-handle db-before type id)]
      (cond->
        (-> (update res :entries into (index-entries tid id t hash (inc num-changes) :put))
            (update-in [:stats tid :num-changes] (fnil inc 0)))
        (or (nil? old-t) (identical? :delete op))
        (update-in [:stats tid :total] (fnil inc 0))))))


(def ^:private deleted-hash
  "The hash of a deleted version of a resource."
  (bs/from-byte-array (byte-array 32)))


(defmethod verify-tx-cmd "delete"
  [db-before t res {:keys [type id]}]
  (log/trace "verify-tx-cmd :delete" (str type "/" id))
  (with-open [_ (prom/timer duration-seconds "verify-delete")]
    (let [tid (codec/tid type)
          {:keys [num-changes op] :or {num-changes 0}}
          (d/resource-handle db-before type id)]
      (cond->
        (-> (update res :entries into (index-entries tid id t deleted-hash (inc num-changes) :delete))
            (update-in [:stats tid :num-changes] (fnil inc 0)))
        (and op (not (identical? :delete op)))
        (update-in [:stats tid :total] (fnil dec 0))))))


(defmethod verify-tx-cmd :default
  [_ _ res _]
  res)


(defn- tx-entries** [db-before t tx-cmds]
  (reduce
    (partial verify-tx-cmd db-before t)
    {:entries []}
    tx-cmds))


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


(defn- post-process-res [db-before t {:keys [entries stats]}]
  (cond-> (conj-type-stats entries db-before t stats)
    stats
    (conj (system-stats db-before t stats))))


(defn- verify-commands* [db-before cmds]
  (when-ok [cmds (impl/resolve-conditional-create db-before cmds)
            _ (impl/detect-duplicate-commands cmds)
            cmds (filterv (comp not #{"hold"} :op) cmds)
            _ (impl/verify-commands db-before cmds)
            db (p/-with db-before cmds)
            cmds (impl/resolve-conditional-refs db cmds)
            _ (impl/check-referential-integrity db cmds)]
    cmds))


(defn verify-commands
  "Verifies transaction commands."
  [db-before cmds]
  (with-open [_ (prom/timer duration-seconds "verify-tx-cmds")
              batch-db-before (d/new-batch-db db-before)]
    (verify-commands* batch-db-before cmds)))


(defn- tx-entries* [db t cmds]
  (let [res (tx-entries** db t cmds)]
    (post-process-res db t res)))


(defn tx-entries
  "Returns index entries of the transaction outcome if it is successful or an
  anomaly if it fails.

  The `t` is for the new transaction to commit."
  [db-before t cmds]
  (with-open [_ (prom/timer duration-seconds "tx-entries")
              batch-db-before (d/new-batch-db db-before)]
    (tx-entries* batch-db-before t cmds)))
