(ns blaze.terminology-service.local.code-system.sct.filter.descendent-of
  "Includes all concepts that have a transitive descendent-of relationship with the
  concept provided as the value (include descendant codes)."
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system.sct.context :as context :refer [url]]
   [blaze.terminology-service.local.code-system.sct.filter.core :as core]
   [blaze.terminology-service.local.code-system.sct.type :refer [parse-sctid]]))

(defn- expand-filter
  [{{:keys [child-index]} :sct/context :sct/keys [module-id version]} value]
  (if (nil? value)
    (ba/incorrect (format "Missing concept descendent-of filter value in code system `%s`." url))
    (if-let [code (parse-sctid value)]
      (context/transitive-neighbors child-index module-id version code)
      (ba/incorrect (format "Invalid concept descendent-of filter value `%s` in code system `%s`." value url)))))

(defmethod core/expand-filter :descendent-of
  [code-system {:keys [property value]}]
  (condp = (type/value property)
    "concept" (expand-filter code-system (type/value value))
    nil (ba/incorrect (format "Missing descendent-of filter property in code system `%s`." url))
    (ba/unsupported (format "Unsupported descendent-of filter property `%s` in code system `%s`." (type/value property) url))))

(defn- satisfies-filter
  [{{:keys [child-index]} :sct/context :sct/keys [module-id version]} value code]
  (if (nil? value)
    (ba/incorrect (format "Missing concept descendent-of filter value in code system `%s`." url))
    (if-let [start-code (parse-sctid value)]
      (context/find-transitive-neighbor child-index module-id version
                                        start-code code)
      (ba/incorrect (format "Invalid concept descendent-of filter value `%s` in code system `%s`." value url)))))

(defmethod core/satisfies-filter :descendent-of
  [code-system {:keys [property value]} code]
  (condp = (type/value property)
    "concept" (satisfies-filter code-system (type/value value) code)
    nil (ba/incorrect (format "Missing descendent-of filter property in code system `%s`." url))
    (ba/unsupported (format "Unsupported descendent-of filter property `%s` in code system `%s`." (type/value property) url))))
