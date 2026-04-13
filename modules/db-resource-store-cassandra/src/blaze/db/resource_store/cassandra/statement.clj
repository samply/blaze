(ns blaze.db.resource-store.cassandra.statement
  (:import
   [com.datastax.oss.driver.api.core ConsistencyLevel DefaultConsistencyLevel]
   [com.datastax.oss.driver.api.core.cql SimpleStatement]))

(set! *warn-on-reflection* true)

(def get-statement
  "The get statement retrieves the content according to the `hash`.

  The consistency level is set to ONE for efficiency. Because rows are never
  updated, reading from a single node is sufficient in the common case where
  replication has already completed."
  (-> (SimpleStatement/builder "select content from resources where hash = ?")
      (.setConsistencyLevel ConsistencyLevel/ONE)
      (.build)))

(def get-quorum-statement
  "The get quorum statement retrieves the content according to the `hash`.

  The consistency level is set to QUORUM to guarantee that the read sees any
  previously acknowledged write. Used as a fallback when the ONE read returns
  not-found, which can happen when replication hasn't completed yet."
  (-> (SimpleStatement/builder "select content from resources where hash = ?")
      (.setConsistencyLevel ConsistencyLevel/QUORUM)
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
