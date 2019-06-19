(ns blaze.deferred
  (:require
    [manifold.deferred :as md])
  (:refer-clojure :exclude [cat map mapcat]))


(defn- preserving-reduced
  [rf]
  #(let [ret (rf %1 %2)]
     (if (reduced? ret)
       (reduced ret)
       ret)))


(defn cat
  "Like `clojure.core/cat` but can handle deferred values."
  [rf]
  (let [rrf (preserving-reduced rf)]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result input]
       (-> result
           (md/chain'
             (fn [result]
               (md/chain' input #(reduce rrf result %)))))))))


(defn map
  [f]
  (fn [rf]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result input]
       (-> result
           (md/chain'
             (fn [result]
               (md/chain' input f #(rf result %)))))))))


(defn mapcat
  "Like `clojure.core/mapcat` but can handle deferred values."
  [f]
  (comp (map f) cat))



