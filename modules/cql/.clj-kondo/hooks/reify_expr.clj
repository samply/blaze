(ns hooks.reify-expr
  (:require
   [clj-kondo.hooks-api :as api]))

(def ^:private method-arities
  {'-static 1
   '-attach-cache 2
   '-patient-count 1
   '-resolve-refs 2
   '-resolve-params 2
   '-optimize 2
   '-eval 4
   '-form 1})

(defn- method-name [node]
  (when (api/list-node? node)
    (let [head (first (:children node))]
      (when (api/token-node? head)
        (:value head)))))

(defn- stub [name arity]
  (api/list-node
   [(api/token-node name)
    (api/vector-node (repeat arity (api/token-node '_)))
    (api/token-node nil)]))

(defn reify-expr [{:keys [node]}]
  (let [[_ proto & body] (:children node)
        present (into #{} (keep method-name) body)
        stubs (for [[name arity] method-arities
                    :when (not (contains? present name))]
                (stub name arity))
        new-children (into [(api/token-node 'reify) proto]
                           (concat body stubs))]
    {:node (with-meta (api/list-node new-children) (meta node))}))
