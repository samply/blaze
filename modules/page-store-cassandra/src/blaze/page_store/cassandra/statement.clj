(ns blaze.page-store.cassandra.statement
  (:import
   [com.datastax.oss.driver.api.core ConsistencyLevel DefaultConsistencyLevel]
   [com.datastax.oss.driver.api.core.cql SimpleStatement]))

(set! *warn-on-reflection* true)

(def get-statement
  "The get statement retrieves the content according to the `token`.

  The consistency level is set to TWO because, together with the write
  consistency of TWO and a replication factor of 3, it guarantees strong
  consistency (2 + 2 = 4 > 3)."
  (-> (SimpleStatement/builder "select content from clauses where \"token\" = ?")
      (.setConsistencyLevel ConsistencyLevel/TWO)
      (.build)))

(defn put-statement
  "The put statement upserts the `token` and `content` of clauses into the
  `clauses` table.

  The consistency level can be set to ONE or TWO depending on durability
  requirements. With TWO, together with the read consistency of TWO and a
  replication factor of 3, strong consistency is guaranteed (2 + 2 = 4 > 3)."
  [consistency-level]
  (-> (SimpleStatement/builder "insert into clauses (\"token\", content) values (?, ?)")
      (.setConsistencyLevel (DefaultConsistencyLevel/valueOf consistency-level))
      (.build)))
