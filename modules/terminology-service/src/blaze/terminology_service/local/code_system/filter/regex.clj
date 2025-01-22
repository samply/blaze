(ns blaze.terminology-service.local.code-system.filter.regex
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system.filter.core :as core]
   [cognitect.anomalies :as anom]))

(defn- pred [{:keys [property value]}]
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
        #(assoc % ::anom/message (format "Invalid regex pattern `%s`." pattern)))
      (ba/incorrect "Missing filter value."))
    (ba/incorrect "Missing filter property.")))

(defmethod core/filter-concepts :regex
  [{{:keys [concepts]} :default/graph} filter]
  (when-ok [pred (pred filter)]
    (into [] (clojure.core/filter pred) (vals concepts))))

(defmethod core/find-concept :regex
  [{{:keys [concepts]} :default/graph} filter code]
  (when-ok [pred (pred filter)]
    (when-let [concept (concepts code)]
      (when (pred concept)
        concept))))
