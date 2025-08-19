(ns blaze.interaction.search.query-plan
  (:require
   [clojure.string :as str]))

(defn- render-search-param-code [{:keys [code modifier]}]
  (cond-> code modifier (str ":" modifier)))

(defn render
  "Renders `query-plan` into a human readable string."
  {:arglists '([query-plan])}
  [{:keys [query-type scan-type scan-clauses seek-clauses]}]
  (format
   (cond->> "SCANS%s: %s; SEEKS: %s"
     (= :compartment query-type)
     (str "TYPE: compartment; "))
   (if scan-type (format "(%s)" (name scan-type)) "")
   (if (seq scan-clauses)
     (str/join ", " (map render-search-param-code scan-clauses))
     "NONE")
   (if (seq seek-clauses)
     (str/join ", " (map render-search-param-code seek-clauses))
     "NONE")))
