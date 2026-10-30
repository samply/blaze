(ns blaze.db.api-stub.clj-kondo
  (:require
   [clj-kondo.hooks-api :as api]))

(defn with-db
  "Lints `(with-db [db node t] & body)` as `(do node t (fn [db] & body))`, so
  that `db` is bound in `body` and `node` and `t` are used."
  [{{[_ binding-vec & body] :children} :node}]
  (let [[db & args] (:children binding-vec)]
    {:node (api/list-node
            (concat [(api/token-node 'do)]
                    args
                    [(api/list-node
                      (list* (api/token-node 'fn) (api/vector-node [db]) body))]))}))
