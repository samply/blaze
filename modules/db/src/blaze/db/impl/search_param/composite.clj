(ns blaze.db.impl.search-param.composite
  (:require
    [blaze.anomaly :refer [conj-anom when-ok]]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.search-param.composite.token-quantity :as tq]
    [blaze.db.impl.search-param.composite.token-token :as tt]
    [blaze.db.search-param-registry :as sr]
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
    (map #(resolve-search-param index %))
    conj-anom
    []
    components))


(defn- compile-expression [{:keys [expression] :as component}]
  (let [res (fhir-path/compile expression)]
    (if (::anom/category res)
      (assoc res :expression expression)
      (assoc component :expression res))))


(defn- compile-expressions [components]
  (transduce
    (map compile-expression)
    conj-anom
    []
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
  (log/debug (format "Skip creating search parameter `%s` of type `%s` because it is not implemented." url type))
  (assoc anomaly ::anom/category ::anom/unsupported))


(defmethod sr/search-param "composite"
  [index search-param]
  (let [res (create-search-param index search-param)]
    (if (::anom/category res)
      (handle-anomaly search-param res)
      res)))
