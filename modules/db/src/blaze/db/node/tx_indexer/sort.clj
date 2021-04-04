(ns blaze.db.node.tx-indexer.sort
  (:require
    [loom.alg]
    [loom.graph]))


(defn- reference-graph [cmds]
  (loom.graph/digraph
    (into
      {}
      (map
        (fn [{:keys [type id refs]}]
          [[type id] refs]))
      cmds)))


(defn- reference-order
  "Returns a seq of `[type id]` tuples of `cmds` in reference dependency order."
  [cmds]
  (reverse (loom.alg/topsort (reference-graph cmds))))


(defn- index-by-type-id
  "Returns a map from `[type id]` tuples to commands."
  [cmds]
  (into {} (map (fn [{:keys [type id] :as cmd}] [[type id] cmd])) cmds))


(defn- split-delete-commands [tx-cmds]
  (reduce
    (fn [res {:keys [op] :as cmd}]
      (update res (if (= "delete" op) 0 1) conj cmd))
    [[] []]
    tx-cmds))


(defn sort-by-references [tx-cmds]
  (let [[del-cmds other-cmds] (split-delete-commands tx-cmds)]
    (if (empty? other-cmds)
      del-cmds
      (let [index (index-by-type-id other-cmds)
            order (reference-order other-cmds)]
        (into del-cmds (keep index) order)))))
