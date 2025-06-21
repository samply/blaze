(ns blaze.util
  (:require
   [clojure.string :as str])
  (:import
   [com.google.common.base CaseFormat]))

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

(defn camel->kebab [s]
  (.to CaseFormat/LOWER_CAMEL CaseFormat/LOWER_HYPHEN s))
