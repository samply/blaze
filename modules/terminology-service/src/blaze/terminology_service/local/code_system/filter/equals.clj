(ns blaze.terminology-service.local.code-system.filter.equals
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system.filter.core :as core]))

(defn- xform
  "Creates either a filter of a remove xform, depending on the filter value."
  [{:keys [property value]}]
  (if-let [property (type/value property)]
    (if-some [search-value (type/value value)]
      (filter
       (fn [{properties :property}]
         (some
          (fn [{:keys [code value]}]
            (and (= property (type/value code))
                 (= search-value (type/value value))))
          properties)))
      (ba/incorrect "Missing filter value."))
    (ba/incorrect "Missing filter property.")))

(defmethod core/filter-concepts :=
  [filter {{:keys [concepts]} :default/graph}]
  (when-ok [xform (xform filter)]
    (into [] xform (vals concepts))))
