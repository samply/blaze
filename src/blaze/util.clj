(ns blaze.util
  (:require
    [clojure.string :as str]))


(defn title-case [s]
  (str (str/upper-case (subs s 0 1)) (subs s 1)))
