(ns blaze.terminology-service.local.filter
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.filter.core :as core]
   [blaze.terminology-service.local.filter.exists]
   [blaze.terminology-service.local.filter.is-a]))

(defn- priority [{:keys [op]}]
  (case (type/value op)
    "is-a" 0
    1))

(defn- order-filters [filters]
  (sort-by priority filters))

(defn- filter-concepts [filters concepts]
  (reduce
   (fn [concepts filter]
     (let [res (core/filter-concepts filter concepts)]
       (cond-> res (ba/anomaly? res) reduced)))
   concepts
   filters))

(defn expand-code-system
  "Returns a list of concepts as expansion of `code-system` according to the
  given `filters`."
  {:arglists '([code-system filters])}
  [{:keys [url] concepts :concept} filters]
  (when-ok [concepts (filter-concepts (order-filters filters) concepts)]
    (into
     []
     (map
      (fn [{:keys [code display]}]
        {:system url :code code :display display}))
     concepts)))
