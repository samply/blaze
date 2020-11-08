(ns blaze.db.impl.search-param.composite
  (:require
    [blaze.anomaly :refer [conj-anom if-ok when-ok]]
    [blaze.coll.core :as coll]
    [blaze.db.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.protocols :as p]
    [blaze.db.impl.search-param.quantity :as spq]
    [blaze.db.impl.search-param.token :as spt]
    [blaze.db.impl.search-param.util :as u]
    [blaze.db.search-param-registry :as sr]
    [blaze.fhir-path :as fhir-path]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [taoensso.timbre :as log]))


(set! *warn-on-reflection* true)


(defn- split-value [value]
  (str/split value #"\$" 2))


(defn- compile-component-value [{:keys [search-param]} value]
  (p/-compile-value search-param value))


(defn- component-index-values
  [resolver main-value {:keys [expression search-param]}]
  (when-ok [values (fhir-path/eval resolver expression main-value)]
    (into
      []
      (comp (filter (fn [[modifier]] (nil? modifier)))
            (map (fn [[_ value]] value)))
      (p/-compile-index-values search-param values))))


(defn- index-values [resolver main-value c1 c2]
  (for [v1 (component-index-values resolver main-value c1)
        v2 (component-index-values resolver main-value c2)]
    [nil (bytes/concat v1 v2)]))


(defrecord SearchParamCompositeTokenToken
  [name url type base code c-hash main-expression c1 c2]
  p/SearchParam
  (-compile-value [_ value]
    (let [[v1 v2] (split-value value)
          v1 (compile-component-value c1 v1)
          v2 (compile-component-value c1 v2)]
      (bytes/concat v1 v2)))

  (-resource-handles [_ context tid _ value start-id]
    (coll/eduction
      (u/resource-handle-mapper context tid)
      (spt/resource-keys context c-hash tid value start-id)))

  (-matches? [_ context tid id hash _ values]
    (some #(spt/matches? context c-hash tid id hash %) values))

  (-index-values [_ resolver resource]
    (when-ok [values (fhir-path/eval resolver main-expression resource)]
      (into [] (mapcat #(index-values resolver % c1 c2)) values))))


(def ^:private ^:const ^int token-quantity-prefix-length
  (* 2 codec/v-hash-size))


(defrecord SearchParamCompositeTokenQuantity
  [name url type base code c-hash main-expression c1 c2]
  p/SearchParam
  (-compile-value [_ value]
    (let [[v1 v2] (split-value value)
          token-value (compile-component-value c1 v1)]
      ;; the second component is always of type quantity
      (if-ok [quantity-value (compile-component-value c2 v2)]
        (let [[op lower-bound exact-value upper-bound] quantity-value]
          [op
           (bytes/concat token-value lower-bound)
           (bytes/concat token-value exact-value)
           (bytes/concat token-value upper-bound)])
        (case (::spq/category quantity-value)
          ::spq/invalid-decimal-value
          (assoc quantity-value
            ::anom/message (spq/invalid-decimal-value-msg code v2))
          ::spq/unsupported-prefix
          (assoc quantity-value
            ::anom/message
            (spq/unsupported-prefix-msg
              code (::spq/unsupported-prefix quantity-value)))
          quantity-value))))

  (-resource-handles [_ context tid _ value start-id]
    (coll/eduction
      (u/resource-handle-mapper context tid)
      (spq/resource-keys context c-hash tid token-quantity-prefix-length value
                         start-id)))

  (-matches? [_ context tid id hash _ values]
    (some
      #(spq/matches? context c-hash tid id hash token-quantity-prefix-length %)
      values))

  (-index-values [_ resolver resource]
    (when-ok [values (fhir-path/eval resolver main-expression resource)]
      (into [] (mapcat #(index-values resolver % c1 c2)) values))))


(defn- resolve-definition [index {url :definition :as component}]
  (if-let [search-param (get index url)]
    (assoc component :search-param search-param)
    {::anom/category ::anom/not-found
     :url url}))


(defn- resolve-definitions [index components]
  (transduce
    (map #(resolve-definition index %))
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
  (when-ok [components (resolve-definitions index components)]
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
          (->SearchParamCompositeTokenToken name url type base code
                                            (codec/c-hash code)
                                            main-expression c1 c2)
          "quantity"
          (->SearchParamCompositeTokenQuantity name url type base code
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
