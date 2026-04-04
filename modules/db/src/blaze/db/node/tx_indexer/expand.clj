(ns blaze.db.node.tx-indexer.expand
  "Expands non-terminal transaction commands into terminal transaction commands
  that are verified later."
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.impl.protocols :as p]
   [blaze.db.kv.spec]
   [blaze.db.node.tx-indexer.util :as tx-u]
   [blaze.db.search-param-registry :as sr]
   [cognitect.anomalies :as anom]
   [prometheus.alpha :as prom]))

(set! *warn-on-reflection* true)

(defmulti expand
  "Expands `command` into possibly many commands.

  Returns a CompletableFuture that completes with the expanded commands or
  completes exceptionally with an anomaly on errors."
  {:arglists '([db-before command])}
  (fn [_ {:keys [op]}] op))

(defn- failing-conditional-create-query-msg [type clauses {::anom/keys [message]}]
  (format "Conditional create of a %s with query `%s` failed. Cause: %s"
          type (tx-u/clauses->query-params clauses) message))

(defn- conditional-create-matches [db type clauses]
  (-> (d/type-query db type clauses)
      (ac/then-apply #(into [] (take 2) %))
      (ac/exceptionally #(ba/incorrect (failing-conditional-create-query-msg type clauses %)))))

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
    (if if-none-exist
      (do-sync [[h1 h2] (conditional-create-matches db-before type if-none-exist)]
        (cond
          h2 (multiple-existing-resources-anom type if-none-exist [h1 h2])
          h1 [(assoc command :op "hold" :id (:id h1))]
          :else [command]))
      (ac/completed-future [command]))))

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
      (ac/exceptionally #(ba/incorrect (failing-conditional-delete-query-msg type clauses %)))))

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
    (-> (if clauses
          (conditional-delete-matches db-before type clauses)
          (ac/completed-future (d/type-list db-before type)))
        (ac/then-apply
         (fn [matches]
           (if allow-multiple
             (let [handles (into [] (take (inc max-multiple-deletes)) matches)]
               (if (< max-multiple-deletes (count handles))
                 (too-many-multiple-matches-anom type clauses)
                 (mapv (fn [{:keys [id]}] (assoc command :op "delete" :id id)) handles)))
             (let [[h1 h2] (into [] (take 2) matches)]
               (cond
                 h2 (multiple-matches-anom type clauses [h1 h2])
                 h1 [(assoc command :op "delete" :id (:id h1))]
                 :else []))))))))

(defn- purge-tx-cmds
  [{{:keys [search-param-registry]} :node :as batch-db} patient-handle check-refs]
  (let [rev-include (partial p/-rev-include batch-db patient-handle)]
    (coll/eduction
     (comp
      (mapcat
       (fn [[type codes]]
         (when-not (= "Patient" type)
           (coll/eduction
            (comp
             (mapcat (partial rev-include type))
             (map
              (fn [{:keys [id]}]
                {:op "purge" :type type :id id :check-refs check-refs})))
            codes))))
      (distinct))
     (sr/compartment-resources search-param-registry "Patient"))))

(defmethod expand "patient-purge"
  [db-before {:keys [id check-refs] :or {check-refs false}}]
  (with-open [_ (prom/timer tx-u/duration-seconds "expand-patient-purge")]
    (ac/completed-future
     (when-let [handle (d/resource-handle db-before "Patient" id)]
       (into
        [{:op "purge" :type "Patient" :id id :check-refs check-refs}]
        (purge-tx-cmds db-before handle check-refs))))))

(defmethod expand :default
  [_ command]
  (ac/completed-future [command]))

(defn expand-tx-cmds
  "Expands all non-terminal `tx-cmds` into terminal transaction commands.

  Returns a CompletableFuture that completes with the expanded commands or
  completes exceptionally with an anomaly on errors."
  [db-before tx-cmds]
  (let [timer (prom/timer tx-u/duration-seconds "expand-tx-cmds")
        futures (mapv (partial expand db-before) tx-cmds)]
    (-> (ac/all-of futures)
        (ac/then-apply (fn [_] (into [] (mapcat ac/join) futures)))
        (ac/when-complete (fn [_ _] (.close timer))))))
