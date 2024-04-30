(ns blaze.db.impl.search-param.composite.common
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.impl.protocols :as p]
   [blaze.fhir-path :as fhir-path]
   [clojure.string :as str])
  (:import
   [clojure.lang IReduceInit]))

(set! *warn-on-reflection* true)

(defn split-value
  "Returns the first and second value part in a vector or an anomaly if the
  second part is missing."
  [value]
  (let [res (str/split value #"\$" 2)]
    (if (< (count res) 2)
      (ba/incorrect (format "Miss the second part is composite search value `%s`." value))
      res)))

(defn compile-component-value [{:keys [search-param]} value]
  (p/-compile-value-composite search-param nil value))

(defn- component-index-values
  [resolver main-value {:keys [expression search-param]}]
  (when-ok [values (fhir-path/eval resolver expression main-value)]
    (coll/eduction
     (comp
      (p/-index-value-compiler search-param)
      (keep (fn [[modifier value]] (when (nil? modifier) value))))
     values)))

(defn index-values [resolver c1 c2]
  (comp
   (map
    (fn [main-value]
      (when-ok [c1-values (component-index-values resolver main-value c1)]
        (.reduce
         ^IReduceInit c1-values
         (fn [res v1]
           (if-ok [c2-values (component-index-values resolver main-value c2)]
             (.reduce
              ^IReduceInit c2-values
              (fn [res v2]
                (conj res [nil (bs/concat v1 v2)]))
              res)
             reduced))
         []))))
   (halt-when ba/anomaly?)
   cat))
