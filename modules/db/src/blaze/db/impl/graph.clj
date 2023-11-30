(ns blaze.db.impl.graph)

(defn get-reachables
  "From a graph represented as a function returning the neighbors of
   a single node in a vector, produces another function which returns
   the set of all nodes reachable from a vector of nodes. Nil cannot
   be a node.
   
   The returned set is materialized, so it is not suitable for big
   graphs."
  [get-neighbors]
  (fn [nodes]
    (loop [collected (set nodes) frontier nodes]
      (if (empty? frontier) collected
          (let [neighbors (remove collected (mapcat get-neighbors frontier))]
            (recur (into collected neighbors) neighbors))))))
