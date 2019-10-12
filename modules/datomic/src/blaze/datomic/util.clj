(ns blaze.datomic.util
  (:require
    [clojure.spec.alpha :as s]
    [datomic.api :as d]
    [datomic-spec.core :as ds])
  (:import
    [java.time Instant]
    [java.util Date]))


(s/fdef entity-type*
  :args (s/cat :db ::ds/db :eid ::ds/entity-id)
  :ret string?)

(defn entity-type*
  [db eid]
  (name (d/ident db (d/part eid))))


(s/fdef entity-type
  :args (s/cat :entity ::ds/entity)
  :ret string?)

(defn entity-type
  "Returns the type of a `resource` like \"Patient\" or \"Observation\" or the
  type of a non-primitive type like \"CodeableConcept\"."
  {:arglists '([resource])}
  [{:db/keys [id] :as resource}]
  (entity-type* (d/entity-db resource) id))


(defn resource-id-attr [type]
  (keyword type "id"))


(s/fdef literal-reference
  :args (s/cat :resource ::ds/entity)
  :ret (s/tuple string? string?))

(defn literal-reference
  "Returns a tuple of type and id of `resource`."
  [resource]
  (let [type (entity-type resource)]
    [type ((resource-id-attr type) resource)]))


(s/fdef first-transaction
  :args (s/cat :resource ::ds/entity)
  :ret ::ds/entity)

(defn first-transaction
  "Returns the transaction of the creation of `resource`."
  {:arglists '([resource])}
  [{:db/keys [id] :as resource}]
  (let [db (d/entity-db resource)
        id-attr (resource-id-attr (entity-type resource))]
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
  :ret (s/nilable ::ds/entity))

(defn resource
  "Returns the resource with `type` and `id` or `nil` if nothing was found.

  Also returns deleted resources. Please use the function `deleted?` to test
  for deleted resources."
  [db type id]
  (d/entity db (resource-ident type id)))


(s/fdef resource-by
  :args (s/cat :db ::ds/db :attr keyword? :val some?)
  :ret (s/nilable ::ds/entity))

(defn resource-by
  "Returns the resource by using the index on `attr` or `nil` if nothing was
  found."
  [db attr val]
  (when-let [eid (:e (first (d/datoms db :avet attr val)))]
    (d/entity db eid)))


(s/fdef deleted?
  :args (s/cat :resource ::ds/entity)
  :ret boolean?)

(defn deleted?
  "Returns true iff the resource is currently deleted."
  [resource]
  (bit-test (:instance/version resource) 1))


(s/fdef initial-version?
  :args (s/cat :resource ::ds/entity)
  :ret boolean?)

(defn initial-version?
  "Returns true iff the resource is in it's initial version."
  [resource]
  (#{-3 -4} (:instance/version resource)))


(s/fdef initial-version-server-assigned-id?
  :args (s/cat :resource ::ds/entity)
  :ret boolean?)

(defn initial-version-server-assigned-id?
  "Returns true iff the resource is in it's initial version and had it's
  literal id server assigned."
  [resource]
  (= -3 (:instance/version resource)))


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


(s/fdef instance-transaction-history
  :args (s/cat :db ::ds/db :eid ::ds/entity-identifier)
  :ret (s/coll-of ::ds/entity))

(defn instance-transaction-history
  "Returns a reducible coll of all transactions on resource with `eid`.
  Newest first."
  [db eid]
  (eduction
    (filter :added)
    (map #(d/entity db (:tx %)))
    (d/datoms (d/history db) :eavt eid :instance/version)))


(s/fdef instance-version
  :args (s/cat :resource ::ds/entity)
  :ret nat-int?)

(defn instance-version
  "Returns the strong monotonic increasing ordinal version of `resource`.

  Ordinal versions start with 1."
  [resource]
  (- (bit-shift-right (:instance/version resource 0) 2)))


(s/fdef type-transaction-history
  :args (s/cat :db ::ds/db :type string?)
  :ret (s/coll-of ::ds/entity))

(defn type-transaction-history
  "Returns a reducible coll of all transactions on `type`.
  Newest first."
  [db type]
  (eduction
    (filter :added)
    (map #(d/entity db (:tx %)))
    (d/datoms (d/history db) :eavt (keyword type) :type/version)))


(s/fdef system-transaction-history
  :args (s/cat :db ::ds/db)
  :ret (s/coll-of ::ds/entity))

(defn system-transaction-history
  "Returns a reducible coll of all transactions in the whole system.
  Newest first."
  [db]
  (eduction
    (filter :added)
    (map #(d/entity db (:tx %)))
    (d/datoms (d/history db) :eavt :system :system/version)))


(s/fdef type-total
  :args (s/cat :db ::ds/db :type string?)
  :ret nat-int?)

(defn type-total
  "Returns the total number of resources with `type` in `db`."
  [db type]
  (- (:type/total (d/entity db (keyword type)) 0)))


(s/fdef system-version
  :args (s/cat :db ::ds/db)
  :ret nat-int?)

(defn system-version
  "Returns the number of resource changes in the whole system."
  [db]
  (- (:system/version (d/entity db :system) 0)))


(s/fdef type-version
  :args (s/cat :db ::ds/db :type string?)
  :ret nat-int?)

(defn type-version
  "Returns the number of resource changes of `type`."
  [db type]
  (- (:type/version (d/entity db (keyword type)) 0)))
