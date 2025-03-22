(ns blaze.db.node.util
  (:require
   [blaze.db.impl.index.resource-handle :as rh]
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

(defn rs-key [resource-handle variant]
  [(rh/type resource-handle) (rh/hash resource-handle) variant])
