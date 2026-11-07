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

(defn supports-resource-type?
  "Returns true if `operation` supports resources of `type`.

  That's the case if `type` or the abstract type `Resource`, which stands for
  all resource types, is one of the resource types of `operation`."
  [{:blaze.rest-api.operation/keys [resource-types]} type]
  (boolean (some #{type "Resource"} resource-types)))
