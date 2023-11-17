(ns blaze.db.resource-store.cassandra.statement
  (:import
   [com.datastax.oss.driver.api.core ConsistencyLevel DefaultConsistencyLevel]
   [com.datastax.oss.driver.api.core.cql SimpleStatement]))

(set! *warn-on-reflection* true)

(def get-statement
  "The get statement retrieves the content according to the `hash`.

  The consistency level is set to ONE because reads are retried if nothing was
  found. Because rows are never updated, strong consistency isn't needed."
  (-> (SimpleStatement/builder "select content from resources where hash = ?")
      (.setConsistencyLevel ConsistencyLevel/ONE)
      (.build)))

(defn put-statement
  "The put statement upserts the `hash` and `content` of a resource into the
  `resources` table.

  The consistency level can be set to ONE or TWO depending on durability
  requirements. Strong consistency of QUORUM isn't needed, because rows are
  never updated. Reads will retry until they see a hash."
  ^SimpleStatement
  [consistency-level]
  (-> (SimpleStatement/builder "insert into resources (hash, content) values (?, ?)")
      (.setConsistencyLevel (DefaultConsistencyLevel/valueOf consistency-level))
      (.build)))
