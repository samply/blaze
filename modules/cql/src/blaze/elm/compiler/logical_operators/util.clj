(ns blaze.elm.compiler.logical-operators.util
  (:require
   [blaze.elm.expression.cache.bloom-filter :as bloom-filter]))

(defn insert-sorted [cmp coll x]
  (loop [coll (seq coll) result []]
    (cond
      (empty? coll)
      (conj result x)

      (neg? (cmp x (first coll)))
      (into result (cons x coll))

      :else
      (recur (rest coll) (conj result (first coll))))))

(defn merge-sorted [cmp coll1 coll2]
  (loop [result []
         coll1 (seq coll1)
         coll2 (seq coll2)]
    (cond
      (and (empty? coll1) (empty? coll2)) result
      (empty? coll1) (into result coll2)
      (empty? coll2) (into result coll1)
      :else (let [x1 (first coll1)
                  x2 (first coll2)]
              (if (neg? (cmp x2 x1))
                (recur (conj result x2) coll1 (rest coll2))
                (recur (conj result x1) (rest coll1) coll2))))))

(defn and-attach-cache-result [op triples]
  [(fn []
     [(op (ffirst triples) (mapv first (rest triples)))
      (into [] (mapcat second) triples)])
   :and
   triples])

(defn or-attach-cache-result [op triples]
  [(fn []
     [(let [triples (reverse triples)]
        (op
         (vec
          (reduce
           (fn [[[_ last-bf] :as r] [op _ bf]]
             (cons
              (if (and last-bf bf)
                [op (bloom-filter/merge last-bf bf)]
                [op])
              r))
           (let [[[op _ bf]] triples]
             (when op (list [op bf])))
           (rest triples)))))
      (into [] (mapcat second) triples)])
   :or
   triples])
