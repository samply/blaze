(ns blaze.db.test-util
  (:require
    [clojure.test :as test :refer [is]]
    [juxt.iota :refer [given]]))


(defmacro given-thrown [v & body]
  `(given (try ~v (is false) (catch Exception e# (ex-data e#)))
     ~@body))
