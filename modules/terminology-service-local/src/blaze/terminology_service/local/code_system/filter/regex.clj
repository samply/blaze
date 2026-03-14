(ns blaze.terminology-service.local.code-system.filter.regex
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.terminology-service.local.code-system.filter.core :as core]
   [cognitect.anomalies :as anom]))

(defn- pred [{{url :value} :url} {:keys [property value]}]
  (if-let [property (:value property)]
    (if-some [pattern (:value value)]
      (if-ok [pattern (ba/try-all ::anom/incorrect (re-pattern pattern))]
        (if (= "code" property)
          (fn [{:keys [code]}]
            (re-matches pattern (:value code)))
          (fn [{properties :property}]
            (some
             (fn [{:keys [code value]}]
               (and (= property (:value code))
                    (re-matches pattern (:value value))))
             properties)))
        #(assoc % ::anom/message (format "Invalid %s regex filter value `%s` in code system `%s`. Should be a valid regex pattern." property (:value value) url)))
      (ba/incorrect (format "Missing %s regex filter value in code system `%s`." property url)))
    (ba/incorrect (format "Missing regex filter property in code system `%s`." url))))

(defmethod core/filter-concepts :regex
  [{{:keys [concepts]} :default/graph :as code-system} filter]
  (when-ok [pred (pred code-system filter)]
    (into [] (clojure.core/filter pred) (vals concepts))))

(defmethod core/find-concept :regex
  [{{:keys [concepts]} :default/graph :as code-system} filter code]
  (when-ok [pred (pred code-system filter)]
    (when-let [concept (concepts code)]
      (when (pred concept)
        concept))))
