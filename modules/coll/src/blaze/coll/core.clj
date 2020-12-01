(ns blaze.coll.core
  (:import
    [clojure.lang Counted IReduceInit Seqable Sequential])
  (:refer-clojure :exclude [count eduction empty? first]))


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
      (.reduce coll (fn ^long [^long sum _] (inc sum)) 0))))


(defn first-by
  "Like partition-by but only returns the first element of each partition.

  Same as `(comp (partition-by pred) (map first))`."
  ([f]
   (fn [rf]
     (let [fi (volatile! ::none)
           pv (volatile! ::none)]
       (fn
         ([] (rf))
         ([result]
          (let [result (if (identical? @fi ::none)
                         result
                         (let [v @fi]
                           ;;clear first!
                           (vreset! fi ::none)
                           (unreduced (rf result v))))]
            (rf result)))
         ([result input]
          (let [pval @pv
                val (f input)]
            (vreset! pv val)
            (if (or (identical? pval ::none)
                    (.equals ^Object val pval))
              (do
                (when (identical? @fi ::none)
                  (vreset! fi input))
                result)
              (let [v @fi
                    ret (rf result v)]
                (if (reduced? ret)
                  (vreset! fi ::none)
                  (vreset! fi input))
                ret)))))))))
