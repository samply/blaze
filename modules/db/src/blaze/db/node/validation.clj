(ns blaze.db.node.validation
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec :as fhir-spec]))

(defmulti extract-type-id first)

(defmethod extract-type-id :create
  [[_ {:keys [id] :as resource}]]
  [(name (fhir-spec/fhir-type resource)) id])

(defmethod extract-type-id :put
  [[_ {:keys [id] :as resource}]]
  [(name (fhir-spec/fhir-type resource)) id])

(defmethod extract-type-id :keep
  [[_ type id]]
  [type id])

(defmethod extract-type-id :delete
  [[_ type id]]
  [type id])

(defmethod extract-type-id :conditional-delete [_])

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
