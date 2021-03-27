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


(defn- forward-include-defs->query-param-values [include-defs]
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


(defn- reverse-include-defs->query-param-values [include-defs]
  (into
    []
    (mapcat
      (fn [[target-type include-defs]]
        (mapv
          (fn [{:keys [source-type code]}]
            (cond-> (str source-type ":" code)
              (not= :any target-type)
              (str ":" target-type)))
          include-defs)))
    include-defs))


(defn- include-defs->query-params
  [{{fwd-dir :forward rev-dir :reverse} :direct
    {fwd-itr :forward rev-itr :reverse} :iterate}]
  (let [fwd-dir (forward-include-defs->query-param-values fwd-dir)
        fwd-itr (forward-include-defs->query-param-values fwd-itr)
        rev-dir (reverse-include-defs->query-param-values rev-dir)
        rev-itr (reverse-include-defs->query-param-values rev-itr)]
    (cond-> {}
      (seq fwd-dir)
      (assoc "_include" fwd-dir)
      (seq fwd-itr)
      (assoc "_include:iterate" fwd-itr)
      (seq rev-dir)
      (assoc "_revinclude" rev-dir)
      (seq rev-itr)
      (assoc "_revinclude:iterate" rev-itr))))


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
