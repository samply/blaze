(ns blaze.util
  (:refer-clojure :exclude [str])
  (:require
   [clojure.string :as str])
  (:import
   [blaze.util Str]))

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

(defn strip-leading-slashes
  "Strips all possible leading slashes from `s`."
  [s]
  (if (str/starts-with? s "/") (recur (subs s 1)) s))

(defn available-processors []
  (.availableProcessors (Runtime/getRuntime)))

(defn str
  "Like clojure.core/str but faster.

  With no args, returns the empty string. With one arg x, returns
  x.toString().  (str nil) returns the empty string. With more than
  one arg, returns the concatenation of the str values of the args."
  {:inline
   (fn
     ([x1]
      `(Str/string ~x1))
     ([x1 x2]
      `(Str/concat (str ~x1) (str ~x2)))
     ([x1 x2 x3]
      `(Str/concat (str ~x1) (str ~x2) (str ~x3)))
     ([x1 x2 x3 x4]
      `(Str/concat (str ~x1) (str ~x2) (str ~x3) (str ~x4)))
     ([x1 x2 x3 x4 x5]
      `(Str/concat (str ~x1) (str ~x2) (str ~x3) (str ~x4) (str ~x5)))
     ([x1 x2 x3 x4 x5 x6]
      `(Str/concat (str ~x1) (str ~x2) (str ~x3) (str ~x4) (str ~x5) (str ~x6))))
   :inline-arities #{1 2 3 4 5 6}}
  (^String [] "")
  (^String [^Object x]
   (if (nil? x) "" (.toString x)))
  (^String [x1 x2]
   (-> (StringBuilder.)
       (.append (str x1))
       (.append (str x2))
       (.toString)))
  (^String [x1 x2 x3]
   (-> (StringBuilder.)
       (.append (str x1))
       (.append (str x2))
       (.append (str x3))
       (.toString)))
  (^String [x1 x2 x3 x4]
   (-> (StringBuilder.)
       (.append (str x1))
       (.append (str x2))
       (.append (str x3))
       (.append (str x4))
       (.toString)))
  (^String [x1 x2 x3 x4 x5]
   (-> (StringBuilder.)
       (.append (str x1))
       (.append (str x2))
       (.append (str x3))
       (.append (str x4))
       (.append (str x5))
       (.toString)))
  (^String [x1 x2 x3 x4 x5 x6]
   (-> (StringBuilder.)
       (.append (str x1))
       (.append (str x2))
       (.append (str x3))
       (.append (str x4))
       (.append (str x5))
       (.append (str x6))
       (.toString)))
  (^String [x1 x2 x3 x4 x5 x6 & ys]
   ((fn [^StringBuilder sb more]
      (if more
        (recur (.append sb (str (first more))) (next more))
        (.toString sb)))
    (-> (StringBuilder.)
        (.append (str x1))
        (.append (str x2))
        (.append (str x3))
        (.append (str x4))
        (.append (str x5))
        (.append (str x6)))
    ys)))
