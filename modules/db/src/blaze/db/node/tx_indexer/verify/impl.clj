(ns blaze.db.node.tx-indexer.verify.impl
  (:require
    [blaze.anomaly :as ba :refer [if-ok]]
    [blaze.db.api :as d]
    [clojure.string :as str]))


(defn clauses->query-params [clauses]
  (->> clauses
       (map (fn [[param & values]] (str param "=" (str/join "," values))))
       (str/join "&")))


(defn- format-handle [type {:keys [id t]}]
  (format "%s/%s/_history/%s" type id t))


(defn- multiple-existing-resources-msg [type clauses [h1 h2]]
  (format "Conditional create of a %s with query `%s` failed because at least the two matches `%s` and `%s` were found."
          type (clauses->query-params clauses) (format-handle type h1)
          (format-handle type h2)))


(defn- multiple-existing-resources-anom [type clauses handles]
  (ba/conflict
    (multiple-existing-resources-msg type clauses handles)
    :http/status 412))


(defn- existing-resource-handles [db type clauses]
  (into [] (take 2) (d/type-query db type clauses)))


(defn- resolve-conditional-create* [db {:keys [type if-none-exist] :as cmd}]
  (let [[h1 h2] (some->> if-none-exist (existing-resource-handles db type))]
    (cond
      h2 (multiple-existing-resources-anom type if-none-exist [h1 h2])
      h1 (assoc cmd :op "hold" :id (:id h1))
      :else cmd)))


(defn resolve-conditional-create
  "Resolves all conditional create commands.

  Returns either `commands` with conditional create commands converted to `hold`
  command if the resource already exists or a conflict anomaly on the first
  ambiguous match."
  [db commands]
  (transduce
    (comp (map (partial resolve-conditional-create* db)) (halt-when ba/anomaly?))
    conj [] commands))


(defn- multiple-matches-msg [type clauses [h1 h2]]
  (format "Resolving the conditional reference `%s?%s` failed because at least the two matches `%s` and `%s` were found."
          type (clauses->query-params clauses) (format-handle type h1)
          (format-handle type h2)))


(defn- multiple-matches-anom [type clauses handles]
  (ba/conflict
    (multiple-matches-msg type clauses handles)
    :blaze/unresolvable-ref [type clauses]
    :http/status 412))


(defn- no-match-msg [type clauses]
  (format "Resolving the conditional reference `%s?%s` failed because to match was found."
          type (clauses->query-params clauses)))


(defn- no-match-anom [type clauses]
  (ba/conflict
    (no-match-msg type clauses)
    :blaze/unresolvable-ref [type clauses]
    :http/status 412))


(defn- resolve-conditional-ref* [db [type clauses]]
  (let [[h1 h2] (existing-resource-handles db type clauses)]
    (cond
      h2 (multiple-matches-anom type clauses [h1 h2])
      h1 [type (:id h1)]
      :else (no-match-anom type clauses))))


(defn- conditional? [[_ x]]
  (vector? x))


(defn- resolve-conditional-ref [db {:keys [refs] :as cmd}]
  (reduce
    (fn [cmd ref]
      (if (conditional? ref)
        (if-ok [resolved-ref (resolve-conditional-ref* db ref)]
          (-> (update cmd :refs conj resolved-ref)
              (update :ref-mappings assoc ref resolved-ref))
          reduced)
        (update cmd :refs conj ref)))
    (assoc cmd :refs [])
    refs))


(defn resolve-conditional-refs
  "Resolves all conditional references in `commands`.

  The `db` has to be a with-database that includes already the resources of the
  transaction.

  Returns either commands with the resolved references including :ref-mappings
  or a conflict anomaly on the first ambiguous match."
  [db commands]
  (transduce
    (comp (map (partial resolve-conditional-ref db)) (halt-when ba/anomaly?))
    conj [] commands))


(defmulti format-command :op)


(defmethod format-command "hold" [{:keys [type id if-none-exist]}]
  (format "create %s?%s (resolved to id %s)" type
          (clauses->query-params if-none-exist) id))


(defmethod format-command :default [{:keys [op type id]}]
  (format "%s %s/%s" op type id))


(defn- duplicate-tx-cmds-msg [cmd-a cmd-b]
  (format "Duplicate transaction commands `%s` and `%s`."
          (format-command cmd-a) (format-command cmd-b)))


(defn detect-duplicate-commands [commands]
  (reduce
    (fn [index {:keys [type id] :as cmd}]
      (if-let [existing-cmd (get index [type id])]
        (reduced (ba/conflict (duplicate-tx-cmds-msg cmd existing-cmd)))
        (assoc index [type id] cmd)))
    {}
    commands))


(defmulti verify-command
  {:arglists '([db cmd])}
  (fn [_ {:keys [op]}] op))


(defn- resource-already-exists-msg [type id]
  (format "Resource `%s/%s` already exists and can't be created again." type id))


(defmethod verify-command "create"
  [db {:keys [type id] :as cmd}]
  (if (d/resource-handle db type id)
    (ba/conflict (resource-already-exists-msg type id))
    cmd))


(defn- precondition-failed-msg [if-match type id]
  (format "Precondition `W/\"%d\"` failed on `%s/%s`." if-match type id))


(defmethod verify-command "put"
  [db {:keys [type id if-match] :as cmd}]
  (if (or (nil? if-match) (= if-match (:t (d/resource-handle db type id))))
    cmd
    (ba/conflict (precondition-failed-msg if-match type id) :http/status 412)))


(defmethod verify-command :default
  [_ cmd]
  cmd)


(defn verify-commands [db commands]
  (transduce
    (comp (map (partial verify-command db)) (halt-when ba/anomaly?))
    conj [] commands))


(defn- reduce-anom
  ([x] x)
  ([_ x] (when (ba/anomaly? x) (reduced x))))


(defn- ref-integrity-msg [type id]
  (format "Referential integrity violated. Resource `%s/%s` doesn't exist."
          type id))


(defn- resource-exists? [db type id]
  (when-let [{:keys [op]} (d/resource-handle db type id)]
    (not (identical? :delete op))))


(defn- check-referential-integrity** [db [type id]]
  (when-not (resource-exists? db type id)
    (ba/conflict (ref-integrity-msg type id))))


(defn- check-referential-integrity* [db {:keys [refs]}]
  (transduce
    (map (partial check-referential-integrity** db))
    reduce-anom nil refs))


(defn check-referential-integrity [db commands]
  (transduce
    (map (partial check-referential-integrity* db))
    reduce-anom nil commands))
