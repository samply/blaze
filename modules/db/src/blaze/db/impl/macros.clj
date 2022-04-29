(ns blaze.db.impl.macros
  (:import
    [clojure.lang Counted IReduceInit Seqable Sequential]))


(defn inc-rf [sum _] (inc ^long sum))


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
     Seqable
     (seq [this#]
       (.seq ^Seqable (persistent! (.reduce this# conj! (transient [])))))
     Counted
     (count [this#]
       (.reduce this# inc-rf 0))))
