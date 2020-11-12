(ns blaze.db.impl.search-param.composite.common
  (:require
    [blaze.anomaly :refer [when-ok]]
    [blaze.byte-string :as bs]
    [blaze.db.impl.protocols :as p]
    [blaze.fhir-path :as fhir-path]
    [clojure.string :as str]))


(defn split-value [value]
  (str/split value #"\$" 2))


(defn compile-component-value [{:keys [search-param]} value]
  (p/-compile-value search-param nil value))


(defn- component-index-values
  [resolver main-value {:keys [expression search-param]}]
  (when-ok [values (fhir-path/eval resolver expression main-value)]
    (into
      []
      (comp (filter (fn [[modifier]] (nil? modifier)))
                   (map (fn [[_ value]] value)))
      (p/-compile-index-values search-param values))))


(defn index-values [resolver main-value c1 c2]
  (for [v1 (component-index-values resolver main-value c1)
        v2 (component-index-values resolver main-value c2)]
    [nil (bs/concat v1 v2)]))
