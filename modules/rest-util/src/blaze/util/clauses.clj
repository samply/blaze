(ns blaze.util.clauses
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.util :as u]
   [clojure.string :as str]))

(defn- remove-query-param? [[param-key]]
  (let [[code] (str/split param-key #":" 2)]
    (and (str/starts-with? code "_")
         (not (#{"_id" "_list" "_profile" "_tag" "_lastUpdated" "_has"} code)))))

(defn- query-param->clauses
  "Takes a query param with possible multiple values and returns possible
  multiple clauses one for each query param."
  [[param-key param-value]]
  (map
   #(into [param-key] (map str/trim) (str/split % #","))
   (u/to-seq param-value)))

(def ^:private query-params->clauses-xf
  (comp
   (remove remove-query-param?)
   (mapcat query-param->clauses)))

(defn- sort-clauses [sort]
  (let [[param & params] (str/split sort #",")
        param (str/trim param)]
    (if params
      (ba/unsupported "More than one sort parameter is unsupported.")
      [(if (str/starts-with? param "-")
         [:sort (subs param 1) :desc]
         [:sort param :asc])])))

(defn clauses
  "Extracts search clauses from `query-params`.

  Removes some redundant or not supported special query params."
  {:arglists '([query-params])}
  [{sort "_sort" :as query-params}]
  (let [clauses (into [] query-params->clauses-xf query-params)]
    (if (or (str/blank? sort) (some (fn [[code]] (= "_id" code)) clauses))
      clauses
      (when-ok [sort-clauses (sort-clauses sort)]
        (into sort-clauses clauses)))))

(defn search-clauses [query-params]
  (into [] query-params->clauses-xf query-params))
