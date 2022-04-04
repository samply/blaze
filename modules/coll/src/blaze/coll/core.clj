(ns blaze.coll.core
  (:refer-clojure :exclude [count eduction empty? first])
  (:import
    [clojure.lang Counted IReduceInit Seqable Sequential]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn first
  "Like `clojure.core/first` but for reducible collections."
  [coll]
  (reduce (fn [_ x] (reduced x)) nil coll))


(defn empty?
  "Like `clojure.core/empty?` but for reducible collections."
  [coll]
  (nil? (first coll)))


(defn inc-rf [sum _] (inc ^long sum))


(defn eduction
  "Like `clojure.core/eduction` but faster."
  [xform coll]
  (reify
    Sequential
    IReduceInit
    (reduce [_ f init]
      (transduce xform (completing f) init coll))
    Seqable
    (seq [coll]
      (.seq ^Seqable (persistent! (.reduce coll conj! (transient [])))))
    Counted
    (count [coll]
      (.reduce coll inc-rf 0))))
