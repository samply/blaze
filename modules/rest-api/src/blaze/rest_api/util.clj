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

(defn all-resource-types?
  "Returns true if `operation` supports all resource types.

  That's the case if the abstract type `Resource`, which stands for all
  resource types, is one of the resource types of `operation`."
  {:arglists '([operation])}
  [{:blaze.rest-api.operation/keys [resource-types]}]
  (boolean (some #{"Resource"} resource-types)))

(defn supports-resource-type?
  "Returns true if `operation` supports resources of `type`.

  That's the case if `operation` supports all resource types or `type` is one
  of its resource types."
  {:arglists '([operation type])}
  [{:blaze.rest-api.operation/keys [resource-types] :as operation} type]
  (or (all-resource-types? operation)
      (boolean (some #(= type %) resource-types))))
