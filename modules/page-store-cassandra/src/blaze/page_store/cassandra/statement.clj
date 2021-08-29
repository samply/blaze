(ns blaze.page-store.cassandra.statement
  (:import
    [com.datastax.oss.driver.api.core ConsistencyLevel DefaultConsistencyLevel]
    [com.datastax.oss.driver.api.core.cql SimpleStatement]))


(def get-statement
  "The get statement retrieves the content according to the `token`.

  The consistency level is set to ONE because reads are retried if nothing was
  found. Because rows are never updated, strong consistency isn't needed."
  (-> (SimpleStatement/builder "select content from clauses where \"token\" = ?")
      (.setConsistencyLevel ConsistencyLevel/ONE)
      (.build)))


(defn put-statement
  "The put statement upserts the `token` and `content` of clauses into the
  `clauses` table.

  The consistency level can be set to ONE or TWO depending on durability
  requirements. Strong consistency of QUORUM isn't needed, because rows are
  never updated. Reads will retry until they see a token."
  [consistency-level]
  (-> (SimpleStatement/builder "insert into clauses (\"token\", content) values (?, ?)")
      (.setConsistencyLevel (DefaultConsistencyLevel/valueOf consistency-level))
      (.build)))
