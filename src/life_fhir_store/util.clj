(ns life-fhir-store.util
  (:require
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [life-fhir-store.structure-definition :refer [structure-definition]]))


(defn- read-structure-definition [file]
  (structure-definition (json/parse-string (slurp file) keyword)))


(defn read-structure-definitions [path]
  (reduce
    (fn [ret file]
      (let [{:life.structure-definition/keys [id] :as def}
            (read-structure-definition file)]
        (assoc ret id def)))
    {}
    (rest (file-seq (io/file path)))))
