(ns blaze.terminology-service.local.code-system.filter.exists
  "When value is `true`, includes all codes that have the specified property.
  When value is `false`, includes all codes that lack the specified property."
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.terminology-service.local.code-system.filter.core :as core]))

(defn- pred
  "Creates either a filter or a remove xform, depending on the filter value."
  [{{url :value} :url} {:keys [property value]}]
  (if-let [property (:value property)]
    (if-some [value (:value value)]
      (if-some [should-exist? (parse-boolean value)]
        ((if should-exist? identity complement)
         (if (= "concept" property)
           (fn [_] true)
           (fn [{properties :property}]
             (some #(-> % :code :value (= property)) properties))))
        (ba/incorrect (format "Invalid %s exists filter value `%s` in code system `%s`. Should be one of `true` or `false`." property value url)))
      (ba/incorrect (format "Missing %s exists filter value in code system `%s`." property url)))
    (ba/incorrect (format "Missing exists filter property in code system `%s`." url))))

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
