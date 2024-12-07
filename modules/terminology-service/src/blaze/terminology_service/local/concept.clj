(ns blaze.terminology-service.local.concept)

(defn- index-by-code [concepts]
  (reduce
   (fn [ret {:keys [code] :as concept}]
     (assoc ret code concept))
   {}
   concepts))

(defn expand-code-system
  "Returns a list of concepts as expansion of `code-system` according to the
  given `value-set-concepts`."
  {:arglists '([code-system value-set-concepts])}
  [{:keys [url] concepts :concept} value-set-concepts]
  (let [code-index (index-by-code value-set-concepts)]
    (into
     []
     (keep
      (fn [{:keys [code display]}]
        (when-let [{:keys [display] :or {display display}} (get code-index code)]
          (cond-> {:system url :code code}
            display (assoc :display display)))))
     concepts)))
