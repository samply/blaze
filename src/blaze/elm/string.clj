(ns blaze.elm.string
  "Implementation of the string type."
  (:require
    [blaze.elm.protocols :as p]))


(set! *warn-on-reflection* true)


;; 17.1. Combine
(defn combine
  "Like `clojure.string/join` but returns nil on first nil in `source`."
  ([source]
   (loop [sb (StringBuilder.)
          [s & more] source]
     (when s
       (if more
         (recur (.append sb s) more)
         (str (.append sb s))))))
  ([separator source]
   (loop [sb (StringBuilder.)
          [s & more] source]
     (when s
       (if more
         (recur (-> sb (.append s) (.append separator)) more)
         (str (.append sb s)))))))


;; 17.6. Indexer
(extend-protocol p/Indexer
  String
  (indexer [string index]
    (when (and index (<= 0 index) (< index (count string)))
      (.substring string index (inc index)))))
