(ns blaze.terminology-service.local.code-system.filter.exists
  "When value is `true`, includes all codes that have the specified property.
  When value is `false`, includes all codes that lack the specified property."
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system.filter.core :as core]))

(defn- pred
  "Creates either a filter or a remove xform, depending on the filter value."
  [{:keys [url]} {:keys [property value]}]
  (if-let [property (type/value property)]
    (if-some [should-exist? (parse-boolean (type/value value))]
      ((if should-exist? identity complement)
       (fn [{properties :property}]
         (some #(-> % :code type/value (= property)) properties)))
      (ba/incorrect (format "The filter value should be one of `true` or `false` but was `%s`." (type/value value))))
    (ba/incorrect (format "Missing exists filter property in code system `%s`." (type/value url)))))

(defmethod core/filter-concepts :exists
  [{{:keys [concepts]} :default/graph :as code-system} filter]
  (when-ok [pred (pred code-system filter)]
    (into [] (clojure.core/filter pred) (vals concepts))))

(defmethod core/find-concept :exists
  [{{:keys [concepts]} :default/graph :as code-system} filter code]
  (when-ok [pred (pred code-system filter)]
    (when-let [concept (concepts code)]
      (when (pred concept)
        concept))))
