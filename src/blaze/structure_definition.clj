(ns blaze.structure-definition
  (:require
    [cheshire.core :as json]))


(defn- read-bundle [file]
  (json/parse-string (slurp file) keyword))


(defn- extract [kind bundle]
  (into
    []
    (comp
      (map :resource)
      (filter #(= kind (:kind %))))
    (:entry bundle)))


(defn read-structure-definitions [dir]
  (into
    (extract "complex-type" (read-bundle (str dir "/profiles-types.json")))
    (extract "resource" (read-bundle (str dir "/profiles-resources.json")))))


(defn read-other [dir file]
  (into
    []
    (comp
      (map #(get % "resource"))
      (map #(dissoc % "text")))
    (get (json/parse-string (slurp (str dir "/" file))) "entry")))
