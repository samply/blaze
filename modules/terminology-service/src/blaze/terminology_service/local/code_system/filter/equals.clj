(ns blaze.terminology-service.local.code-system.filter.equals
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system.filter.core :as core]))

(defn- pred [{:keys [url]} {:keys [property value]}]
  (if-let [property (type/value property)]
    (if-some [search-value (type/value value)]
      (fn [{properties :property}]
        (some
         (fn [{:keys [code value]}]
           (and (= property (type/value code))
                (= search-value (type/value value))))
         properties))
      (ba/incorrect (format "Missing %s = filter value in code system `%s`." property (type/value url))))
    (ba/incorrect (format "Missing = filter property in code system `%s`." (type/value url)))))

(defmethod core/filter-concepts :=
  [{{:keys [concepts]} :default/graph :as code-system} filter]
  (when-ok [pred (pred code-system filter)]
    (into [] (clojure.core/filter pred) (vals concepts))))

(defmethod core/find-concept :=
  [{{:keys [concepts]} :default/graph :as code-system} filter code]
  (when-ok [pred (pred code-system filter)]
    (when-let [concept (concepts code)]
      (when (pred concept)
        concept))))
