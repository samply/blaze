(ns blaze.db.indexer.tx
  (:require
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index :as index]
    [blaze.db.indexer :as indexer]
    [blaze.db.kv :as kv]
    [blaze.module :refer [reg-collector]]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [manifold.deferred :as md]
    [manifold.time :as time]
    [prometheus.alpha :as prom :refer [defhistogram]]
    [taoensso.nippy :as nippy]
    [taoensso.timbre :as log])
  (:import
    [java.io Closeable]))


(set! *warn-on-reflection* true)


(defhistogram tx-indexer-duration-seconds
  "Durations in transaction indexer."
  {:namespace "blaze"
   :subsystem "db"}
  (mapcat #(list % (* 2.5 %) (* 5 %) (* 7.5 %)) (take 6 (iterate #(* 10 %) 0.00001)))
  "op")


(defmulti verify-tx-cmd
  "Verifies one transaction command. Returns index entries and statistics of the
  transaction outcome.

  Should either update index entries and statistics or return a reduced value of
  an entry into the :tx-error-index."
  {:arglists '([resource-as-of-index t res cmd])}
  (fn [_ _ _ [op]] op))


(defn- entries [tid id t hash num-changes op]
  (assert num-changes)
  [[:resource-as-of-index (codec/resource-as-of-key tid id t)
    (codec/resource-as-of-value hash (codec/state (inc num-changes) op))]
   [:type-as-of-index (codec/type-as-of-key tid t id)
    codec/empty-byte-array]
   [:system-as-of-index (codec/system-as-of-key t tid id)
    codec/empty-byte-array]])


(defmethod verify-tx-cmd :create
  [_ t res [_ type id hash]]
  (log/trace "verify-tx-cmd :create" type id)
  (with-open [_ (prom/timer tx-indexer-duration-seconds "verify-tx-cmd-create")]
    (-> res
        (update :entries into (entries (codec/tid type) (codec/id-bytes id) t hash 0 :create))
        (update-in [:stats type :num-changes] (fnil inc 0))
        (update-in [:stats type :total] (fnil inc 0)))))


(defmethod verify-tx-cmd :put
  [resource-as-of-iter t res [_ type id new-hash matches]]
  (log/trace "verify-tx-cmd :put" type id)
  (with-open [_ (prom/timer tx-indexer-duration-seconds "verify-tx-cmd-put")]
    (let [tid (codec/tid type)
          id-bytes (codec/id-bytes id)
          [state old-t] (index/state-t resource-as-of-iter tid id-bytes t)
          num-changes (or (some-> state codec/state->num-changes) 0)]
      (if (or (nil? matches) (= matches old-t))
        (cond->
          (-> res
              (update :entries into (entries tid id-bytes t new-hash num-changes :put))
              (update-in [:stats type :num-changes] (fnil inc 0)))
          (nil? old-t)
          (update-in [:stats type :total] (fnil inc 0)))
        (reduced
          (index/tx-error-entries
            t
            {::anom/category ::anom/conflict
             ::anom/message (format "put mismatch for %s/%s" type id)}))))))


(defmethod verify-tx-cmd :delete
  [resource-as-of-iter t res [_ type id hash]]
  (log/trace "verify-tx-cmd :delete" type id)
  (with-open [_ (prom/timer tx-indexer-duration-seconds "verify-tx-cmd-delete")]
    (let [tid (codec/tid type)
          id-bytes (codec/id-bytes id)
          [state] (index/state-t resource-as-of-iter tid id-bytes t)
          num-changes (or (some-> state codec/state->num-changes) 0)]
      (-> res
          (update :entries into (entries tid id-bytes t hash num-changes :delete))
          (update-in [:stats type :num-changes] (fnil inc 0))
          (update-in [:stats type :total] (fnil dec 0))))))


(defn- resource-as-of-iter ^Closeable [snapshot]
  (kv/new-iterator snapshot :resource-as-of-index))


(defn- verify-tx-cmds* [snapshot t tx-instant tx-cmds]
  (with-open [i (resource-as-of-iter snapshot)]
    (reduce
      (partial verify-tx-cmd i t)
      {:entries (index/tx-success-entries t tx-instant)}
      tx-cmds)))


(defn- type-stat-entry
  [i t [type {:keys [total num-changes] :or {total 0 num-changes 0}}]]
  (let [tid (codec/tid type)
        old-type-stats (index/type-stats i tid t)
        total (+ (or (some-> old-type-stats codec/type-stats-value->total) 0) total)
        num-changes (+ (or (some-> old-type-stats codec/type-stats-value->num-changes) 0) num-changes)]
    [:type-stats-index
     (codec/type-stats-key tid t)
     (codec/type-stats-value total num-changes)]))


(defn- type-stats-iter ^Closeable [snapshot]
  (kv/new-iterator snapshot :type-stats-index))


(defn- conj-type-stats [entries snapshot t stats]
  (with-open [_ (prom/timer tx-indexer-duration-seconds "type-stats")
              i (type-stats-iter snapshot)]
    (into entries (map (partial type-stat-entry i t)) stats)))


(defn- system-stats-iter ^Closeable [snapshot]
  (kv/new-iterator snapshot :system-stats-index))


(defn- system-total [init stats]
  (transduce (map (comp #(or % 0) :total)) + init (vals stats)))


(defn- system-num-changes [init stats]
  (transduce (map :num-changes) + init (vals stats)))


(defn- system-stats [snapshot t stats]
  (with-open [_ (prom/timer tx-indexer-duration-seconds "system-stats")
              i (system-stats-iter snapshot)]
    (let [old-system-stats (index/system-stats i t)
          total (or (some-> old-system-stats codec/system-stats-value->total) 0)
          num-changes (or (some-> old-system-stats codec/system-stats-value->num-changes) 0)]
      [:system-stats-index
       (codec/system-stats-key t)
       (codec/system-stats-value
         (system-total total stats)
         (system-num-changes num-changes stats))])))


(defn- post-process-res [snapshot t {:keys [entries stats]}]
  (-> (conj-type-stats entries snapshot t stats)
      (conj (system-stats snapshot t stats))))


(defn verify-tx-cmds
  "Verifies transaction commands. Returns index entries of the transaction
  outcome which can be successful or fail. Can be put into `kv-store` without
  further processing.

  The `t` and `tx-instant` are for the new transaction to commit."
  [kv-store t tx-instant tx-cmds]
  (with-open [_ (prom/timer tx-indexer-duration-seconds "verify-tx-cmds")
              snapshot (kv/new-snapshot kv-store)]
    (let [res (verify-tx-cmds* snapshot t tx-instant tx-cmds)]
      (if (vector? res)
        res
        (post-process-res snapshot t res)))))


(defn- find-tx-result [kv-store t]
  (if (kv/get kv-store :tx-success-index (codec/t-key t))
    (md/success-deferred t)
    (when-let [anom (kv/get kv-store :tx-error-index (codec/t-key t))]
      (md/error-deferred (nippy/fast-thaw anom)))))


(deftype TxIndexer [kv-store tx-result-polling-interval]
  indexer/Tx
  (last-t [_]
    (with-open [snapshot (kv/new-snapshot kv-store)
                i (kv/new-iterator snapshot :tx-success-index)]
      (or (some-> (kv/seek-to-first i) (codec/decode-t-key)) 0)))

  (-submit-tx [_ t tx-instant tx-cmds]
    (with-open [_ (prom/timer tx-indexer-duration-seconds "submit-tx")]
      (kv/put kv-store (verify-tx-cmds kv-store t tx-instant tx-cmds))))

  (tx-result [_ t]
    (md/loop [res (find-tx-result kv-store t)]
      (if res
        res
        (-> (time/in tx-result-polling-interval #(find-tx-result kv-store t))
            (md/chain' md/recur))))))


(defn init-tx-indexer [kv-store]
  (->TxIndexer kv-store 100))


(defmethod ig/init-key ::indexer/tx
  [_ {:keys [kv-store]}]
  (log/info "Open transaction indexer.")
  (init-tx-indexer kv-store))


(reg-collector ::duration-seconds
  tx-indexer-duration-seconds)
