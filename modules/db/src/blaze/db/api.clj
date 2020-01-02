(ns blaze.db.api
  "Public API"
  (:import
    [clojure.lang IReduceInit])
  (:refer-clojure :exclude [sync]))


(defprotocol Node
  "A Database node provides access a set of databases."

  (-db [node]
    "Returns the most recent database known to this node.

    Does not block.")

  (sync [node t]
    "Returns a database with at least the `t` specified. Returns a deferred.")

  (-submit-tx [node tx-ops])

  (-compartment-query-batch [_ code type clauses]))


(defn db [node]
  (-db node))


(defn submit-tx
  "Submits `tx-ops` to the central transaction log. Returns a success deferred
  with the database after the transaction or an error deferred with an anomaly."
  [node tx-ops]
  (-submit-tx node tx-ops))


(defn compartment-query-batch
  "Returns a function taking a `db` and an `id` of a compartment. This is a
  batch variant of `compartment-query`. The returned function closes over the
  common arguments `code`, `type` and `clauses` and takes the remaining
  arguments `db` and `id` through individual calls.

  The code is the code of the compartment not necessary the same as a resource
  type. One common compartment is `Patient`.

  Returns an anomaly if search parameters in clauses can't be resolved."
  [node code type clauses]
  (-compartment-query-batch node code type clauses))


(defprotocol Db
  "A Database is an immutable, indexed set of Resources at a certain point in
  time."

  (as-of [db t]
    "Returns the value of the database as of some point `t`, inclusive.")

  (basis-t [db]
    "Returns the t of the most recent transaction reachable via this db.")

  (as-of-t [db]
    "Returns the as-of point, or nil if none.")

  (-tx [db t]
    "Returns the transaction of `t`.")

  (-resource-exists? [db type id])

  (-resource [db type id])

  (-list-resources [db type] [db type start-id])

  (-list-compartment-resources [db code id type] [db code id type start-id])

  (-type-query [db type clauses])

  (-compartment-query [db code id type clauses])

  (type-total [db type])

  (-instance-history [db type id start-t since])

  (total-num-of-instance-changes [_ type id since])

  (type-history [db type start-t start-id since])

  (-total-num-of-type-changes [_ type since])

  (system-history [db start-t start-type start-id since])

  (total-num-of-system-changes [_ since]))


(defn tx [db t]
  (-tx db t))


(defn resource-exists?
  "Returns true if the resource with given `type` and `id` exists in this
  database.

  If the resource is deleted, it does not count as existence."
  [db type id]
  (-resource-exists? db type id))


(defn resource
  "Returns the resource with the given `type` and `id` or a resource stub with
  :blaze.db/op set to :delete in metadata if it is known to be deleted or nil
  if it never existed.

  Deleted resources can also be tested using `deleted?`."
  [db type id]
  (-resource db type id))


(defn deleted? [resource]
  (identical? :delete (:blaze.db/op (meta resource))))


(defn list-resources
  "Returns a reducible collection of all non-deleted resources of `type`.

  An optional `start-id` (inclusive) can be supplied."
  ([db type]
   (-list-resources db type))
  ([db type start-id]
   (-list-resources db type start-id)))


(defn list-compartment-resources
  "Returns a reducible collection of all non-deleted resources linked to
  `compartment` and of `type`.

  An optional `start-id` (inclusive) can be supplied."
  ([db code id type]
   (-list-compartment-resources db code id type))
  ([db code id type start-id]
   (-list-compartment-resources db code id type start-id)))


(defn type-query [db type clauses]
  (-type-query db type clauses))


(defn compartment-query
  "Searches for resources of `type` in compartment specified by `code` and `id`
  with `clauses`.

  The code is the code of the compartment not necessary the same as a resource
  type. One common compartment is `Patient`.

  Returns an anomaly if search parameters in clauses can't be resolved."
  [db code id type clauses]
  (-compartment-query db code id type clauses))


(defn instance-history
  "Returns a reducible collection of the history of the resource with the given
  `type` and `id` in reverse chronological order.

  Available options:
   * :start-t - t at which the history should start
   * :since   - instant"
  [db type id start-t since]
  (-instance-history db type id start-t since))


(defn total-num-of-type-changes
  ([db type]
   (-total-num-of-type-changes db type nil))
  ([db type since]
   (-total-num-of-type-changes db type since)))


(defn ri-first
  "Like `first` for `IReduceInit` collections."
  [^IReduceInit coll]
  (.reduce coll (fn [_ x] (reduced x)) nil))
