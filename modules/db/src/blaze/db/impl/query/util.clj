(ns blaze.db.impl.query.util
  (:refer-clojure :exclude [str])
  (:require
   [blaze.util :refer [str]]))

(defn decode-clauses [clauses]
  (into
   []
   (keep
    (fn [[search-param modifier values]]
      (if (#{"asc" "desc"} modifier)
        [:sort (:code search-param) (keyword modifier)]
        (into [(cond-> (:code search-param) modifier (str ":" modifier))] values))))
   clauses))
