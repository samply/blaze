(ns blaze.db.tx-log.local
  "A a transaction log which is suitable only for single node setups.

  The transaction log only contains the transaction commands indexed by t. The
  resources are passed directly to the resource-indexer. So restoring the
  database from the transaction logs key-value store alone is not possible."
  (:require
    [blaze.db.impl.codec :as codec]
    [blaze.db.indexer :as indexer]
    [blaze.db.tx-log :as tx-log]
    [blaze.db.tx-log.local.references :as references]
    [blaze.executors :as ex]
    [blaze.module :refer [reg-collector]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [loom.alg]
    [loom.graph]
    [manifold.deferred :as md]
    [prometheus.alpha :as prom :refer [defhistogram]]
    [taoensso.timbre :as log])
  (:import
    [java.time Clock Instant]))


(set! *warn-on-reflection* true)


(defhistogram tx-log-indexer-duration-seconds
  "Durations in transaction log."
  {:namespace "blaze"
   :subsystem "db"}
  (mapcat #(list % (* 2.5 %) (* 5 %) (* 7.5 %)) (take 6 (iterate #(* 10 %) 0.00001)))
  "op")


(defmulti extract-type-id first)


(defmethod extract-type-id :create
  [[_ {:keys [resourceType id]}]]
  [resourceType id])


(defmethod extract-type-id :put
  [[_ {:keys [resourceType id]}]]
  [resourceType id])


(defmethod extract-type-id :delete
  [[_ type id]]
  [type id])


(defn- duplicate-resource-anomaly [[type id]]
  {::anom/category ::anom/incorrect
   ::anom/message (format "Duplicate resource `%s/%s`." type id)
   :fhir/issue "invariant"})


(defn- validate-ops
  "Validates transactions operators for any duplicate resource.

  Returns an anomaly if their is any duplicate resource."
  [tx-ops]
  (transduce
    (map extract-type-id)
    (completing
      (fn [index type-id]
        (if (contains? index type-id)
          (reduced (duplicate-resource-anomaly type-id))
          (conj index type-id))))
    #{}
    tx-ops))


(defmulti prepare-op first)


(defmethod prepare-op :create
  [[op resource]]
  (let [hash (codec/hash resource)]
    {:hash-and-resource
     [hash resource]
     :blaze.db/tx-cmd
     [op
      (:resourceType resource)
      (:id resource)
      hash
      (references/extract-references resource)]}))


(defmethod prepare-op :put
  [[op resource matches]]
  (let [hash (codec/hash resource)]
    {:hash-and-resource
     [hash resource]
     :blaze.db/tx-cmd
     (cond->
       [op
        (:resourceType resource)
        (:id resource)
        hash
        (references/extract-references resource)]
       matches
       (conj matches))}))


(defmethod prepare-op :delete
  [[_ type id :as cmd]]
  (let [resource (codec/deleted-resource type id)
        hash (codec/hash resource)]
    {:hash-and-resource
     [hash resource]
     :blaze.db/tx-cmd
     (conj cmd hash)}))


(defn- prepare-ops
  "Splits each transaction operator into an :hash-and-resource tuple and
  a :blaze.db/tx-cmd."
  [tx-ops]
  (mapv prepare-op tx-ops))


(defn- index-resources [resource-indexer batch-size hash-and-resources]
  (->> (partition-all batch-size hash-and-resources)
       (map (partial indexer/index-resources resource-indexer))
       (apply md/zip')))


(defn- reference-graph [cmds]
  (loom.graph/digraph
    (into
      {}
      (map
        (fn [[_ type id _ references]]
          [[type id] references]))
      cmds)))


(defn- reference-order
  "Returns a seq of `[type id]` tuples of `cmds` in reference dependency order."
  [cmds]
  (reverse (loom.alg/topsort (reference-graph cmds))))


(defn- index-by-type-id
  "Returns a map from `[type id]` tuples to commands."
  [cmds]
  (into {} (map (fn [[_ type id :as cmd]] [[type id] cmd])) cmds))


(defn- sort-by-references [cmds]
  (let [index (index-by-type-id cmds)
        order (reference-order cmds)]
    (into [] (comp (map index) (remove nil?)) order)))


(deftype LocalTxLog [resource-indexer resource-indexer-batch-size tx-indexer
                     ^Clock clock executor t-counter]
  tx-log/TxLog
  (-submit [_ tx-ops]
    (let [timer (prom/timer tx-log-indexer-duration-seconds "submit")
          res (validate-ops tx-ops)]
      (if (::anom/category res)
        (md/error-deferred res)
        (let [ops (prepare-ops tx-ops)
              cmds (mapv :blaze.db/tx-cmd ops)
              cmds (sort-by-references cmds)]
          (-> (index-resources resource-indexer resource-indexer-batch-size
                               (mapv :hash-and-resource ops))
              (md/chain'
                (fn submit-tx [_]
                  (md/future-with executor
                    (let [t (vswap! t-counter inc)]
                      (indexer/index-tx tx-indexer t (Instant/now clock) cmds)
                      (prom/observe-duration! timer)
                      t))))))))))


(defn init-local-tx-log
  "Returns a transaction log which is suitable only for single node setups.

  Note: The key-value store has to be exclusive for this transaction log."
  [resource-indexer resource-indexer-batch-size tx-indexer clock]
  (->LocalTxLog
    resource-indexer
    resource-indexer-batch-size
    tx-indexer
    clock
    (ex/single-thread-executor "transactor")
    (volatile! (indexer/last-t tx-indexer))))


(s/def ::resource-indexer-batch-size
  nat-int?)


(defmethod ig/pre-init-spec :blaze.db.tx-log/local [_]
  (s/keys
    :req-un
    [:blaze.db.indexer/resource-indexer
     :blaze.db.indexer/tx-indexer]
    :opt-un
    [::resource-indexer-batch-size]))


(defn- init-msg [resource-indexer-batch-size]
  (format "Open local transaction log with a resource indexer batch size of %d."
          resource-indexer-batch-size))


(defmethod ig/init-key :blaze.db.tx-log/local
  [_ {:keys [resource-indexer resource-indexer-batch-size tx-indexer]
      :or {resource-indexer-batch-size 1}}]
  (log/info (init-msg resource-indexer-batch-size))
  (init-local-tx-log resource-indexer resource-indexer-batch-size tx-indexer
                     (Clock/systemDefaultZone)))


(derive :blaze.db.tx-log/local :blaze.db/tx-log)


(reg-collector ::duration-seconds
  tx-log-indexer-duration-seconds)
