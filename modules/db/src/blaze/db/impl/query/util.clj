(ns blaze.db.impl.query.util)

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
