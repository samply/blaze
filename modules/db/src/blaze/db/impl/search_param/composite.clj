(ns blaze.db.impl.search-param.composite
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.search-param.composite.token-quantity :as tq]
   [blaze.db.impl.search-param.composite.token-token :as tt]
   [blaze.db.impl.search-param.core :as sc]
   [blaze.fhir-path :as fhir-path]
   [cognitect.anomalies :as anom]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(defn- resolve-search-param [index {url :definition :as component}]
  (if-let [search-param (get index url)]
    (assoc component :search-param search-param)
    {::anom/category ::anom/not-found
     :url url}))

(defn- resolve-search-params [index components]
  (transduce
   (comp (map (partial resolve-search-param index))
         (halt-when ba/anomaly?))
   conj
   components))

(defn- compile-expression [{:keys [expression] :as component}]
  (if-ok [expr (fhir-path/compile expression)]
    (assoc component :expression expr)
    #(assoc % :expression expression)))

(defn- compile-expressions [components]
  (transduce
   (comp (map compile-expression)
         (halt-when ba/anomaly?))
   conj
   components))

(defn- supported? [[c1 c2 & more]]
  (when-not (seq more)
    (and (= "token" (:type (:search-param c1)))
         (#{"token" "quantity"} (:type (:search-param c2))))))

(defn- prepare-components [index components]
  (when-ok [components (resolve-search-params index components)]
    (if (supported? components)
      (compile-expressions components)
      {::anom/category ::anom/unsupported})))

(defn- create-search-param
  [index {:keys [name url type base code] main-expression :expression
          components :component}]
  (when-ok [components (prepare-components index components)]
    (when-ok [main-expression (fhir-path/compile main-expression)]
      (let [[c1 c2] components]
        (case (:type (:search-param c2))
          "token"
          (tt/->SearchParamCompositeTokenToken name url type base code
                                               (codec/c-hash code)
                                               main-expression c1 c2)
          "quantity"
          (tq/->SearchParamCompositeTokenQuantity name url type base code
                                                  (codec/c-hash code)
                                                  main-expression c1 c2))))))

(defn- handle-anomaly [{:keys [url type]} anomaly]
  (log/warn (format "Skip creating search parameter `%s` of type `%s` because it is not implemented." url type))
  (assoc anomaly ::anom/category ::anom/unsupported))

(defmethod sc/search-param "composite"
  [{:keys [index]} search-param]
  (-> (create-search-param index search-param)
      (ba/exceptionally #(handle-anomaly search-param %))))
