(ns blaze.structure-definition
  (:require
    [cheshire.core :as json]
    [clojure.java.io :as io]))


(defn- read-bundle
  "Reads a bundle from classpath named `resource-name`."
  [resource-name]
  (with-open [rdr (io/reader (io/resource resource-name))]
    (json/parse-stream rdr keyword)))


(defn- extract [kind bundle]
  (into
    []
    (comp
      (map :resource)
      (filter #(= kind (:kind %))))
    (:entry bundle)))


(defn read-structure-definitions []
  (let [package "blaze/fhir/r4/structure-definitions"]
    (into
      (extract "complex-type" (read-bundle (str package "/profiles-types.json")))
      (into
        []
        (remove #(= "Parameters" (:name %)))
        (extract "resource" (read-bundle (str package "/profiles-resources.json")))))))
