(ns blaze.terminology-service.local.code-system.sct.filter.equals
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system.sct.context :as context :refer [url]]
   [blaze.terminology-service.local.code-system.sct.filter.core :as core]
   [blaze.terminology-service.local.code-system.sct.type :refer [parse-sctid]]))

(defn- expand-filter-parent
  [{{:keys [module-dependency-index child-index]} :sct/context :sct/keys [module-id version]} value]
  (if (nil? value)
    (ba/incorrect (format "Missing parent = filter value in code system `%s`." url))
    (if-let [code (parse-sctid value)]
      (context/neighbors module-dependency-index child-index module-id version code)
      (ba/incorrect (format "Invalid parent = filter value `%s` in code system `%s`." value url)))))

(defn- expand-filter-child
  [{{:keys [module-dependency-index parent-index]} :sct/context :sct/keys [module-id version]} value]
  (if (nil? value)
    (ba/incorrect (format "Missing child = filter value in code system `%s`." url))
    (if-let [code (parse-sctid value)]
      (context/neighbors module-dependency-index parent-index module-id version code)
      (ba/incorrect (format "Invalid child = filter value `%s` in code system `%s`." value url)))))

(defmethod core/expand-filter :=
  [code-system {:keys [property value]}]
  (condp = (type/value property)
    "parent" (expand-filter-parent code-system (type/value value))
    "child" (expand-filter-child code-system (type/value value))
    nil (ba/incorrect (format "Missing = filter property in code system `%s`." url))
    (ba/unsupported (format "Unsupported = filter property `%s` in code system `%s`." (type/value property) url))))

(defn- satisfies-filter-parent
  [{{:keys [module-dependency-index child-index]} :sct/context :sct/keys [module-id version]} value code]
  (if (nil? value)
    (ba/incorrect (format "Missing parent = filter value in code system `%s`." url))
    (if-let [start-code (parse-sctid value)]
      (contains? (context/neighbors module-dependency-index child-index module-id version start-code) code)
      (ba/incorrect (format "Invalid parent = filter value `%s` in code system `%s`." value url)))))

(defn- satisfies-filter-child
  [{{:keys [module-dependency-index parent-index]} :sct/context :sct/keys [module-id version]} value code]
  (if (nil? value)
    (ba/incorrect (format "Missing child = filter value in code system `%s`." url))
    (if-let [start-code (parse-sctid value)]
      (contains? (context/neighbors module-dependency-index parent-index module-id version start-code) code)
      (ba/incorrect (format "Invalid child = filter value `%s` in code system `%s`." value url)))))

(defmethod core/satisfies-filter :=
  [code-system {:keys [property value]} code]
  (condp = (type/value property)
    "parent" (satisfies-filter-parent code-system (type/value value) code)
    "child" (satisfies-filter-child code-system (type/value value) code)
    nil (ba/incorrect (format "Missing = filter property in code system `%s`." url))
    (ba/unsupported (format "Unsupported = filter property `%s` in code system `%s`." (type/value property) url))))
