(ns blaze.rest-api.util)


(defn resolve-pattern
  "Tries to find a resource pattern in `resource-patterns` according to the
  name of the `structure-definition`.

  Falls back to the :default resource pattern if there is any."
  {:arglists '([resource-patterns structure-definition])}
  [resource-patterns {:keys [name]}]
  (or
    (some
      #(when (= name (:blaze.rest-api.resource-pattern/type %)) %)
      resource-patterns)
    (some
      #(when (= :default (:blaze.rest-api.resource-pattern/type %)) %)
      resource-patterns)))
