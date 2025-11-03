(ns blaze.db.node.validation
  (:require
   [blaze.anomaly :as ba]))

(defmulti extract-type-id first)

(defmethod extract-type-id :create
  [[_ {:fhir/keys [type] :keys [id]}]]
  [(name type) id])

(defmethod extract-type-id :put
  [[_ {:fhir/keys [type] :keys [id]}]]
  [(name type) id])

(defmethod extract-type-id :keep
  [[_ type id]]
  [type id])

(defmethod extract-type-id :delete
  [[_ type id]]
  [type id])

(defmethod extract-type-id :default [_])

(defn- duplicate-resource-anomaly [[type id]]
  (ba/incorrect
   (format "Duplicate resource `%s/%s`." type id)
   :fhir/issue "invariant"))

(defn validate-ops
  "Validates transactions operators for any duplicate resource.

  Returns an anomaly if there is any duplicate resource."
  [tx-ops]
  (transduce
   (keep extract-type-id)
   (fn
     ([res]
      (when (ba/anomaly? res) res))
     ([index type-id]
      (if (contains? index type-id)
        (reduced (duplicate-resource-anomaly type-id))
        (conj index type-id))))
   #{}
   tx-ops))
