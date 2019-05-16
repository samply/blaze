(ns blaze.datomic.util
  (:require
    [clojure.spec.alpha :as s]
    [datomic.api :as d]
    [datomic-spec.core :as ds])
  (:import
    [java.time Instant]
    [java.util Date]))


(s/fdef last-transaction
  :args (s/cat :resource ::ds/entity)
  :ret ::ds/entity)

(defn last-transaction
  "Returns the transaction of the last update of `resource`."
  {:arglists '([resource])}
  [{:db/keys [id] :as resource}]
  (let [db (d/entity-db resource)]
    (d/entity db (:tx (first (d/datoms db :eavt id :version))))))


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
