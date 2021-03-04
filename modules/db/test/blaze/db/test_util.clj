(ns blaze.db.test-util
  (:require
    [juxt.iota :refer [given]]))


(defmacro given-thrown [v & body]
  `(given (try ~v (catch Exception e# (ex-data e#)))
     ~@body))
