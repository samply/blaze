(ns blaze.structure-definition
  (:require
    [cheshire.core :as json]
    [clojure.java.io :as io]))


(defn- read-structure-definition [file]
  (json/parse-string (slurp file) keyword))


(defn read-structure-definitions [dir]
  (reduce
    (fn [defs file]
      (let [{:keys [id] :as def} (read-structure-definition file)]
        (assoc defs id def)))
    {}
    (rest (file-seq (io/file dir)))))
