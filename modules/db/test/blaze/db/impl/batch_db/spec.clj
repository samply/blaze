(ns blaze.db.impl.batch-db.spec
  (:require
   [blaze.db.impl.batch-db]
   [clojure.spec.alpha :as s])
  (:import
   [blaze.db.impl.batch_db BatchDb]))

(s/def :blaze.db.impl/batch-db
  #(instance? BatchDb %))
