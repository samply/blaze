(ns blaze.db.node.tx-indexer.expand
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.db.api :as d]
   [blaze.db.kv.spec]
   [blaze.db.node.tx-indexer.util :as tx-u]
   [cognitect.anomalies :as anom]
   [prometheus.alpha :as prom]))

(defmulti expand
  "Expands `command` into possibly many commands.

  Returns an anomaly on errors."
  {:arglists '([db-before command])}
  (fn [_ {:keys [op]}] op))

(defn- failing-conditional-create-query-msg [type clauses {::anom/keys [message]}]
  (format "Conditional create of a %s with query `%s` failed. Cause: %s"
          type (tx-u/clauses->query-params clauses) message))

(defn- conditional-create-matches [db type clauses]
  (if-ok [resource-handles (d/type-query db type clauses)]
    (into [] (take 2) resource-handles)
    #(ba/incorrect (failing-conditional-create-query-msg type clauses %))))

(defn- format-handle [type {:keys [id t]}]
  (format "%s/%s/_history/%s" type id t))

(defn- multiple-existing-resources-msg [type clauses [h1 h2]]
  (format "Conditional create of a %s with query `%s` failed because at least the two matches `%s` and `%s` were found."
          type (tx-u/clauses->query-params clauses) (format-handle type h1)
          (format-handle type h2)))

(defn- multiple-existing-resources-anom [type clauses handles]
  (ba/conflict
   (multiple-existing-resources-msg type clauses handles)
   :http/status 412))

(defmethod expand "create"
  [db-before {:keys [type if-none-exist] :as command}]
  (with-open [_ (prom/timer tx-u/duration-seconds "expand-create")]
    (when-ok [[h1 h2] (some->> if-none-exist (conditional-create-matches db-before type))]
      (cond
        h2 (multiple-existing-resources-anom type if-none-exist [h1 h2])
        h1 [(assoc command :op "hold" :id (:id h1))]
        :else [command]))))

(def ^:private ^:const ^long max-multiple-deletes 10000)

(defn- too-many-multiple-matches-msg
  ([type]
   (format "Conditional delete of all %ss failed because more than %,d matches were found."
           type max-multiple-deletes))
  ([type clauses]
   (format "Conditional delete of %ss with query `%s` failed because more than %,d matches were found."
           type (tx-u/clauses->query-params clauses) max-multiple-deletes)))

(defn- too-many-multiple-matches-anom [type clauses]
  (ba/conflict
   (if clauses
     (too-many-multiple-matches-msg type clauses)
     (too-many-multiple-matches-msg type))
   :fhir/issue "too-costly"))

(defn- failing-conditional-delete-query-msg [type clauses {::anom/keys [message]}]
  (format "Conditional delete of %ss with query `%s` failed. Cause: %s"
          type (tx-u/clauses->query-params clauses) message))

(defn- conditional-delete-matches [db type clauses]
  (-> (d/type-query db type clauses)
      (ba/exceptionally #(ba/incorrect (failing-conditional-delete-query-msg type clauses %)))))

(defn- multiple-matches-msg
  ([type [h1 h2]]
   (format "Conditional delete of one single %s without a query failed because at least the two matches `%s` and `%s` were found."
           type (format-handle type h1) (format-handle type h2)))
  ([type clauses [h1 h2]]
   (format "Conditional delete of one single %s with query `%s` failed because at least the two matches `%s` and `%s` were found."
           type (tx-u/clauses->query-params clauses) (format-handle type h1)
           (format-handle type h2))))

(defn- multiple-matches-anom [type clauses handles]
  (ba/conflict
   (if clauses
     (multiple-matches-msg type clauses handles)
     (multiple-matches-msg type handles))
   :http/status 412))

(defmethod expand "conditional-delete"
  [db-before {:keys [type clauses allow-multiple] :as command}]
  (with-open [_ (prom/timer tx-u/duration-seconds "expand-conditional-delete")]
    (when-ok [matches (or (some->> clauses (conditional-delete-matches db-before type))
                          (d/type-list db-before type))]
      (if allow-multiple
        (let [handles (into [] (take (inc max-multiple-deletes)) matches)]
          (if (< max-multiple-deletes (count handles))
            (too-many-multiple-matches-anom type clauses)
            (mapv (fn [{:keys [id]}] (assoc command :op "delete" :id id)) handles)))
        (let [[h1 h2] (into [] (take 2) matches)]
          (cond
            h2 (multiple-matches-anom type clauses [h1 h2])
            h1 [(assoc command :op "delete" :id (:id h1))]
            :else []))))))

(defmethod expand :default
  [_ command]
  [command])

(defn expand-tx-cmds
  "Expands all conditional `commands` into non-conditional commands.

  Returns an anomaly on errors."
  [db-before commands]
  (with-open [_ (prom/timer tx-u/duration-seconds "expand-tx-cmds")]
    (transduce
     (comp (map (partial expand db-before))
           (halt-when ba/anomaly?)
           cat)
     conj
     commands)))
