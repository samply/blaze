(ns blaze.coll.core
  (:refer-clojure :exclude [count eduction empty? first])
  (:import
    [blaze.coll.core Eduction]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn first
  "Like `clojure.core/first` but for reducible collections."
  [coll]
  (reduce (fn [_ x] (reduced x)) nil coll))


(defn empty?
  "Like `clojure.core/empty?` but for reducible collections."
  [coll]
  (reduce (fn [_ _] (reduced false)) true coll))


(defn eduction
  "Like `clojure.core/eduction` but faster."
  [xform coll]
  (Eduction. xform coll))
