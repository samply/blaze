(ns blaze.db.node.tx-indexer.expand
  "Expands non-terminal transaction commands into terminal transaction commands
  that are verified later."
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.impl.protocols :as p]
   [blaze.db.kv.spec]
   [blaze.db.node.tx-indexer.util :as tx-u]
   [blaze.db.search-param-registry :as sr]
   [blaze.fhir.canonical :as canonical]
   [blaze.graph-definition :as gd]
   [clojure.string :as str]
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

(defn- compartment-rev-links
  "Returns the `[type code]` tuples of all search params of the Patient
  compartment, except the ones of Patients, which would select other patients."
  [search-param-registry]
  (coll/eduction
   (comp
    (remove (comp #{"Patient"} first))
    (mapcat (fn [[type codes]] (map (partial vector type) codes))))
   (sr/compartment-resources search-param-registry "Patient")))

(def ^:private internal-security-clause
  ["_security" (str (canonical/url "CodeSystem/AccessControl") "|internal")])

(defn- graph-def-clauses
  "Returns the search clauses selecting the GraphDefinition with the canonical
  `graph` of the form `url|version`, labelled internal, so that only a
  GraphDefinition Blaze installed itself is found."
  [graph]
  (let [[url version] (str/split graph #"\|" 2)]
    [["url" url] ["version" version] internal-security-clause]))

(defn- graph-def-handle [db graph]
  (do-sync [[h1 h2] (-> (d/type-query db "GraphDefinition" (graph-def-clauses graph))
                        (ac/then-apply #(into [] (take 2) %)))]
    (cond
      h2 (ba/conflict (format "Found more than one GraphDefinition `%s` for purging Patients." graph))
      h1 h1
      :else (ba/conflict (format "The GraphDefinition `%s` for purging Patients wasn't found." graph)))))

(defn- rev-link-error-msg
  "Returns the error message for the search param with `code` of a link to
  `type`, if it isn't a reference search param targeting Patient, because
  `p/-rev-include` would silently select no resources otherwise."
  [search-param-registry [type code]]
  (if-let [{sp-type :type :keys [target]} (sr/get search-param-registry code type)]
    (cond
      (not= "reference" sp-type)
      (format "The link to `%s` uses a search parameter of type `%s` instead of `reference` with code `%s`." type sp-type code)

      (not (some #{"Patient"} target))
      (format "The link to `%s` uses a reference search parameter not targeting Patient with code `%s`." type code))
    (format "The link to `%s` uses an unknown search parameter with code `%s`." type code)))

(defn- check-rev-links [search-param-registry rev-links]
  (if-let [msg (some (partial rev-link-error-msg search-param-registry) rev-links)]
    (ba/incorrect msg)
    rev-links))

(defn- graph-def-rev-links [search-param-registry graph-def]
  (when-ok [compiled-graph (gd/compile graph-def)
            rev-links (gd/rev-links compiled-graph)]
    (check-rev-links search-param-registry rev-links)))

(defn- invalid-graph-def-msg [graph {::anom/keys [message]}]
  (format "Invalid GraphDefinition `%s` for purging Patients: %s" graph message))

(defn- graph-rev-links
  "Returns a CompletableFuture that completes with the `[type code]` tuples of
  the links of the GraphDefinition with the canonical `graph`, found in `db`, or
  completes exceptionally with an anomaly if the GraphDefinition can't be found
  or isn't valid for purging."
  [{{:keys [search-param-registry]} :node :as db} graph]
  (do-sync [graph-def (-> (graph-def-handle db graph)
                          (ac/then-compose (partial d/pull db)))]
    (ba/exceptionally
     (graph-def-rev-links search-param-registry graph-def)
     #(assoc % ::anom/message (invalid-graph-def-msg graph %)))))

(defn- rev-links
  "Returns a CompletableFuture that completes with the `[type code]` tuples of
  the resources to purge together with a patient.

  Uses the GraphDefinition with the canonical `graph` if given and the Patient
  compartment otherwise."
  [{{:keys [search-param-registry]} :node :as db} graph]
  (if graph
    (graph-rev-links db graph)
    (ac/completed-future (compartment-rev-links search-param-registry))))

(defn- purge-tx-cmd [type check-refs {:keys [id]}]
  {:op "purge" :type type :id id :check-refs check-refs})

(defn- purge-tx-cmds
  "Returns the purge commands of all resources of `type` that reference the
  patient of `patient-handle` through the search param with `code` of each
  `[type code]` tuple of `rev-links`."
  [db patient-handle check-refs rev-links]
  (coll/eduction
   (comp
    (mapcat
     (fn [[type code]]
       (coll/eduction
        (map (partial purge-tx-cmd type check-refs))
        (p/-rev-include db patient-handle type code))))
    (distinct))
   rev-links))

(defmethod expand "patient-purge"
  [db-before {:keys [id check-refs graph] :or {check-refs false}}]
  (with-open [_ (prom/timer tx-u/duration-seconds "expand-patient-purge")]
    (if-let [handle (d/resource-handle db-before "Patient" id)]
      (do-sync [rev-links (rev-links db-before graph)]
        (into
         [{:op "purge" :type "Patient" :id id :check-refs check-refs}]
         (purge-tx-cmds db-before handle check-refs rev-links)))
      (ac/completed-future []))))

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
