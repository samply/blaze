(ns blaze.terminology-service.local.code-system.filter.exists
  "When value is `true`, includes all codes that have the specified property.
  When value is `false`, includes all codes that lack the specified property."
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system.filter.core :as core]))

(defn- xform
  "Creates either a filter of a remove xform, depending on the filter value."
  [{:keys [property value]}]
  (if-let [property (type/value property)]
    (if-some [should-exist? (parse-boolean (type/value value))]
      ((if should-exist? filter remove)
       (fn [{properties :property}]
         (some #(-> % :code type/value (= property)) properties)))
      (ba/incorrect (format "The filter value should be one of `true` or `false` but was `%s`." (type/value value))))
    (ba/incorrect "Missing filter property.")))

(defmethod core/filter-concepts :exists
  [filter {{:keys [concepts]} :default/graph}]
  (when-ok [xform (xform filter)]
    (into [] xform (vals concepts))))
