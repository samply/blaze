(ns blaze.db.node.tx-indexer.expand
  "Expands non-terminal transaction commands into terminal transaction commands
  that are verified later."
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.fold :as f]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.index.search-param-value-resource :as sp-vr]
   [blaze.db.impl.search-param.util :as u]
   [blaze.db.kv.spec]
   [blaze.db.node.stats :as node-stats]
   [blaze.db.node.tx-indexer.util :as tx-u]
   [blaze.db.search-param-registry :as sr]
   [blaze.fhir.canonical :as canonical]
   [blaze.graph-definition :as gd]
   [clojure.string :as str]
   [cognitect.anomalies :as anom]
   [prometheus.alpha :as prom]))

(set! *warn-on-reflection* true)

(defmulti expand
  "Expands `command` into possibly many commands in `context`, a map of the
  `:db-before`, the database before the transaction, its `:stats` and the
  `:pool`, the ForkJoinPool to expand in parallel on.

  The expanded commands act on every resource at most once, so that the
  expansion of a transaction of a single command doesn't have to be checked
  for duplicates.

  Returns a CompletableFuture that completes with the expanded commands or
  completes exceptionally with an anomaly on errors."
  {:arglists '([context command])}
  (fn [_ {:keys [op]}] op))

(defn- failing-conditional-create-query-msg [type clauses {::anom/keys [message]}]
  (format "Conditional create of a %s with query `%s` failed. Cause: %s"
          type (tx-u/clauses->query-params clauses) message))

(defn- conditional-create-matches [db type clauses]
  (-> (do-sync [handles (d/type-query db type clauses)]
        (into [] (take 2) handles))
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
  [{:keys [db-before]} {:keys [type if-none-exist] :as command}]
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
  [{:keys [db-before]} {:keys [type clauses allow-multiple] :as command}]
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

(defn- graph-def-matches
  "Returns a CompletableFuture that completes with at most two handles of the
  GraphDefinitions with the canonical `graph`, which are enough to decide
  whether it's unique."
  [db graph]
  (do-sync [handles (d/type-query db "GraphDefinition" (graph-def-clauses graph))]
    (into [] (take 2) handles)))

(defn- graph-def-handle [db graph]
  (do-sync [[h1 h2] (graph-def-matches db graph)]
    (cond
      h2 (ba/fault (format "Found more than one GraphDefinition `%s` for purging Patients." graph))
      h1 h1
      :else (ba/fault (format "The GraphDefinition `%s` for purging Patients wasn't found." graph)))))

(defn- rev-link-error-msg
  "Returns the error message for the search param with `code` of a link to
  `type`, if it isn't a reference search param targeting Patient, because the
  link would silently select no resources otherwise."
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
  completes exceptionally with a fault if the GraphDefinition can't be found
  or isn't valid for purging.

  The anomaly is a fault, because only Blaze itself passes the canonical of a
  GraphDefinition it installed, so a missing or invalid one is a problem of the
  server and not of the client requesting the purge."
  [{{:keys [search-param-registry]} :node :as db} graph]
  (do-sync [graph-def (-> (graph-def-handle db graph)
                          (ac/then-compose (partial d/pull db)))]
    (ba/exceptionally
     (graph-def-rev-links search-param-registry graph-def)
     #(ba/fault (invalid-graph-def-msg graph %)))))

(defn- rev-links
  "Returns a CompletableFuture that completes with the `[type code]` tuples of
  the resources to purge together with a patient.

  Uses the GraphDefinition with the canonical `graph` if given and the Patient
  compartment otherwise."
  [{{:keys [search-param-registry]} :node :as db} graph]
  (if graph
    (graph-rev-links db graph)
    (ac/completed-future (compartment-rev-links search-param-registry))))

(defn- purge-tx-cmd [check-refs {:keys [id] :as resource-handle}]
  {:op "purge" :type (name (:fhir/type resource-handle)) :id id
   :check-refs check-refs})

(def ^:private ^:const ^long resolve-part-size
  "The number of index handles whose resource handles are resolved in one part
  of a parallel resolution."
  512)

(defn- resolve-part
  "Returns a vector of the resource handles of `part`, index handles of
  resources of the type with `tid`, that exist in `db` and match their index
  handle."
  [db tid part]
  (into [] (u/resource-handle-xf db tid) part))

(defn- link-resource-handles
  "Returns a vector of the resource handles of the resources of `type` that
  reference `reference`, a tid-id, through the search param with `code`,
  ordered by id. A link returns each resource only once.

  Reads the index handles of the link on the calling thread and resolves them
  in parts in parallel on `pool` while they are still read, because resolving
  takes a seek per resource and a patient can have thousands of resources of a
  type. Each part is resolved with a transducer of its own, which holds an
  iterator."
  {:arglists '([db pool reference rev-link])}
  [{:keys [snapshot] :as db} pool reference [type code]]
  (let [tid (codec/tid type)]
    (f/fold
     pool
     resolve-part-size
     (partial resolve-part db tid)
     into
     (sp-vr/index-handles-full-value snapshot (codec/c-hash code) tid
                                     reference))))

(defn- single-link-resource-handles
  "Returns the resource handles of the single link of `part` (see
  `link-resource-handles`)."
  [db pool reference [rev-link]]
  (link-resource-handles db pool reference rev-link))

(defn- single-link-resource-handles-vector
  "Returns the resource handles of the single link of `part` (see
  `link-resource-handles`) in a vector of its own, so that `into` collects the
  ones of all links, because `into` has the identity `f/fold` needs, unlike
  `conj`."
  [db pool reference part]
  [(single-link-resource-handles db pool reference part)])

(defn- into-set
  "Adds `resource-handles`, the ones of a link, to `set`, the ones of the links
  before it, or returns the empty set if called without arguments."
  ([] #{})
  ([set resource-handles]
   (into set resource-handles)))

(defn- distinct-types?
  "Returns true if no two `[type code]` tuples of `rev-links` have the same
  type."
  [rev-links]
  (boolean
   (reduce
    (fn [types [type]]
      (if (contains? types type)
        (reduced false)
        (conj types type)))
    #{}
    rev-links)))

(defn- referencing-resource-handles
  "Returns a reducible collection of the resource handles of the resources that
  reference `reference`, a tid-id, through the search param with `code` of
  each `[type code]` tuple of `rev-links`.

  A resource referencing `reference` through several search params, like
  `subject` and `patient` of an Observation, is returned only once. A single
  link returns each resource only once, so that only links of the same type can
  return a resource twice. Only then the resource handles are collected in a
  set. Otherwise the vectors of the links are concatenated only when the
  collection is reduced, which saves hashing each of them on the calling
  thread, where the links are combined, and copies them only once, into
  whatever the collection is reduced to.

  The links are read in parallel on `pool`, one task per link, because reading
  the index handles of a link takes a step per resource, so that reading the
  links one after another would limit the expansion of a patient with many
  resources. Each task resolves the resource handles of its link in parallel
  as well (see `link-resource-handles`)."
  [db pool reference rev-links]
  (if (distinct-types? rev-links)
    (coll/eduction
     cat
     (f/fold pool 1
             (partial single-link-resource-handles-vector db pool reference)
             into rev-links))
    (f/fold pool 1 (partial single-link-resource-handles db pool reference)
            into-set rev-links)))

(defn- purge-tx-cmds
  "Returns the purge commands of all resources that reference the patient of
  `patient-handle` through the search param with `code` of each `[type code]`
  tuple of `rev-links`, in no particular order."
  [db pool patient-handle check-refs rev-links]
  (coll/eduction
   (map (partial purge-tx-cmd check-refs))
   (referencing-resource-handles db pool (rh/tid-id patient-handle) rev-links)))

(defn- non-empty-links
  "Returns the `[type code]` tuples of `rev-links` whose type has resources
  according to `stats`, the stats of the database before the transaction.

  A type without resources has none that could reference the patient, so that
  reading its link would only cost a seek. Most links of the purge
  GraphDefinition are of types that a database has no resources of."
  [stats rev-links]
  (filterv (comp (partial node-stats/non-empty-type? stats) first) rev-links))

(def ^:private patient-twins
  "The `[type code-1 code-2]` tuples of the built-in search params of `type`
  with `code-1` and `code-2` that index the same references to Patients.

  Either both have the same expression for `type`, or one restricts the
  references of the other one to Patients with `where(resolve() is Patient)`.
  The index entries pointing to a Patient are written for literal references
  only, and while indexing, `resolve()` returns a stub with the type of the
  literal reference, so that the restriction keeps exactly the references
  having such an entry.

  A custom search param replacing one of them for `type` breaks that."
  [["Account" "patient" "subject"]
   ["Appointment" "actor" "patient"]
   ["AppointmentResponse" "actor" "patient"]
   ["Basic" "patient" "subject"]
   ["CarePlan" "patient" "subject"]
   ["CareTeam" "patient" "subject"]
   ["ChargeItem" "patient" "subject"]
   ["ClinicalImpression" "patient" "subject"]
   ["Communication" "patient" "subject"]
   ["CommunicationRequest" "patient" "subject"]
   ["Composition" "patient" "subject"]
   ["Condition" "patient" "subject"]
   ["Contract" "patient" "subject"]
   ["Coverage" "beneficiary" "patient"]
   ["DeviceRequest" "patient" "subject"]
   ["DeviceUseStatement" "patient" "subject"]
   ["DiagnosticReport" "patient" "subject"]
   ["DocumentManifest" "patient" "subject"]
   ["DocumentReference" "patient" "subject"]
   ["Encounter" "patient" "subject"]
   ["EnrollmentRequest" "patient" "subject"]
   ["Flag" "patient" "subject"]
   ["Goal" "patient" "subject"]
   ["GuidanceResponse" "patient" "subject"]
   ["ImagingStudy" "patient" "subject"]
   ["Invoice" "patient" "subject"]
   ["Linkage" "item" "source"]
   ["List" "patient" "subject"]
   ["MeasureReport" "patient" "subject"]
   ["Media" "patient" "subject"]
   ["MedicationAdministration" "patient" "subject"]
   ["MedicationDispense" "patient" "subject"]
   ["MedicationRequest" "patient" "subject"]
   ["MedicationStatement" "patient" "subject"]
   ["Observation" "patient" "subject"]
   ["Person" "link" "patient"]
   ["Procedure" "patient" "subject"]
   ["Provenance" "patient" "target"]
   ["QuestionnaireResponse" "patient" "subject"]
   ["RequestGroup" "patient" "subject"]
   ["ResearchSubject" "individual" "patient"]
   ["RiskAssessment" "patient" "subject"]
   ["ServiceRequest" "patient" "subject"]
   ["Specimen" "patient" "subject"]
   ["Task" "patient" "subject"]])

(def ^:private patient-twin-rev-include-param
  "Map from the `[type code]` tuple of each search param of `patient-twins` to
  the rev-include param of its twin (see `tx-u/rev-include-param`).

  The rev-include params are built once, so that the expansion of a patient
  only looks them up."
  (into
   {}
   (mapcat
    (fn [[type code-1 code-2]]
      [[[type code-1] (tx-u/rev-include-param type code-2)]
       [[type code-2] (tx-u/rev-include-param type code-1)]]))
   patient-twins))

(defn- rev-include-param
  "Returns the rev-include param of `rev-link` (see `tx-u/rev-include-param`)."
  [[type code]]
  (tx-u/rev-include-param type code))

(defn- purged-rev-include-params
  "Returns the set of the rev-include params of `rev-links`, the `[type code]`
  tuples of the links of the expansion of a patient, and of their twins (see
  `patient-twins`).

  The transaction purges every resource referencing the patient through them,
  through the links because the expansion selects the resources of each link,
  and through the twins because they index the same references."
  [rev-links]
  (-> (into #{} (map rev-include-param) rev-links)
      (into (keep patient-twin-rev-include-param) rev-links)))

(defn- patient-purge-tx-cmd
  "Returns the purge command of the patient with `id`, whose expansion has the
  `rev-links` of the types with resources.

  The command checking references carries the `purged-rev-include-params` of
  `rev-links`, so that the check of the references to the patient leaves out
  the search params through which the transaction purges every resource
  referencing it. Reading them would read the index entries of all resources
  the expansion selected once again."
  [id check-refs rev-links]
  (cond-> {:op "purge" :type "Patient" :id id :check-refs check-refs}
    check-refs
    (assoc :purged-rev-include-params (purged-rev-include-params rev-links))))

(defn- patient-purge-tx-cmds
  "Returns the purge commands of the patient of `command` and the resources
  selected with it or an anomaly if the GraphDefinition of `command` can't be
  resolved.

  Waits for the GraphDefinition, which is resolved in `db-before`
  asynchronously, if it isn't cached. So the resources are selected on `pool`,
  with the calling thread, the indexer thread, waiting for them, and not from
  the thread completing the resolution, a thread of the common ForkJoinPool.
  That also keeps the verification following the expansion on the indexer
  thread."
  {:arglists '([context command])}
  [{:keys [db-before stats pool]} {:keys [id check-refs graph] :or {check-refs false}}]
  (if-let [handle (d/resource-handle db-before "Patient" id)]
    (when-ok [rev-links (ba/try-anomaly (ac/join (rev-links db-before graph)))]
      (let [rev-links (non-empty-links stats rev-links)]
        (into
         [(patient-purge-tx-cmd id check-refs rev-links)]
         (purge-tx-cmds db-before pool handle check-refs rev-links))))
    []))

(defmethod expand "patient-purge"
  [context command]
  (with-open [_ (prom/timer tx-u/duration-seconds "expand-patient-purge")]
    (ac/completed-future (patient-purge-tx-cmds context command))))

(defmethod expand :default
  [_ command]
  (ac/completed-future [command]))

(defn expand-tx-cmds
  "Expands all non-terminal `tx-cmds` into terminal transaction commands in
  `context`, a map of the `:db-before`, the database before the transaction,
  its `:stats` and the `:pool`, the ForkJoinPool to expand in parallel on.

  Returns a CompletableFuture that completes with the expanded commands or
  completes exceptionally with an anomaly on errors."
  [context tx-cmds]
  (let [timer (prom/timer tx-u/duration-seconds "expand-tx-cmds")
        futures (mapv (partial expand context) tx-cmds)]
    (-> (ac/all-of futures)
        (ac/then-apply (fn [_] (into [] (mapcat ac/join) futures)))
        (ac/when-complete (fn [_ _] (.close timer))))))
