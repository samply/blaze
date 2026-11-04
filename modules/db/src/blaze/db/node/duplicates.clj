(ns blaze.db.node.duplicates
  "Detection of transaction commands that conflict with each other, because a
  transaction can act on each resource only once.

  Most commands act on a whole resource. The exception are delete history
  version commands, that act on a single version of a resource. They conflict
  with each other only if they act on the same version, so that several
  versions of a resource can be deleted in one transaction. They still conflict
  with all commands acting on the whole resource.

  Used both by the node, that rejects such transactions before submitting them,
  and by the indexer, that rejects them after expanding their commands."
  (:require
   [blaze.db.node.tx-indexer.util :as tx-u]))

(defmulti format-command
  "Formats `command` for messages."
  {:arglists '([command])}
  :op)

(defmethod format-command "hold" [{:keys [type id if-none-exist]}]
  (format "create %s?%s (resolved to id %s)" type
          (tx-u/clauses->query-params if-none-exist) id))

(defmethod format-command "delete-history-version" [{:keys [op type id t]}]
  (format "%s %s/%s/_history/%d" op type id t))

(defmethod format-command :default [{:keys [op type id]}]
  (format "%s %s/%s" op type id))

(defn duplicate-msg
  "Returns the message stating that `earlier-cmd` and `later-cmd` conflict,
  naming them in the order of the transaction."
  [earlier-cmd later-cmd]
  (format "Duplicate transaction commands `%s` and `%s`."
          (format-command earlier-cmd) (format-command later-cmd)))

(defn- conflict
  "Returns the entry of the command in `index` that a command acting on the
  resource with `type` and `id`, or only on its version `t`, conflicts with.
  Returns nil if there is no such command."
  [index type id t]
  (or (get index [type id])
      (if t
        (get index [type id t])
        (get index [type id ::any-version]))))

(defn- add
  "Returns `index` with `entry` of the command acting on the resource with
  `type` and `id`, or only on its version `t`, added.

  A command acting on a version is indexed under its version and, if it's the
  first one of its resource, also under ::any-version, so that commands acting
  on the whole resource conflict with it."
  [index type id t entry]
  (if t
    (cond-> (assoc index [type id t] entry)
      (not (contains? index [type id ::any-version]))
      (assoc [type id ::any-version] entry))
    (assoc index [type id] entry)))

(defn- version
  "Returns the t of the version `cmd` acts on, if it acts only on a single
  version of a resource. Returns nil for commands acting on the whole resource.

  Decides by the op of `cmd`, so that a command carrying a `t` for another
  purpose still acts on the whole resource."
  {:arglists '([cmd])}
  [{:keys [op t]}]
  (when (= "delete-history-version" op)
    t))

(defn- duplicate [[existing-idx existing-cmd] idx cmd]
  {:cmd cmd :idx idx :existing-cmd existing-cmd :existing-idx existing-idx})

(defn find-duplicate
  "Returns the first command of `cmds` that conflicts with an earlier one under
  :cmd and its position in `cmds` under :idx, together with the earlier command
  under :existing-cmd and its position under :existing-idx. Returns nil if no
  commands conflict.

  Only delete history version commands act on a single version, so that they
  can delete different versions of the same resource.

  Skips commands that don't identify a resource by type and id, like
  conditional deletes and patient purges, because they are expanded into such
  commands only later."
  [cmds]
  (::duplicate
   (reduce-kv
    (fn [index idx {:keys [type id] :as cmd}]
      (if (and type id)
        (let [t (version cmd)]
          (if-let [existing (conflict index type id t)]
            (reduced {::duplicate (duplicate existing idx cmd)})
            (add index type id t [idx cmd])))
        index))
    {}
    cmds)))
