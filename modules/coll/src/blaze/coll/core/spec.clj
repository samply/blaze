(ns blaze.coll.core.spec
  (:refer-clojure :exclude [sorted?])
  (:require
   [blaze.coll.core :as coll]
   [clojure.spec.alpha :as s]))

(set! *warn-on-reflection* true)

(defn sorted? [coll]
  (if-let [first (coll/first coll)]
    (reduce
     (fn [x y]
       (if (pos? (.compareTo ^Comparable x y))
         (reduced false)
         y))
     first
     coll)
    true))

(s/def :blaze/sorted-iterable
  (s/and #(instance? Iterable %) sorted?))
