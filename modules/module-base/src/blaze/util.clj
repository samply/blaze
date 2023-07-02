(ns blaze.util)


(set! *warn-on-reflection* true)


(def conj-vec (fnil conj []))


(defn duration-s
  "Returns the duration in seconds from a System/nanoTime `start`."
  [start]
  (/ (double (- (System/nanoTime) start)) 1e9))


(defn to-seq
  "Coerces `x` to a sequence."
  [x]
  (if (or (nil? x) (sequential? x)) x [x]))
