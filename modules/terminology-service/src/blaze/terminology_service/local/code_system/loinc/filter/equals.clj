(ns blaze.terminology-service.local.code-system.loinc.filter.equals
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system.loinc.context :as context :refer [url]]
   [blaze.terminology-service.local.code-system.loinc.filter.core :as core]
   [clojure.string :as str]))

(defn- expand-filter [{:loinc/keys [context]} index value]
  (if (nil? value)
    (ba/incorrect (format "Missing %s = filter value in code system `%s`."
                          (context/property-name-from-index index) url))
    (get-in context [index (str/upper-case value)])))

(defn- expand-filter-status [{{:keys [status-index]} :loinc/context} value]
  (if (nil? value)
    (ba/incorrect (format "Missing STATUS = filter value in code system `%s`." url))
    (let [value-upper (str/upper-case value)]
      (if (#{"ACTIVE" "TRIAL" "DISCOURAGED" "DEPRECATED"} value-upper)
        (status-index (keyword value-upper))
        (ba/incorrect (format "Invalid STATUS = filter value `%s` in code system `%s`." value url))))))

(defn- expand-filter-class-type [{{:keys [class-type-index]} :loinc/context} value]
  (if (nil? value)
    (ba/incorrect (format "Missing CLASSTYPE = filter value in code system `%s`." url))
    (if-ok [class-type (context/parse-class-type value)]
      (class-type-index class-type)
      (fn [_] (ba/incorrect (format "Invalid CLASSTYPE = filter value `%s` in code system `%s`." value url))))))

(defn- expand-filter-order-obs [{{:keys [order-obs-index]} :loinc/context} value]
  (if (nil? value)
    (ba/incorrect (format "Missing ORDER_OBS = filter value in code system `%s`." url))
    (if-ok [order-obs (context/parse-order-obs value)]
      (order-obs-index order-obs)
      (fn [_] (ba/incorrect (format "Invalid ORDER_OBS = filter value `%s` in code system `%s`." value url))))))

(defmethod core/expand-filter :=
  [code-system {:keys [property value]}]
  (condp = (type/value property)
    "COMPONENT" (expand-filter code-system :component-index (type/value value))
    "PROPERTY" (expand-filter code-system :property-index (type/value value))
    "TIME_ASPCT" (expand-filter code-system :time-index (type/value value))
    "SYSTEM" (expand-filter code-system :system-index (type/value value))
    "SCALE_TYP" (expand-filter code-system :scale-index (type/value value))
    "METHOD_TYP" (expand-filter code-system :method-index (type/value value))
    "CLASS" (expand-filter code-system :class-index (type/value value))
    "STATUS" (expand-filter-status code-system (type/value value))
    "CLASSTYPE" (expand-filter-class-type code-system (type/value value))
    "ORDER_OBS" (expand-filter-order-obs code-system (type/value value))
    nil (ba/incorrect (format "Missing = filter property in code system `%s`." url))
    (ba/unsupported (format "Unsupported = filter property `%s` in code system `%s`." (type/value property) url))))

(defn- satisfies-filter [key value {:loinc/keys [properties] :as concept}]
  (if (nil? value)
    (ba/incorrect (format "Missing %s = filter value in code system `%s`."
                          (context/property-name-from-key key) url))
    (when (some #{(str/upper-case value)} (key properties))
      concept)))

(defn- satisfies-filter-status [value {:loinc/keys [properties] :as concept}]
  (if (nil? value)
    (ba/incorrect (format "Missing STATUS = filter value in code system `%s`." url))
    (let [value-upper (str/upper-case value)]
      (if (#{"ACTIVE" "TRIAL" "DISCOURAGED" "DEPRECATED"} value-upper)
        (when (= (keyword value-upper) (:status properties))
          concept)
        (ba/incorrect (format "Invalid STATUS = filter value `%s` in code system `%s`." value url))))))

(defn- satisfies-filter-class-type [value {:loinc/keys [properties] :as concept}]
  (if (nil? value)
    (ba/incorrect (format "Missing CLASSTYPE = filter value in code system `%s`." url))
    (if-ok [class-type (context/parse-class-type value)]
      (when (= class-type (:class-type properties))
        concept)
      (fn [_] (ba/incorrect (format "Invalid CLASSTYPE = filter value `%s` in code system `%s`." value url))))))

(defn- satisfies-filter-order-obs [value {:loinc/keys [properties] :as concept}]
  (if (nil? value)
    (ba/incorrect (format "Missing ORDER_OBS = filter value in code system `%s`." url))
    (if-ok [order-obs (context/parse-order-obs value)]
      (when (= order-obs (:order-obs properties))
        concept)
      (fn [_] (ba/incorrect (format "Invalid ORDER_OBS = filter value `%s` in code system `%s`." value url))))))

(defn- satisfies-filter-list [value {:loinc/keys [properties] :as concept}]
  (if (nil? value)
    (ba/incorrect (format "Missing LIST = filter value in code system `%s`." url))
    (when (= value (:list properties))
      concept)))

(defn- satisfies-filter-answer-list [value {:loinc/keys [properties] :as concept}]
  (if (nil? value)
    (ba/incorrect (format "Missing answer-list = filter value in code system `%s`." url))
    (when (= value (:list properties))
      concept)))

(defmethod core/satisfies-filter :=
  [_ {:keys [property value]} concept]
  (condp = (type/value property)
    "COMPONENT" (satisfies-filter :component (type/value value) concept)
    "PROPERTY" (satisfies-filter :property (type/value value) concept)
    "TIME_ASPCT" (satisfies-filter :time (type/value value) concept)
    "SYSTEM" (satisfies-filter :system (type/value value) concept)
    "SCALE_TYP" (satisfies-filter :scale (type/value value) concept)
    "METHOD_TYP" (satisfies-filter :method (type/value value) concept)
    "CLASS" (satisfies-filter :class (type/value value) concept)
    "STATUS" (satisfies-filter-status (type/value value) concept)
    "CLASSTYPE" (satisfies-filter-class-type (type/value value) concept)
    "ORDER_OBS" (satisfies-filter-order-obs (type/value value) concept)
    "LIST" (satisfies-filter-list (type/value value) concept)
    "answer-list" (satisfies-filter-answer-list (type/value value) concept)
    nil (ba/incorrect (format "Missing = filter property in code system `%s`." url))
    (ba/unsupported (format "Unsupported = filter property `%s` in code system `%s`." (type/value property) url))))
