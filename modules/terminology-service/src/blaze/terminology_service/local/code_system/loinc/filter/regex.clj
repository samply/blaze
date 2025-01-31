(ns blaze.terminology-service.local.code-system.loinc.filter.regex
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system.loinc.context :as context :refer [url]]
   [blaze.terminology-service.local.code-system.loinc.filter.core :as core]))

(defn- case-insensitive-pattern [value]
  (re-pattern (str "(?i)" value)))

(defn- matches
  "should be a full match
  see: https://chat.fhir.org/#narrow/channel/179202-terminology/topic/ValueSet.20.60regex.60.20op"
  [f value]
  (filter #(re-matches (case-insensitive-pattern value) (f %))))

(defn- expand-filter-regex [{:loinc/keys [context]} index value]
  (if (nil? value)
    (ba/incorrect (format "Missing %s regex filter value in code system `%s`."
                          (context/property-name-from-index index) url))
    (let [index (index context)]
      (into [] (comp (matches identity value) (mapcat index)) (keys index)))))

(defn- expand-filter-regex-keyword [{:loinc/keys [context]} index value]
  (if (nil? value)
    (ba/incorrect (format "Missing %s regex filter value in code system `%s`."
                          (context/property-name-from-index index) url))
    (let [index (index context)]
      (into [] (comp (matches name value) (mapcat index)) (keys index)))))

(defmethod core/expand-filter :regex
  [code-system {:keys [property value]}]
  (condp = (type/value property)
    "COMPONENT" (expand-filter-regex code-system :component-index (type/value value))
    "PROPERTY" (expand-filter-regex code-system :property-index (type/value value))
    "TIME_ASPCT" (expand-filter-regex code-system :time-index (type/value value))
    "SYSTEM" (expand-filter-regex code-system :system-index (type/value value))
    "SCALE_TYP" (expand-filter-regex code-system :scale-index (type/value value))
    "METHOD_TYP" (expand-filter-regex code-system :method-index (type/value value))
    "CLASS" (expand-filter-regex code-system :class-index (type/value value))
    "STATUS" (expand-filter-regex-keyword code-system :status-index (type/value value))
    "ORDER_OBS" (expand-filter-regex-keyword code-system :order-obs-index (type/value value))
    nil (ba/incorrect (format "Missing regex filter property in code system `%s`." url))
    (ba/unsupported (format "Unsupported regex filter property `%s` in code system `%s`." (type/value property) url))))

(defn- satisfies-filter-regex [key value {:loinc/keys [properties] :as concept}]
  (if (nil? value)
    (ba/incorrect (format "Missing %s regex filter value in code system `%s`."
                          (context/property-name-from-key key) url))
    (when (some (partial re-matches (case-insensitive-pattern value)) (key properties))
      concept)))

(defmethod core/satisfies-filter :regex
  [_ {:keys [property value]} concept]
  (condp = (type/value property)
    "COMPONENT" (satisfies-filter-regex :component (type/value value) concept)
    "PROPERTY" (satisfies-filter-regex :property (type/value value) concept)
    "TIME_ASPCT" (satisfies-filter-regex :time (type/value value) concept)
    "SCALE_TYP" (satisfies-filter-regex :scale (type/value value) concept)
    "METHOD_TYP" (satisfies-filter-regex :method (type/value value) concept)
    "SYSTEM" (satisfies-filter-regex :system (type/value value) concept)
    "CLASS" (satisfies-filter-regex :class (type/value value) concept)
    nil (ba/incorrect (format "Missing regex filter property in code system `%s`." url))
    (ba/unsupported (format "Unsupported regex filter property `%s` in code system `%s`." (type/value property) url))))
