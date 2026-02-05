(ns blaze.db.impl.query.system
  (:require
   [blaze.db.impl.index :as index]
   [blaze.db.impl.protocols :as p]))

(defrecord SystemQuery [clauses]
  p/Query
  (-execute [_ batch-db]
    (index/system-query batch-db clauses)))
