(ns blaze.db.node.util
  (:require
   [clojure.string :as str]))

(defn name-part [[_ key]]
  (-> key namespace (str/split #"\.") last))

(defn component-name [key suffix]
  (cond->> suffix
    (vector? key)
    (str (name-part key) " ")))

(defn thread-name-template [key suffix]
  (cond->> suffix
    (vector? key)
    (str (name-part key) "-")))
