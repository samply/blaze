(ns blaze.db.api
  "Public Database API

  A Database node provides access a set of databases.

  A Database is an immutable, indexed set of Resources at a certain point in
  time."
  (:require
    [blaze.anomaly :refer [when-ok]]
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
  "Returns a database with at least the `t` specified.

  Returns a deferred."
  [node t]
  (p/-sync node t))


(defn submit-tx
  "Submits `tx-ops` to the central transaction log.

  Returns a success deferred with the database after the transaction or an
  error deferred with an anomaly."
  [node tx-ops]
  (p/-submit-tx node tx-ops))


(defn compile-type-query
  "Compiles a query, which when executed, will return a collection of all
  resources of `type` matching `clauses`.

  A clause is a vector were the first element is a search param code and the
  following elements are values which are combined with logical or.

  Returns an anomaly if search parameters in clauses can't be resolved."
  [node-or-db type clauses]
  (p/-compile-type-query node-or-db type clauses))


(defn compile-compartment-query
  "Compiles a query, which when executed, will return a collection of all
  resources of `type` matching `clauses` linked to the compartment with `code`
  and an id from its first argument.

  The code is the code of the compartment not necessary the same as a resource
  type. One common compartment is `Patient`.

  A clause is a vector were the first element is a search param code and the
  following elements are values with are combined with logical or.

  Returns an anomaly if search parameters in clauses can't be resolved."
  [node-or-db code type clauses]
  (p/-compile-compartment-query node-or-db code type clauses))


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


(defn resource-exists?
  "Returns true if the resource with given `type` and `id` exists in this
  database.

  If the resource is deleted, it does not count as existence."
  [db type id]
  (p/-resource-exists? db type id))


(defn resource
  "Returns the resource with the given `type` and `id` or a resource stub with
  :blaze.db/op set to :delete in metadata if it is known to be deleted or nil
  if it never existed.

  Deleted resources can also be tested using `deleted?`."
  [db type id]
  (p/-resource db type id))


(defn deleted? [resource]
  (identical? :delete (:blaze.db/op (meta resource))))


(defn list-resources
  "Returns a reducible collection of all non-deleted resources of `type`.

  An optional `start-id` (inclusive) can be supplied."
  ([db type]
   (p/-list-resources db type))
  ([db type start-id]
   (p/-list-resources db type start-id)))


(defn list-compartment-resources
  "Returns a reducible collection of all resources of `type` linked to the
  compartment with `code` and `id`.

  The code is the code of the compartment not necessary the same as a resource
  type. One common compartment is `Patient`.

  An optional `start-id` (inclusive) can be supplied.

  Example:

    (list-compartment-resources db \"Patient\" \"0\" \"Observation\")"
  ([db code id type]
   (p/-list-compartment-resources db code id type))
  ([db code id type start-id]
   (p/-list-compartment-resources db code id type start-id)))


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


(defn type-query
  "Returns a reducible collection of all resources of `type` matching `clauses`.

  A clause is a vector were the first element is a search param code and the
  following elements are values which are combined with logical or.

  Returns an anomaly if search parameters in clauses can't be resolved."
  [db type clauses]
  (when-ok [query (p/-compile-type-query db type clauses)]
    (p/-execute-query db query)))


(defn compartment-query
  "Returns a reducible collection of all resources of `type` matching `clauses`
  linked to the compartment with `code` and `id`.

  The code is the code of the compartment not necessary the same as a resource
  type. One common compartment is `Patient`.

  A clause is a vector were the first element is a search param code and the
  following elements are values with are combined with logical or.

  Returns an anomaly if search parameters in clauses can't be resolved."
  [db code id type clauses]
  (when-ok [query (p/-compile-compartment-query db code type clauses)]
    (p/-execute-query db query id)))


(defn instance-history
  "Returns a reducible collection of the history of the resource with the given
  `type` and `id` in reverse chronological order.

  Available options:
   * :start-t - t at which the history should start
   * :since   - instant"
  [db type id start-t since]
  (p/-instance-history db type id start-t since))


(defn type-history [db type start-t start-id since]
  (p/-type-history db type start-t start-id since))


(defn type-total [db type]
  (p/-type-total db type))


(defn total-num-of-instance-changes
  [db type id since]
  (p/-total-num-of-instance-changes db type id since))


(defn total-num-of-type-changes
  ([db type]
   (p/-total-num-of-type-changes db type nil))
  ([db type since]
   (p/-total-num-of-type-changes db type since)))


(defn system-total [db]
  (p/-system-total db))


(defn system-history [db start-t start-type start-id since]
  (p/-system-history db start-t start-type start-id since))


(defn total-num-of-system-changes [db since]
  (p/-total-num-of-system-changes db since))


(defn new-batch-db
  "Returns a variant of this `db` which is optimized for batch processing.

  The batch DB has to be closed after usage, because it holds resources with
  have to be freed."
  ^Closeable
  [db]
  (p/-new-batch-db db))


(defn ri-first
  "Like `first` but for reducible collections."
  [coll]
  (reduce (fn [_ x] (reduced x)) nil coll))


(defn ri-empty?
  "Like `empty?` but for reducible collections."
  [coll]
  (nil? (ri-first coll)))
