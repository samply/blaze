(ns blaze.path
  (:import
   [java.nio.file Files LinkOption Path]))

(set! *warn-on-reflection* true)

(defn path? [x]
  (instance? Path x))

(defn path [first & more]
  (Path/of first (into-array String more)))

(defn dir? [path]
  (Files/isDirectory ^Path path (into-array LinkOption [])))
