(ns blaze.elm.compiler.clinical-operators.impl
  (:require
   [blaze.elm.value-set :as value-set]))

(defn contains-any? [value-set [code & codes]]
  (cond
    (nil? code) false
    (contains? value-set (value-set/from-code code)) true
    :else (recur value-set codes)))
