(ns blaze.interaction.util
  (:require
    [blaze.anomaly :as ba]
    [blaze.db.api :as d]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.luid :as luid]
    [clojure.string :as str]
    [cuerdas.core :as c-str]))


(defn etag->t [etag]
  (let [[_ t] (re-find #"W/\"(\d+)\"" etag)]
    (some-> t parse-long)))


(defn- remove-query-param? [[k]]
  (and (str/starts-with? k "_")
       (not (#{"_id" "_list" "_profile" "_lastUpdated"} k))
       (not (str/starts-with? k "_has"))))


(defn- query-param->clauses
  "Takes a query param with possible multiple values and returns possible
  multiple clauses one for each query param."
  [[k v]]
  (map
    #(into [k] (map str/trim) (str/split % #","))
    (fhir-util/to-seq v)))


(def ^:private query-params->clauses-xf
  (comp
    (remove remove-query-param?)
    (mapcat query-param->clauses)))


(defn- sort-clauses [sort]
  (let [[param & params] (str/split sort #",")
        param (str/trim param)]
    (if params
      (ba/unsupported "More than one sort parameter is unsupported.")
      [[:sort (c-str/ltrim param "-") (if (str/starts-with? param "-") :desc :asc)]])))


(defn clauses [{:strs [_sort] :as query-params}]
  (into (if (str/blank? _sort) [] (sort-clauses _sort))
        query-params->clauses-xf query-params))


(defn search-clauses [query-params]
  (into [] query-params->clauses-xf query-params))


(defn luid [{:keys [clock rng-fn]}]
  (luid/luid clock (rng-fn)))


(defn successive-luids [{:keys [clock rng-fn]}]
  (luid/successive-luids clock (rng-fn)))


(defn t [db]
  (or (d/as-of-t db) (d/basis-t db)))


(defn- prep-if-none-match [if-none-match]
  (if (= "*" if-none-match)
    :any
    (etag->t if-none-match)))


(defn put-tx-op [resource if-match if-none-match]
  (let [if-match (some-> if-match etag->t)
        if-none-match (some-> if-none-match prep-if-none-match)]
    (cond
      if-match [:put resource [:if-match if-match]]
      if-none-match [:put resource [:if-none-match if-none-match]]
      :else [:put resource])))
