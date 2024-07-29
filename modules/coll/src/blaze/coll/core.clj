(ns blaze.coll.core
  (:refer-clojure :exclude [count eduction empty? first nth some])
  (:import
   [clojure.lang Counted Indexed IReduceInit Sequential]))

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

(defn- inc-rf [sum _] (inc ^long sum))

(defn eduction
  "Like `clojure.core/eduction` but implements Counted instead of Iterable."
  [xform coll]
  (reify
    Sequential
    IReduceInit
    (reduce [_ f init]
      (transduce xform (completing f) init coll))
    Counted
    (count [coll]
      (.reduce coll inc-rf 0))))

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
         (reduce rf# init# ~coll)))))
