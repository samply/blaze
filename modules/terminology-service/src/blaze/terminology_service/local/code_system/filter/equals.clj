(ns blaze.terminology-service.local.code-system.filter.equals
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.terminology-service.local.code-system.filter.core :as core]))

(defn- pred [{:keys [url]} {:keys [property value]}]
  (if-let [property (:value property)]
    (if-some [search-value (:value value)]
      (fn [{properties :property}]
        (some
         (fn [{:keys [code value]}]
           (and (= property (:value code))
                (= search-value (:value value))))
         properties))
      (ba/incorrect (format "Missing %s = filter value in code system `%s`." property (:value url))))
    (ba/incorrect (format "Missing = filter property in code system `%s`." (:value url)))))

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
