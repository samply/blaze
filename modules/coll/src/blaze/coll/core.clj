(ns blaze.coll.core
  (:refer-clojure :exclude [count eduction empty? first nth some])
  (:require
   [clojure.core.protocols])
  (:import
   [blaze.coll IntersectionIterator UnionIterator]
   [clojure.lang Counted IReduceInit Indexed Sequential]
   [java.lang AutoCloseable]
   [java.util ArrayDeque Iterator Queue]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn first
  "Like `clojure.core/first` but for reducible collections."
  [coll]
  (reduce #(reduced %2) nil coll))

(defn some
  "Like `clojure.core/some` but for reducible collections."
  [pred coll]
  (reduce #(when (pred %2) (reduced true)) nil coll))

(defn empty?
  "Like `clojure.core/empty?` but for reducible collections."
  [coll]
  (identical? ::empty (reduce #(reduced %2) ::empty coll)))

(defn inc-rf [sum _] (inc ^long sum))

(deftype TransformerIterator [rf ^Iterator iter ^Queue buffer ^:volatile-mutable completed]
  Iterator
  (hasNext [_]
    (loop []
      (if (.isEmpty buffer)
        (cond
          completed false

          (.hasNext iter)
          (let [res (rf nil (.next iter))]
            (when (reduced? res)
              (rf nil)
              (set! completed true))
            (recur))

          :else
          (do
            (rf nil)
            (set! completed true)
            (recur)))
        true)))
  (next [i]
    (.hasNext i)
    (.remove buffer))
  AutoCloseable
  (close [_]
    (when (instance? AutoCloseable iter) (.close ^AutoCloseable iter))))

(defn eduction
  "Like `clojure.core/eduction` but implements Counted on top of IReduceInit and
  uses a TransformerIterator that closes closeable iterators."
  [xform coll]
  (reify
    Sequential
    IReduceInit
    (reduce [_ f init]
      (transduce xform (completing f) init coll))
    Counted
    (count [coll]
      (.reduce coll inc-rf 0))
    Iterable
    (iterator [_]
      (let [buffer (ArrayDeque.)
            rf (xform (completing (fn [_ x] (.add buffer x))))]
        (->TransformerIterator rf (.iterator ^Iterable coll) buffer false)))))

(defn count
  "Like `clojure.core/count` but works only for non-nil collections
  implementing `clojure.lang.Counted` like vectors."
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

(defmacro with-open-coll
  "Like `clojure.core/with-open` but opens and closes the resources on every
  reduce call to `coll`."
  [bindings coll]
  `(reify
     Sequential
     IReduceInit
     (reduce [_ rf# init#]
       (with-open ~bindings
         (reduce rf# init# ~coll)))
     Counted
     (count [coll#]
       (.reduce coll# inc-rf 0))))

(defn intersection
  "Returns a reducible and iterable collection of the intersection of `colls`.

  The `merge` function is applied to two items considered equal by `comparator`
  and its result is included in the final intersection.

  All `colls` have to be sorted according to `comparator`.

  Uses a divide-and-conquer tree approach for efficiency if called with more
  than two collections."
  {:arglists '([comparator merge colls])}
  ([comparator merge c1 c2]
   (reify
     Sequential
     IReduceInit
     (reduce [coll rf init]
       (with-open [iter ^AutoCloseable (.iterator coll)]
         (clojure.core.protocols/iterator-reduce! iter rf init)))
     Counted
     (count [coll]
       (.reduce coll inc-rf 0))
     Iterable
     (iterator [_]
       (IntersectionIterator. comparator merge (.iterator ^Iterable c1)
                              (.iterator ^Iterable c2)))))
  ([comparator merge c1 c2 & more]
   (letfn [(intersection-tree [colls]
             (condp = (count colls)
               1 (clojure.core/first colls)
               2 (apply intersection comparator merge colls)
               (let [mid (quot (count colls) 2)
                     left (subvec colls 0 mid)
                     right (subvec colls mid)]
                 (intersection comparator merge (intersection-tree left)
                               (intersection-tree right)))))]
     (intersection-tree (into [c1 c2] more)))))

(defn union
  "Returns a reducible and iterable collection of the union of `colls`.

  The `merge` function is applied to two items considered equal by `comparator`
  and its result is included in the final union.

  All `colls` have to be sorted according to `comparator`.

  Uses a divide-and-conquer tree approach for efficiency if called with more
  than two collections."
  {:arglists '([comparator merge colls])}
  ([comparator merge c1 c2]
   (reify
     Sequential
     IReduceInit
     (reduce [coll rf init]
       (with-open [iter ^AutoCloseable (.iterator coll)]
         (clojure.core.protocols/iterator-reduce! iter rf init)))
     Counted
     (count [coll]
       (.reduce coll inc-rf 0))
     Iterable
     (iterator [_]
       (UnionIterator. comparator merge (.iterator ^Iterable c1)
                       (.iterator ^Iterable c2)))))
  ([comparator merge c1 c2 & more]
   (letfn [(union-tree [colls]
             (condp = (count colls)
               1 (clojure.core/first colls)
               2 (apply union comparator merge colls)
               (let [mid (quot (count colls) 2)
                     left (subvec colls 0 mid)
                     right (subvec colls mid)]
                 (union comparator merge (union-tree left) (union-tree right)))))]
     (union-tree (into [c1 c2] more)))))
