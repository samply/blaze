(ns blaze.interaction.search.nav
  (:require
    [clojure.string :as str]
    [reitit.core :as reitit]))


(defn- clauses->query-params [clauses]
  (reduce
    (fn [ret [param & values]]
      (assoc ret param (str/join "," values)))
    {}
    clauses))


(defn- query-params [{:keys [clauses page-size]}]
  (cond-> (clauses->query-params clauses)
    page-size (assoc "_count" page-size)))


(defn url
  {:arglists '([match params t offset])}
  [{{:blaze/keys [base-url]} :data :as match} params t offset]
  (let [query-params (-> (query-params params) (assoc "__t" t) (merge offset))
        path (reitit/match->path match query-params)]
    (str base-url path)))
