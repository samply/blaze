(ns blaze.terminology-service.local.code-system.filter.equals
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system.filter.core :as core]))

(defn- pred [{:keys [property value]}]
  (if-let [property (type/value property)]
    (if-some [search-value (type/value value)]
      (fn [{properties :property}]
        (some
         (fn [{:keys [code value]}]
           (and (= property (type/value code))
                (= search-value (type/value value))))
         properties))
      (ba/incorrect "Missing filter value."))
    (ba/incorrect "Missing filter property.")))

(defmethod core/filter-concepts :=
  [{{:keys [concepts]} :default/graph} filter]
  (when-ok [pred (pred filter)]
    (into [] (clojure.core/filter pred) (vals concepts))))

(defmethod core/satisfies-filter? :=
  [_ filter concept]
  (when-ok [pred (pred filter)]
    (pred concept)))
