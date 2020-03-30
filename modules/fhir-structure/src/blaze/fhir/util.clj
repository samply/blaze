(ns blaze.fhir.util
  (:require
    [cheshire.core :as json]
    [clojure.java.io :as io]))


(defn read-bundle
  "Reads a bundle from classpath named `resource-name`."
  [resource-name]
  (with-open [rdr (io/reader (io/resource resource-name))]
    (json/parse-stream rdr keyword)))


(defn extract-all [bundle]
  (into
    []
    (comp
      (map :resource))
    (:entry bundle)))


(defn data-types []
  (extract-all (read-bundle "blaze/fhir/r4/profiles-types.json")))


(defn primitive-types []
  (filterv (comp #{"primitive-type"} :kind) (data-types)))


(defn complex-types []
  (->> (data-types)
       (remove :abstract)
       ;; TODO: look into how to handle this special quantity types
       (remove (comp #{"MoneyQuantity" "SimpleQuantity"} :name))
       (filterv (comp #{"complex-type"} :kind))))


(defn resources []
  (->> (extract-all (read-bundle "blaze/fhir/r4/profiles-resources.json"))
       (filterv (comp #{"resource"} :kind))))


(defn with-name [name struct-defs]
  (some #(when (= name (:name %)) %) struct-defs))
