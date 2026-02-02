(ns blaze.db.impl.index.plan
  "Query plan helper functions."
  (:require
   [blaze.anomaly :as ba]
   [blaze.db.impl.search-param :as search-param]))

(defn- estimated-scan-size
  "Returns a estimated scan size of `clause` under `tid`.

  Returns an anomaly on errors."
  {:arglists '([batch-db tid clause])}
  [batch-db tid [search-param modifier _ compiled-values]]
  (search-param/estimated-scan-size search-param batch-db tid modifier compiled-values))

(defn- total-estimated-scan-size
  "Returns the sum of estimated scan sizes of all `clauses`.

  Returns an anomaly on errors."
  [batch-db tid clauses]
  (transduce
   (comp (map (partial estimated-scan-size batch-db tid))
         (halt-when ba/anomaly?))
   + clauses))

(defn- attach-total-estimated-scan-size [batch-db tid disjunction]
  (let [estimated-scan-size (total-estimated-scan-size batch-db tid disjunction)]
    (cond-> disjunction
      (not (ba/anomaly? estimated-scan-size))
      (with-meta {:estimated-scan-size estimated-scan-size}))))

(def ^:private ^:const ^long scan-factor
  "The factor to calculate the maximum difference between the search-param/values
  combination with the smallest scan size and the largest scan size to allow.

  Clauses with scan sizes larger than the calculated threshold will be excluded
  from scanning."
  10)

(defn group-by-estimated-scan-size
  "Returns two groups, :small and :large."
  [batch-db tid search-clauses]
  (let [sized-clauses (mapv (partial attach-total-estimated-scan-size batch-db tid)
                            search-clauses)
        estimated-sizes (->> (keep (comp :estimated-scan-size meta) sized-clauses)
                             (remove zero?)
                             (sort))]
    (if (seq estimated-sizes)
      (let [threshold (* scan-factor (first estimated-sizes))]
        (group-by
         (fn [clause]
           (let [{:keys [estimated-scan-size]} (meta clause)]
             (if (and estimated-scan-size (< estimated-scan-size threshold))
               :small
               :large)))
         sized-clauses))
      {:small sized-clauses})))
