(ns blaze.db.api
  "Public Database API.

  A database node provides access to a set of database values.

  A database value is an immutable, indexed set of resources at a certain point
  in time `t`."
  (:refer-clojure :exclude [str sync])
  (:require
   [blaze.anomaly :refer [when-ok]]
   [blaze.async.comp :as ac]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.protocols :as p]
   [blaze.db.node.protocols :as np]
   [blaze.util :refer [str]]
   [taoensso.timbre :as log])
  (:import
   [java.lang AutoCloseable]))

(defn db
  "Retrieves the most recent available value of the database for reading.

  Does not communicate with the transaction log, nor block."
  [node]
  (np/-db node))

(defn sync
  "Used to coordinate with other nodes.

  When called with `t`, returns a CompletionStage that will complete with the
  database value with at least the point in time `t` available. Does not
  communicate with the transaction log. Simply waits for the database value
  becoming available.

  When called without `t`, returns a CompletionStage that will complete with the
  database value guaranteed to include all transactions that were complete at
  the time sync was called. Communicates with the transaction log."
  ([node]
   (np/-sync node))
  ([node t]
   (np/-sync node t)))

(defn transact
  "Submits `tx-ops` (transaction operators) to the central transaction log and
  waits for the transaction to commit on `node`.

  The collection of `tx-ops` has to be non-empty.

  A transaction operator can be one of the following:

  * [:create resource clauses?]
  * [:put resource precondition?]
  * [:delete type id]

  Returns a CompletableFuture that will complete with the database after the
  transaction in case of success or will complete exceptionally with an anomaly
  in case of a transaction error or other errors.

  Functions applied after the returned future are executed on the common
  ForkJoinPool."
  [node tx-ops]
  (-> (np/-submit-tx node tx-ops)
      (ac/then-compose #(np/-tx-result node %))))

(defn changed-resources-publisher
  "Returns a publisher that publishes all changed resources of `type`."
  [node type]
  (np/-changed-resources-publisher node type))

(defn node
  "Returns the node of `db`."
  [db]
  (p/-node db))

(defn as-of
  "Returns the value of `db` as of some point `t`, inclusive."
  [db t]
  (p/-as-of db t))

(defn since
  "Returns the value of `db` since some point `t`, inclusive."
  [db t]
  (p/-since db t))

(defn since-t
  "Returns the value of `db` since some point `t`, inclusive."
  [db]
  (p/-since-t db))

(defn t
  "Returns the effective `t` of `db`."
  [db]
  (or (p/-as-of-t db) (p/-basis-t db)))

(defn basis-t
  "Returns the `t` of the most recent transaction reachable via `db`."
  [db]
  (p/-basis-t db))

(defn as-of-t
  "Returns the as-of point, or nil if none."
  [db]
  (p/-as-of-t db))

(defn tx
  "Returns the transaction of `t`."
  [node-or-db t]
  (p/-tx node-or-db t))

;; ---- Instance-Level Functions ----------------------------------------------

(defn resource-handle
  "Returns the resource handle with `type` and `id` or nil if its resource never
  existed.

  Handles of deleted resources have an :op of :delete.

  Please use `pull` to obtain the full resource."
  [db type id]
  (log/trace "fetch resource handle of" (str type "/" id))
  (p/-resource-handle db (codec/tid type) (codec/id-byte-string id)))

(defn resource-handle? [x]
  (rh/resource-handle? x))

(defn deleted?
  "Returns `true` if `resource-handle` is deleted."
  [resource-handle]
  (rh/deleted? resource-handle))

;; ---- Type-Level Functions --------------------------------------------------

(defn type-list
  "Returns a reducible collection of all resource handles of `type` in `db`.

  An optional `start-id` (inclusive) can be supplied.

  Please use `pull-many` to obtain the full resources."
  ([db type]
   (p/-type-list db (codec/tid type)))
  ([db type start-id]
   (p/-type-list db (codec/tid type) (codec/id-byte-string start-id))))

(defn type-total
  "Returns the number of all resources of `type` in `db`.

  This is O(1) instead of O(n) when counting the number of resources returned by
  `type-list`."
  [db type]
  (p/-type-total db (codec/tid type)))

(defn type-query
  "Returns a reducible collection of all resource handles of `type` in `db`
  matching `clauses`.

  A clause is a vector were the first element is a search param code and the
  following elements are values which are combined with logical or.

  Returns an anomaly if search parameters in clauses can't be resolved.

  An optional `start-id` (inclusive) can be supplied.

  Please use `pull-many` to obtain the full resources."
  ([db type clauses]
   (log/trace "Execute type query on" type)
   (when-ok [query (p/-compile-type-query db type clauses)]
     (p/-execute-query db query)))
  ([db type clauses start-id]
   (when-ok [query (p/-compile-type-query db type clauses)]
     (p/-execute-query db query start-id))))

(defn compile-type-query
  "Same as `type-query` but in a two-step process of pre-compilation and later
  execution by `execute-query`.

  Returns an anomaly if search parameters in clauses can't be resolved."
  [node-or-db type clauses]
  (p/-compile-type-query node-or-db type clauses))

(defn compile-type-query-lenient
  "Like `compile-type-query` but ignores clauses which refer to unknown search
  parameters.

  Returns an anomaly if search values are invalid."
  [node-or-db type clauses]
  (p/-compile-type-query-lenient node-or-db type clauses))

(defn compile-type-matcher
  "Returns a matcher that can be later used in `matcher-transducer` to obtain
  a transducer that will filter resource handles of `type` matching `clauses`.

  Returns an anomaly if search parameters in clauses can't be resolved."
  [node-or-db type clauses]
  (p/-compile-type-matcher node-or-db type clauses))

;; ---- System-Level Functions ------------------------------------------------

(defn system-list
  "Returns a reducible collection of all resource handles in `db`.

  An optional `start-type` (inclusive) and `start-id` (inclusive) can be
  supplied.

  Please use `pull-many` to obtain the full resources."
  ([db]
   (p/-system-list db))
  ([db start-type start-id]
   (p/-system-list db (codec/tid start-type) (codec/id-byte-string start-id))))

(defn system-total
  "Returns the number of all resources in `db`."
  [db]
  (p/-system-total db))

(defn system-query
  "Returns a reducible collection of all resource handles in `db` matching `clauses`.

  A clause is a vector were the first element is a search param code and the
  following elements are values which are combined with logical or.

  Returns an anomaly if search parameters in clauses can't be resolved.

  Please use `pull-many` to obtain the full resources."
  [db clauses]
  (when-ok [query (p/-compile-system-query db clauses)]
    (p/-execute-query db query)))

(defn compile-system-query
  "Same as `system-query` but in a two-step process of pre-compilation and later
  execution by `execute-query`.

  Returns an anomaly if search parameters in clauses can't be resolved."
  [node-or-db clauses]
  (p/-compile-system-query node-or-db clauses))

(defn compile-system-matcher
  "Returns a matcher that can be later used in `matcher-transducer` to obtain
  a transducer that will filter resource handles matching `clauses`.

  Returns an anomaly if search parameters in clauses can't be resolved."
  [node-or-db clauses]
  (p/-compile-system-matcher node-or-db clauses))

;; ---- Compartment-Level Functions -------------------------------------------

(defn- compartment [code id]
  [(codec/c-hash code) (codec/id-byte-string id)])

(defn list-compartment-resource-handles
  "Returns a reducible collection of all resource handles of `type` in `db`
  linked to the compartment with `code` and `id`.

  The code is the code of the compartment not necessary the same as a resource
  type. One common compartment is `Patient`.

  An optional `start-id` (inclusive) can be supplied.

  Example:

    (list-compartment-resource-handles db \"Patient\" \"0\" \"Observation\")

  Please use `pull-many` to obtain the full resources."
  ([db code id type]
   (p/-compartment-resource-handles db (compartment code id) (codec/tid type)))
  ([db code id type start-id]
   (p/-compartment-resource-handles db (compartment code id) (codec/tid type)
                                    (codec/id-byte-string start-id))))

(defn compartment-query
  "Returns a reducible collection of all resource handles of `type` in `db`
  matching `clauses` linked to the compartment with `code` and `id`.

  The code is the code of the compartment not necessary the same as a resource
  type. One common compartment is `Patient`.

  A clause is a vector were the first element is a search param code and the
  following elements are values with are combined with logical or.

  Returns an anomaly if search parameters in clauses can't be resolved.

  Please use `pull-many` to obtain the full resources."
  [db code id type clauses]
  (when-ok [query (p/-compile-compartment-query db code type clauses)]
    (p/-execute-query db query id)))

(defn compile-compartment-query
  "Same as `compartment-query` but in a two-step process of pre-compilation and
  later execution by `execute-query`. The `id` of the compartments (`code`)
  resource will be supplied as argument to `execute-query`.

  Returns an anomaly if search parameters in clauses can't be resolved."
  [node-or-db code type clauses]
  (p/-compile-compartment-query node-or-db code type clauses))

(defn compile-compartment-query-lenient
  "Like `compile-compartment-query` but ignores clauses which refer to unknown
  search parameters."
  [node-or-db code type clauses]
  (p/-compile-compartment-query-lenient node-or-db code type clauses))

;; ---- Patient-Compartment-Level Functions -----------------------------------

(defn patient-compartment-last-change-t
  "Returns the `t` of last change of any resource in the patient compartment or
  nil if the patient has no resources."
  [db patient-id]
  (p/-patient-compartment-last-change-t db (codec/id-byte-string patient-id)))

;; ---- Common Query Functions ------------------------------------------------

(defn count-query
  "Returns a CompletableFuture that will complete with the count of the
  matching resource handles."
  [db query]
  (p/-count-query db query))

(defn execute-query
  "Executes a pre-compiled `query` with `args` on `db`.

  Returns a reducible collection of all matching resource handles.

  See:
   * compile-type-query
   * compile-compartment-query

  Please use `pull-many` to obtain the full resources."
  {:arglists '([db query & args])}
  ([db query]
   (p/-execute-query db query))
  ([db query arg1]
   (p/-execute-query db query arg1)))

(defn explain-query
  "Returns the plan of `query`."
  [db query]
  (p/-explain-query db query))

(defn query-clauses
  "Returns the clauses used in `query`."
  [query]
  (p/-query-clauses query))

;; ---- Common Matcher Functions ----------------------------------------------

(defn matches? [db matcher resource-handle]
  (transduce
   (p/-matcher-transducer db matcher)
   (fn ([r] r) ([_ _] true))
   false
   [resource-handle]))

(defn matcher-transducer
  "Returns a transducer of `matcher` which operates on `db`."
  [db matcher]
  (p/-matcher-transducer db matcher))

(defn matcher-clauses
  "Returns the clauses of `matcher`."
  [matcher]
  (p/-matcher-clauses matcher))

;; ---- History Functions -----------------------------------------------------

(defn stop-history-at
  "Returns a transducer that stops reducing a collection of history entries at
  `instant`.

  Can be used with `instance-history`, `type-history` and `system-history`."
  [db instant]
  (p/-stop-history-at db instant))

;; ---- Instance-Level History Functions --------------------------------------

(defn instance-history
  "Returns a reducible collection of the history of the resource with the given
  `type` and `id` starting as-of `db` in reverse chronological order.

  The history optionally starts at `start-t` which defaults to the `t` of `db`.

  History entries are resource handles. Please use `pull-many` to obtain the
  full resources."
  ([db type id]
   (p/-instance-history db (codec/tid type) (codec/id-byte-string id) nil))
  ([db type id start-t]
   (p/-instance-history db (codec/tid type) (codec/id-byte-string id) start-t)))

(defn total-num-of-instance-changes
  "Returns the total number of changes (versions) of the resource with the given
  `type` and `id` starting as-of `db`.

  Optionally a `since` instant can be given to define a point in the past where
  the calculation should start."
  ([db type id]
   (p/-total-num-of-instance-changes db (codec/tid type)
                                     (codec/id-byte-string id) nil))
  ([db type id since]
   (p/-total-num-of-instance-changes db (codec/tid type)
                                     (codec/id-byte-string id) since)))

;; ---- Type-Level History Functions ------------------------------------------

(defn type-history
  "Returns a reducible collection of the history of resources with the given
  `type` starting as-of `db` in reverse chronological order.

  The history optionally starts at `start-t` which defaults to the `t` of `db`.

  History entries are resource handles. Please use `pull-many` to obtain the
  full resources."
  ([db type]
   (p/-type-history db (codec/tid type) nil nil))
  ([db type start-t]
   (p/-type-history db (codec/tid type) start-t nil))
  ([db type start-t start-id]
   (p/-type-history db (codec/tid type) start-t
                    (some-> start-id codec/id-byte-string))))

(defn total-num-of-type-changes
  "Returns the total number of changes (versions) of resources with the given
  `type` starting as-of `db`.

  Optionally a `since` instant can be given to define a point in the past where
  the calculation should start."
  ([db type]
   (p/-total-num-of-type-changes db type nil))
  ([db type since]
   (p/-total-num-of-type-changes db type since)))

;; ---- System-Level History Functions ----------------------------------------

(defn system-history
  "Returns a reducible collection of the history of all resources starting as-of
  `db` in reverse chronological order.

  The history optionally starts at `start-t` which defaults to the `t` of `db`.

  History entries are resource handles. Please use `pull-many` to obtain the
  full resources."
  ([db]
   (p/-system-history db nil nil nil))
  ([db start-t]
   (p/-system-history db start-t nil nil))
  ([db start-t start-type]
   (p/-system-history db start-t (some-> start-type codec/tid) nil))
  ([db start-t start-type start-id]
   (p/-system-history db start-t (some-> start-type codec/tid)
                      (some-> start-id codec/id-byte-string))))

(defn total-num-of-system-changes
  "Returns the total number of changes (versions) of resources starting as-of
  `db`.

  Optionally a `since` instant can be given to define a point in the past where
  the calculation should start."
  ([db]
   (p/-total-num-of-system-changes db nil))
  ([db since]
   (p/-total-num-of-system-changes db since)))

(defn changes
  "Returns a reducible collection of all resource handles changed at the `t` of
  `db`."
  [db]
  (p/-changes db))

;; ---- Include ---------------------------------------------------------------

(defn include
  "Returns a reducible collection of resource handles that are reachable from
  `resource-handle` by the search parameter with `code` and have a type of
  `target-type` (optional).

  The search parameter has to be of type reference.

  One example are Patients that are reachable from resource handle of type
  Observation by the search parameter with code subject."
  ([db resource-handle code]
   (p/-include db resource-handle code))
  ([db resource-handle code target-type]
   (p/-include db resource-handle code target-type)))

(defn rev-include
  "Returns a reducible collection of resource handles that point to
  `resource-handle` by the search parameter with `code` and have a type of
  `source-type`. 
   
  You can also omit `source-type` and `code` to get the resource handles of all
  resources regardless of type and search parameter pointing to
  `resource-handle`.

  The search parameter has to be of type reference.

  One example are Observations that point to resource handle of type Patient by
  the search parameter with code subject."
  ([db resource-handle]
   (p/-rev-include db resource-handle))
  ([db resource-handle source-type code]
   (p/-rev-include db resource-handle source-type code)))

(defn patient-everything
  "Returns a reducible collection of resource handles in the compartment of
  `patient-handle` including supporting resources like Practitioners,
  Medications, Locations, Organizations etc.

  The `patient-handle` itself is returned first."
  ([db patient-handle]
   (p/-patient-everything db patient-handle nil nil))
  ([db patient-handle start end]
   (p/-patient-everything db patient-handle start end)))

;; ---- Batch DB --------------------------------------------------------------

(defn new-batch-db
  "Returns a variant of this `db` which is optimized for batch processing.

  The batch database has to be closed after usage, because it holds resources
  that have to be freed."
  ^AutoCloseable
  [db]
  (p/-new-batch-db db))

;; ---- Pull ------------------------------------------------------------------

(defn pull
  "Returns a CompletableFuture that will complete with the resource of
  `resource-handle` or an anomaly in case of errors.

  Optional, a content `variant` like :complete or :summary can be given in order
  to pull only a subset of data.

  Note: If an deleted resource is pulled, a stub with type, id and meta will be
  returned.

  Functions applied after the returned future are executed on the common
  ForkJoinPool."
  ([node-or-db resource-handle]
   (p/-pull node-or-db resource-handle :complete))
  ([node-or-db resource-handle variant]
   (p/-pull node-or-db resource-handle variant)))

(defn pull-content
  "Returns a CompletableFuture that will complete with the resource content of
  `resource-handle` or an anomaly in case of errors.

  Compared to `pull`, the resource content doesn't contain :versionId and
  :lastUpdated in :meta and also not :blaze.db/t, :blaze.db/num-changes,
  :blaze.db/op and :blaze.db/tx in metadata."
  [node-or-db resource-handle]
  (p/-pull-content node-or-db resource-handle :complete))

(defn pull-many
  "Returns a CompletableFuture that will complete with a vector of all resources
  of all `resource-handles` in the same order.

  Optional, `variant` can be given which is either a content variant like
  :complete or :summary or a list of top-level keys to return instead of all
  keys (elements). Certain mandatory and modifier elements are returned
  regardless of if they are specified in `variant`. In addition the resources
  are marked with the tag SUBSETTED in this case.

  Returns a failed CompletableFuture if one pull fails."
  ([node-or-db resource-handles]
   (p/-pull-many node-or-db resource-handles :complete))
  ([node-or-db resource-handles variant]
   (p/-pull-many node-or-db resource-handles variant)))

;; ---- (Re) Index ------------------------------------------------------------

(defn re-index-total
  [db search-param-url]
  (p/-re-index-total db search-param-url))

(defn re-index
  "Indexes the first 10000 resources of the search param with `search-param-url`.

  Optionally starts at `start-type` and `start-id`.

  Returns a CompletableFuture that will complete with a map of:
  * :num-resources - the number of resources indexed
  * :next - the resource handle to continue with"
  ([db search-param-url]
   (p/-re-index db search-param-url))
  ([db search-param-url start-type start-id]
   (p/-re-index db search-param-url start-type start-id)))
