(ns blaze.path
  (:refer-clojure :exclude [resolve])
  (:import
   [java.nio.file Files LinkOption Path]))

(set! *warn-on-reflection* true)

(defn path? [x]
  (instance? Path x))

(defn path [first & more]
  (Path/of first (into-array String more)))

(defn resolve
  ([path other]
   (.resolve ^Path path ^String other))
  ([path first second]
   (-> (resolve path first)
       (resolve second))))

(defn dir? [path]
  (Files/isDirectory ^Path path (into-array LinkOption [])))
