(ns blaze.db.impl.db.spec
  (:require
   [blaze.db.impl.db]
   [clojure.spec.alpha :as s])
  (:import
   [blaze.db.impl.db Db]))

(s/def :blaze.db.impl/db
  #(instance? Db %))
