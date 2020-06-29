(ns blaze.db.node.validation
  (:require
    [cognitect.anomalies :as anom]))


(defmulti extract-type-id first)


(defmethod extract-type-id :create
  [[_ {:keys [resourceType id]}]]
  [resourceType id])


(defmethod extract-type-id :put
  [[_ {:keys [resourceType id]}]]
  [resourceType id])


(defmethod extract-type-id :delete
  [[_ type id]]
  [type id])


(defn- duplicate-resource-anomaly [[type id]]
  {::anom/category ::anom/incorrect
   ::anom/message (format "Duplicate resource `%s/%s`." type id)
   :fhir/issue "invariant"})


(defn validate-ops
  "Validates transactions operators for any duplicate resource.

  Returns an anomaly if their is any duplicate resource."
  [tx-ops]
  (transduce
    (map extract-type-id)
    (fn
      ([res]
       (when (::anom/category res) res))
      ([index type-id]
       (if (contains? index type-id)
         (reduced (duplicate-resource-anomaly type-id))
         (conj index type-id))))
    #{}
    tx-ops))
