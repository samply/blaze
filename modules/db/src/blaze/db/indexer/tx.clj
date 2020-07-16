(ns blaze.db.indexer.tx
  (:require
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-as-of :as resource-as-of]
    [blaze.db.impl.index.system-stats :as system-stats]
    [blaze.db.impl.index.type-stats :as type-stats]
    [blaze.db.indexer :as indexer]
    [blaze.db.kv :as kv]
    [blaze.module :refer [reg-collector]]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
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


(defn- resource-exists? [raoi type id t]
  (let [[_ state] (resource-as-of/hash-state-t raoi (codec/tid type) (codec/id-bytes id) t)]
    (and (some? state) (not (identical? :delete (codec/state->op state))))))


(defn- referential-integrity-anomaly-delete [[src-type src-id] type id]
  {::anom/category ::anom/conflict
   ::anom/message (format "Referential integrity violated. Resource `%s/%s` should be deleted but is referenced from `%s/%s`." type id src-type src-id)})


(defn- referential-integrity-anomaly [type id]
  {::anom/category ::anom/conflict
   ::anom/message (format "Referential integrity violated. Resource `%s/%s` doesn't exist." type id)})


(defn- check-referential-integrity
  [raoi t {:keys [new-resources del-resources]} source references]
  (reduce
    (fn [ret [type id :as reference]]
      (cond
        (contains? del-resources reference)
        (reduced (referential-integrity-anomaly-delete source type id))

        (or (contains? new-resources reference)
            (resource-exists? raoi type id t))
        ret

        :else
        (reduced (referential-integrity-anomaly type id))))
    nil
    references))


(defn- entries [tid id t hash num-changes op]
  (let [value (codec/resource-as-of-value hash (codec/state (inc num-changes) op))]
    [[:resource-as-of-index (codec/resource-as-of-key tid id t) value]
     [:type-as-of-index (codec/type-as-of-key tid t id) value]
     [:system-as-of-index (codec/system-as-of-key t tid id) value]]))


(defn- format-references [references]
  (->> references
       (map (fn [[type id]] (format "%s/%s" type id)))
       (str/join ", ")))


(defn- verify-tx-cmd-create-msg [type id references]
  (if (seq references)
    (format "verify-tx-cmd :create %s/%s references: %s" type id
            (format-references references))
    (format "verify-tx-cmd :create %s/%s" type id)))


(defmethod verify-tx-cmd :create
  [raoi t res [_ type id hash references]]
  (log/trace (verify-tx-cmd-create-msg type id references))
  (with-open [_ (prom/timer tx-indexer-duration-seconds "verify-tx-cmd-create")]
    (let [check-res (check-referential-integrity raoi t res [type id] references)]
      (if (::anom/category check-res)
        (reduced (codec/tx-error-entries t check-res))
        (let [tid (codec/tid type)]
          (-> res
              (update :entries into (entries (codec/tid type) (codec/id-bytes id) t hash 0 :create))
              (update :new-resources conj [type id])
              (update-in [:stats tid :num-changes] (fnil inc 0))
              (update-in [:stats tid :total] (fnil inc 0))))))))


(defn- verify-tx-cmd-put-msg [type id matches]
  (if matches
    (format "verify-tx-cmd :put %s/%s matches-t: %d" type id matches)
    (format "verify-tx-cmd :put %s/%s" type id)))


(defmethod verify-tx-cmd :put
  [raoi t res [_ type id new-hash references matches]]
  (log/trace (verify-tx-cmd-put-msg type id matches))
  (with-open [_ (prom/timer tx-indexer-duration-seconds "verify-tx-cmd-put")]
    (let [check-res (check-referential-integrity raoi t res [type id] references)]
      (if (::anom/category check-res)
        (reduced (codec/tx-error-entries t check-res))
        (let [tid (codec/tid type)
              id-bytes (codec/id-bytes id)
              [_ state old-t] (resource-as-of/hash-state-t raoi tid id-bytes t)
              num-changes (or (some-> state codec/state->num-changes) 0)]
          (if (or (nil? matches) (= matches old-t))
            (cond->
              (-> res
                  (update :entries into (entries tid id-bytes t new-hash num-changes :put))
                  (update :new-resources conj [type id])
                  (update-in [:stats tid :num-changes] (fnil inc 0)))
              (nil? old-t)
              (update-in [:stats tid :total] (fnil inc 0)))
            (reduced
              (codec/tx-error-entries
                t
                {::anom/category ::anom/conflict
                 ::anom/message (format "Precondition `W/\"%d\"` failed on `%s/%s`." matches type id)
                 :http/status 412}))))))))


(defmethod verify-tx-cmd :delete
  [raoi t res [_ type id hash]]
  (log/trace "verify-tx-cmd :delete" (str type "/" id))
  (with-open [_ (prom/timer tx-indexer-duration-seconds "verify-tx-cmd-delete")]
    (let [tid (codec/tid type)
          id-bytes (codec/id-bytes id)
          [_ state] (resource-as-of/hash-state-t raoi tid id-bytes t)
          num-changes (or (some-> state codec/state->num-changes) 0)]
      (-> res
          (update :entries into (entries tid id-bytes t hash num-changes :delete))
          (update :del-resources conj [type id])
          (update-in [:stats tid :num-changes] (fnil inc 0))
          (update-in [:stats tid :total] (fnil dec 0))))))


(defn- raoi ^Closeable [snapshot]
  (kv/new-iterator snapshot :resource-as-of-index))


(defn- verify-tx-cmds* [snapshot t tx-instant tx-cmds]
  (with-open [raoi (raoi snapshot)]
    (reduce
      (partial verify-tx-cmd raoi t)
      {:entries (codec/tx-success-entries t tx-instant)
       :new-resources #{}
       :del-resources #{}}
      tx-cmds)))


(defn- type-stat-entry!
  [iter t [tid increments]]
  (let [current-stats (type-stats/get! iter tid t)]
    (type-stats/entry tid t (merge-with + current-stats increments))))


(defn- conj-type-stats [entries snapshot t stats]
  (with-open [_ (prom/timer tx-indexer-duration-seconds "type-stats")
              iter (type-stats/new-iterator snapshot)]
    (into entries (map #(type-stat-entry! iter t %)) stats)))


(defn- system-stats [snapshot t stats]
  (with-open [_ (prom/timer tx-indexer-duration-seconds "system-stats")
              iter (system-stats/new-iterator snapshot)]
    (let [current-stats (system-stats/get! iter t)]
      (system-stats/entry t (apply merge-with + current-stats (vals stats))))))


(defn- post-process-res [snapshot t {:keys [entries stats]}]
  (cond-> (conj-type-stats entries snapshot t stats)
    stats
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
  (-last-t [_]
    (with-open [snapshot (kv/new-snapshot kv-store)
                iter (kv/new-iterator snapshot :tx-success-index)]
      (kv/seek-to-first! iter)
      (if (kv/valid? iter)
        (codec/decode-t-key (kv/key iter))
        0)))

  (-index-tx [_ t tx-instant tx-cmds]
    (with-open [_ (prom/timer tx-indexer-duration-seconds "submit-tx")]
      (kv/put kv-store (verify-tx-cmds kv-store t tx-instant tx-cmds))))

  (-tx-result [_ t]
    (md/loop [res (find-tx-result kv-store t)]
      (if res
        res
        (-> (time/in tx-result-polling-interval #(find-tx-result kv-store t))
            (md/chain' md/recur))))))


(defn init-tx-indexer [kv-store]
  (->TxIndexer kv-store 100))


(defmethod ig/pre-init-spec ::indexer/tx-indexer [_]
  (s/keys :req-un [:blaze.db/kv-store]))


(defmethod ig/init-key ::indexer/tx-indexer
  [_ {:keys [kv-store]}]
  (log/info "Init transaction indexer")
  (init-tx-indexer kv-store))


(reg-collector ::duration-seconds
  tx-indexer-duration-seconds)
