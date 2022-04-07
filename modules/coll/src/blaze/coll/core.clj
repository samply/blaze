(ns blaze.coll.core
  (:refer-clojure :exclude [count eduction empty? first nth])
  (:import
    [clojure.lang Counted Indexed IReduceInit Seqable Sequential]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn first
  "Like `clojure.core/first` but for reducible collections."
  [coll]
  (reduce #(reduced %2) nil coll))


(defn empty?
  "Like `clojure.core/empty?` but for reducible collections."
  [coll]
  (nil? (first coll)))


(defn- inc-rf [sum _] (inc ^long sum))


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


(defn count
  "Like `clojure.core/count` but works only for non-nil collections
  implementing `clojure.lang.Countet` like vectors."
  {:inline
   (fn [coll]
     `(.count ~(with-meta coll {:tag `Counted})))}
  [coll]
  (.count ^Counted coll))


(defn nth
  "Like `clojure.core/nth` but works only for non-nil collections implementing
  `clojure.lang.Indexed` like vectors."
  {:inline
   (fn
     ([coll i]
      `(.nth ~(with-meta coll {:tag `Indexed}) (int ~i)))
     ([coll i not-found]
      `(.nth ~(with-meta coll {:tag `Indexed}) (int ~i) ~not-found)))}
  ([coll i]
   (.nth ^Indexed coll i))
  ([coll i not-found]
   (.nth ^Indexed coll i not-found)))
