(ns blaze.datomic.util
  (:require
    [clojure.spec.alpha :as s]
    [datomic.api :as d]
    [datomic-spec.core :as ds])
  (:import
    [java.time Instant]
    [java.util Date]))


(s/fdef resource-type
  :args (s/cat :resource ::ds/entity)
  :ret string?)

(defn resource-type
  "Returns the type of a `resource` like \"Patient\" or \"Observation\"."
  {:arglists '([resource])}
  [{:db/keys [id] :as resource}]
  (name (d/ident (d/entity-db resource) (d/part id))))


(defn resource-id-attr [type]
  (keyword type "id"))


(s/fdef first-transaction
  :args (s/cat :resource ::ds/entity)
  :ret ::ds/entity)

(defn first-transaction
  "Returns the transaction of the creation of `resource`."
  {:arglists '([resource])}
  [{:db/keys [id] :as resource}]
  (let [db (d/entity-db resource)
        id-attr (resource-id-attr (resource-type resource))]
    (d/entity db (:tx (first (d/datoms db :eavt id id-attr))))))


(s/fdef last-transaction
  :args (s/cat :resource ::ds/entity)
  :ret ::ds/entity)

(defn last-transaction
  "Returns the transaction of the last update of `resource`."
  {:arglists '([resource])}
  [{eid :db/id :as resource}]
  (let [db (d/entity-db resource)]
    (d/entity db (:tx (first (d/datoms db :eavt eid :instance/version))))))


(s/fdef tx-instant
  :args (s/cat :transaction ::ds/entity))

(defn tx-instant
  "Returns the transaction instant as java.time.Instant instead of
  java.util.Date."
  [transaction]
  (Instant/ofEpochMilli (.getTime ^Date (:db/txInstant transaction))))


(s/fdef basis-transaction
  :args (s/cat :db ::ds/db)
  :ret ::ds/entity)

(defn basis-transaction
  "Returns the most recent transaction reachable via `db`."
  [db]
  (d/entity db (d/t->tx (d/basis-t db))))


(def ^:private metadata-cache (volatile! {}))


(s/fdef cached-entity
  :args (s/cat :db ::ds/db :eid ::ds/entity-identifier)
  :ret ::ds/entity)

(defn cached-entity
  "Returns a cached version of a Datomic entity.

  This is useful only for metadata because the cache key doesn't depend on t."
  [db eid]
  (if-let [entity (get @metadata-cache eid)]
    entity
    (-> (vswap! metadata-cache assoc eid (d/entity db eid))
        (get eid))))


(defn resource-ident [type id]
  [(resource-id-attr type) id])


(s/fdef resource
  :args (s/cat :db ::ds/db :type string? :id string?)
  :ret ::ds/entity)

(defn resource
  "Returns the resource with `type` and `id`.

  Also returns deleted resources. Please use the function `deleted?` to test
  for deleted resources."
  [db type id]
  (d/entity db (resource-ident type id)))


(defn deleted? [resource]
  (bit-test (:instance/version resource) 1))


(s/fdef ordinal-version
  :args (s/cat :resource ::ds/entity)
  :ret nat-int?)

(defn ordinal-version
  "Returns the strong monotonic increasing ordinal version of `resource`.

  Ordinal versions start with 1."
  [resource]
  (- (bit-shift-right (:instance/version resource) 2)))


(s/fdef list-resources
  :args (s/cat :db ::ds/db :type string?)
  :ret (s/coll-of ::ds/entity))

(defn list-resources
  "Returns a collection of all non-deleted resources of `type`."
  [db type]
  (into
    []
    (comp
      (map (fn [{eid :e}] (d/entity db eid)))
      (remove deleted?))
    (d/datoms db :aevt (resource-id-attr type))))


(s/fdef transaction-history
  :args (s/cat :db ::ds/db :eid ::ds/entity-identifier))

(defn transaction-history
  "Returns a reducible coll of all transactions on resource with `eid`.
  Newest first."
  [db eid]
  (eduction
    (filter :added)
    (map #(d/entity db (:tx %)))
    (d/datoms (d/history db) :eavt eid :instance/version)))


(s/fdef type-transaction-history
  :args (s/cat :db ::ds/db :type string?))

(defn type-transaction-history
  "Returns a reducible coll of all transactions on `type`.
  Newest first."
  [db type]
  (eduction
    (filter :added)
    (map #(d/entity db (:tx %)))
    (d/datoms (d/history db) :eavt (keyword type) :type/version)))


(defn system-transaction-history
  "Returns a reducible coll of all transactions in the whole system.
  Newest first."
  [db]
  (eduction
    (filter :added)
    (map #(d/entity db (:tx %)))
    (d/datoms (d/history db) :eavt :system :system/version)))


(s/fdef resource-type-total
  :args (s/cat :db ::ds/db :type string?)
  :ret nat-int?)

(defn resource-type-total [db type]
  (get (d/entity db (keyword type)) :total 0))
