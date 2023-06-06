(ns blaze.util)


(set! *warn-on-reflection* true)


(def conj-vec (fnil conj []))


(defn duration-s
  "Returns the duration in seconds from a System/nanoTime `start`."
  [start]
  (/ (double (- (System/nanoTime) start)) 1e9))
