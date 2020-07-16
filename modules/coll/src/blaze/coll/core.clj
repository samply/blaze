(ns blaze.coll.core
  (:import
    [clojure.lang IReduceInit Sequential])
  (:refer-clojure :exclude [eduction empty? first]))


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
      (transduce xform (completing f) init coll))))


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
