(ns blaze.db.api
  "Public Database API.

  A Database node provides access to a set of databases.

  A Database is an immutable, indexed set of Resources at a certain point in
  time.


  Instance-level, Type-level, system-level, compatment"
  (:require
    [blaze.anomaly :refer [when-ok]]
    [blaze.async-comp :as ac]
    [blaze.db.impl.protocols :as p])
  (:import
    [java.io Closeable])
  (:refer-clojure :exclude [sync]))


(defn db
  "Returns the most recent database known to this node.

  Does not block."
  [node]
  (p/-db node))


(defn sync
  "Returns a CompletionStage that completes when the database with at least the
  point in time `t` is available.

  The database could be of a newer point in time. Please use `as-of` afterwards
  if you want a database with exactly `t`."
  [node t]
  (p/-sync node t))


(defn transact
  "Submits `tx-ops` to the central transaction log and waits for the transaction
  to commit on `node`.

  Returns a CompletableFuture that completes with the database after the
  transaction in case of success or completes exceptionally with an anomaly in
  case of a transaction error or other errors."
  [node tx-ops]
  (-> (p/-submit-tx node tx-ops)
      (ac/then-compose #(p/-tx-result node %))))


(defn node
  "Returns the node of `db`."
  [db]
  (p/-node db))


(defn as-of
  "Returns the value of `db` as of some point `t`, inclusive."
  [db t]
  (p/-as-of db t))


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
  [db t]
  (p/-tx db t))



;; ---- Instance-Level Functions ----------------------------------------------

(defn resource
  "Returns the resource with the given `type` and `id` or a resource stub with
  :blaze.db/op set to :delete in metadata if it is known to be deleted or nil
  if it never existed.

  Deleted resources can also be tested using `deleted?`."
  [db type id]
  (p/-resource db type id))


(defn deleted?
  "Checks whether `resource` is deleted.

  Please note that the `resource` function can return deleted resources."
  [resource]
  (identical? :delete (:blaze.db/op (meta resource))))



;; ---- Type-Level Functions --------------------------------------------------

(defn list-resources
  "Returns a reducible collection of all resources of `type` in `db`.

  An optional `start-id` (inclusive) can be supplied."
  ([db type]
   (p/-list-resources db type nil))
  ([db type start-id]
   (p/-list-resources db type start-id)))


(defn type-total
  "Returns the number of all resources of `type` in `db`.

  This is O(1) instead of O(n) when counting the number of resources returned by
  `list-resources`."
  [db type]
  (p/-type-total db type))


(defn type-query
  "Returns a reducible collection of all resources of `type` in `db` matching
  `clauses`.

  A clause is a vector were the first element is a search param code and the
  following elements are values which are combined with logical or.

  Returns an anomaly if search parameters in clauses can't be resolved."
  [db type clauses]
  (when-ok [query (p/-compile-type-query db type clauses)]
    (p/-execute-query db query)))


(defn compile-type-query
  "Same as `type-query` but in a two step process of pre-compilation and later
  execution by `execute-query`.

  Returns an anomaly if search parameters in clauses can't be resolved."
  [node-or-db type clauses]
  (p/-compile-type-query node-or-db type clauses))



;; ---- System-Level Functions ------------------------------------------------

(defn system-list
  "Returns a reducible collection of all resources in `db`.

  An optional `start-type` (inclusive) and `start-id` (inclusive) can be
  supplied."
  ([db]
   (p/-system-list db nil nil))
  ([db start-type]
   (p/-system-list db start-type nil))
  ([db start-type start-id]
   (p/-system-list db start-type start-id)))


(defn system-total
  "Returns the number of all resources in `db`."
  [db]
  (p/-system-total db))


(defn system-query
  "Returns a reducible collection of all resources in `db` matching `clauses`.

  A clause is a vector were the first element is a search param code and the
  following elements are values which are combined with logical or.

  Returns an anomaly if search parameters in clauses can't be resolved."
  [db clauses]
  (when-ok [query (p/-compile-system-query db clauses)]
    (p/-execute-query db query)))


(defn compile-system-query
  "Same as `system-query` but in a two step process of pre-compilation and later
  execution by `execute-query`.

  Returns an anomaly if search parameters in clauses can't be resolved."
  [node-or-db clauses]
  (p/-compile-system-query node-or-db clauses))



;; ---- Compartment-Level Functions -------------------------------------------

(defn list-compartment-resources
  "Returns a reducible collection of all resources of `type` in `db` linked to
  the compartment with `code` and `id`.

  The code is the code of the compartment not necessary the same as a resource
  type. One common compartment is `Patient`.

  An optional `start-id` (inclusive) can be supplied.

  Example:

    (list-compartment-resources db \"Patient\" \"0\" \"Observation\")"
  ([db code id type]
   (p/-list-compartment-resources db code id type nil))
  ([db code id type start-id]
   (p/-list-compartment-resources db code id type start-id)))


(defn compartment-query
  "Returns a reducible collection of all resources of `type` in `db` matching
  `clauses` linked to the compartment with `code` and `id`.

  The code is the code of the compartment not necessary the same as a resource
  type. One common compartment is `Patient`.

  A clause is a vector were the first element is a search param code and the
  following elements are values with are combined with logical or.

  Returns an anomaly if search parameters in clauses can't be resolved."
  [db code id type clauses]
  (when-ok [query (p/-compile-compartment-query db code type clauses)]
    (p/-execute-query db query id)))


(defn compile-compartment-query
  "Same as `compartment-query` but in a two step process of pre-compilation and
  later execution by `execute-query`. The `id` of the compartments resource will
  be supplied as argument to `execute-query`.

  Returns an anomaly if search parameters in clauses can't be resolved."
  [node-or-db code type clauses]
  (p/-compile-compartment-query node-or-db code type clauses))



;; ---- Common Query Functions ------------------------------------------------

(defn execute-query
  "Executes a pre-compiled `query` with `args`.

  Returns a reducible collection of all matching resources.

  See:
   * compile-type-query
   * compile-compartment-query"
  {:arglists '([db query & args])}
  ([db query]
   (p/-execute-query db query))
  ([db query arg1]
   (p/-execute-query db query arg1)))



;; ---- Instance-Level History Functions --------------------------------------

(defn instance-history
  "Returns a reducible collection of the history of the resource with the given
  `type` and `id` starting as-of `db` in reverse chronological order.

  The history optionally starts at `start-t` which defaults to the `t` of `db`.
  Additionally a `since` instant can be given to define a point in the past
  where the history should start into the present."
  ([db type id]
   (p/-instance-history db type id nil nil))
  ([db type id start-t]
   (p/-instance-history db type id start-t nil))
  ([db type id start-t since]
   (p/-instance-history db type id start-t since)))


(defn total-num-of-instance-changes
  "Returns the total number of changes (versions) of the resource with the given
  `type` and `id` starting as-of `db`.

  Optionally a `since` instant can be given to define a point in the past where
  the calculation should start."
  ([db type id]
   (p/-total-num-of-instance-changes db type id nil))
  ([db type id since]
   (p/-total-num-of-instance-changes db type id since)))



;; ---- Type-Level History Functions ------------------------------------------

(defn type-history
  "Returns a reducible collection of the history of resources with the given
  `type` starting as-of `db` in reverse chronological order.

  The history optionally starts at `start-t` which defaults to the `t` of `db`.
  Additionally a `since` instant can be given to define a point in the past
  where the history should start into the present."
  ([db type]
   (p/-type-history db type nil nil nil))
  ([db type start-t]
   (p/-type-history db type start-t nil nil))
  ([db type start-t start-id]
   (p/-type-history db type start-t start-id nil))
  ([db type start-t start-id since]
   (p/-type-history db type start-t start-id since)))


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
  Additionally a `since` instant can be given to define a point in the past
  where the history should start into the present."
  ([db]
   (p/-system-history db nil nil nil nil))
  ([db start-t]
   (p/-system-history db start-t nil nil nil))
  ([db start-t start-type]
   (p/-system-history db start-t start-type nil nil))
  ([db start-t start-type start-id]
   (p/-system-history db start-t start-type start-id nil))
  ([db start-t start-type start-id since]
   (p/-system-history db start-t start-type start-id since)))


(defn total-num-of-system-changes
  "Returns the total number of changes (versions) of resources starting as-of
  `db`.

  Optionally a `since` instant can be given to define a point in the past where
  the calculation should start."
  ([db]
   (p/-total-num-of-system-changes db nil))
  ([db since]
   (p/-total-num-of-system-changes db since)))



;; ---- Batch DB --------------------------------------------------------------

(defn new-batch-db
  "Returns a variant of this `db` which is optimized for batch processing.

  The batch database has to be closed after usage, because it holds resources
  witch have to be freed."
  ^Closeable
  [db]
  (p/-new-batch-db db))
