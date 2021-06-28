(ns blaze.fhir.util
  (:require
    [clojure.java.io :as io]
    [jsonista.core :as j]
    [taoensso.timbre :as log]))


(def ^:private object-mapper
  (j/object-mapper
    {:decode-key-fn true}))


(defn read-bundle
  "Reads a bundle from classpath named `resource-name`."
  [resource-name]
  (log/trace (format "Read FHIR bundle `%s`." resource-name))
  (with-open [rdr (io/reader (io/resource resource-name))]
    (j/read-value rdr object-mapper)))


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
