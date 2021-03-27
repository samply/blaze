(ns blaze.interaction.search.nav
  (:require
    [clojure.string :as str]
    [reitit.core :as reitit]))


(defn- clauses->query-params [clauses]
  (reduce
    (fn [ret [param & values]]
      (update ret param (fnil conj []) (str/join "," values)))
    {}
    clauses))


(defn- include-defs->query-param-values [include-defs]
  (into
    []
    (mapcat
      (fn [[source-type include-defs]]
        (mapv
          (fn [{:keys [code target-type]}]
            (cond-> (str source-type ":" code)
              target-type
              (str ":" target-type)))
          include-defs)))
    include-defs))


(defn- include-defs->query-params [{:keys [direct iterate]}]
  (let [direct (include-defs->query-param-values direct)
        iterate (include-defs->query-param-values iterate)]
    (cond-> {}
      (seq direct)
      (assoc "_include" direct)
      (seq iterate)
      (assoc "_include:iterate" iterate))))


(defn- query-params [{:keys [include-defs summary page-size]} clauses]
  (cond-> (clauses->query-params clauses)
    (seq include-defs)
    (merge (include-defs->query-params include-defs))
    summary
    (assoc "_summary" summary)
    page-size
    (assoc "_count" page-size)))


(defn url
  {:arglists '([match params clauses t offset])}
  [{{:blaze/keys [base-url]} :data :as match} params clauses t offset]
  (let [query-params (-> (query-params params clauses) (assoc "__t" t) (merge offset))
        path (reitit/match->path match query-params)]
    (str base-url path)))
