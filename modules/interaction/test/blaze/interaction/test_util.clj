(ns blaze.interaction.test-util
  (:require
    [blaze.log]
    [clojure.test :refer [is]]
    [juxt.iota :refer [given]]))


(defmacro given-thrown [v & body]
  `(given (try ~v (is false) (catch Exception e# (ex-data e#)))
     ~@body))
