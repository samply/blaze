(ns blaze.terminology-service.local.code-system.filter.regex
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system.filter.core :as core]
   [cognitect.anomalies :as anom]))

(defn- pred [{:keys [url]} {:keys [property value]}]
  (if-let [property (type/value property)]
    (if-some [pattern (type/value value)]
      (if-ok [pattern (ba/try-all ::anom/incorrect (re-pattern pattern))]
        (if (= "code" property)
          (fn [{:keys [code]}]
            (re-matches pattern (type/value code)))
          (fn [{properties :property}]
            (some
             (fn [{:keys [code value]}]
               (and (= property (type/value code))
                    (re-matches pattern (type/value value))))
             properties)))
        #(assoc % ::anom/message (format "Invalid %s regex filter value `%s` in code system `%s`. Should be a valid regex pattern." property (type/value value) (type/value url))))
      (ba/incorrect (format "Missing %s regex filter value in code system `%s`." property (type/value url))))
    (ba/incorrect (format "Missing regex filter property in code system `%s`." (type/value url)))))

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
