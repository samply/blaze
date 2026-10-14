(ns blaze.db.impl.query.util
  (:require
   [blaze.db.impl.protocols :as p]))

(defn- decode-sort-clause [[search-param modifier]]
  [:sort (:code search-param) (keyword modifier)])

(defn- decode-search-clause [[search-param modifier values]]
  (into [(cond-> (:code search-param) modifier (str ":" modifier))] values))

(defn decode-clauses [{:keys [sort-clause search-clauses]}]
  (into
   (cond-> [] sort-clause (conj (decode-sort-clause sort-clause)))
   (map
    (fn [disjunction]
      (if (= 1 (count disjunction))
        (decode-search-clause (first disjunction))
        (mapv decode-search-clause disjunction))))
   search-clauses))

(defn- clause-match-extension-fn [[search-param modifier _ compiled-values]]
  (p/-match-extension-fn search-param modifier compiled-values))

(defn match-extension-fn
  "Returns a function of a resource that returns its match extensions over all
  `search-clauses` or nil if there are none. Returns nil if none of the
  `search-clauses` produces match extensions."
  [search-clauses]
  (when-let [fns (not-empty (into [] (comp cat (keep clause-match-extension-fn))
                                  search-clauses))]
    (fn [resource]
      (not-empty (into [] (keep #(% resource)) fns)))))
