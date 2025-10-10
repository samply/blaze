(ns blaze.terminology-service.local.code-system.sct.filter.is-a
  "Includes all concepts that have a transitive is-a relationship with the
  concept provided as the value, including the provided concept itself
  (include descendant codes and self)."
  (:require
   [blaze.anomaly :as ba]
   [blaze.terminology-service.local.code-system.sct.context :as context :refer [url]]
   [blaze.terminology-service.local.code-system.sct.filter.core :as core]
   [blaze.terminology-service.local.code-system.sct.type :refer [parse-sctid]]))

(def ^:private missing-concept-filter-value-msg
  (format "Missing concept is-a filter value in code system `%s`." url))

(defn- invalid-value-msg [value]
  (format "Invalid concept is-a filter value `%s` in code system `%s`."
          value url))

(defn- expand-filter
  [{{:keys [module-dependency-index child-index]} :sct/context
    :sct/keys [module-id version]} value]
  (if (nil? value)
    (ba/incorrect missing-concept-filter-value-msg)
    (if-let [code (parse-sctid value)]
      (context/transitive-neighbors-including module-dependency-index child-index
                                              module-id version code)
      (ba/incorrect (invalid-value-msg value)))))

(def ^:private missing-property-msg
  (format "Missing is-a filter property in code system `%s`." url))

(defn- unsupported-property-msg [property]
  (format "Unsupported is-a filter property `%s` in code system `%s`."
          property url))

(defmethod core/expand-filter :is-a
  [code-system {{property :value} :property {:keys [value]} :value}]
  (condp = property
    "concept" (expand-filter code-system value)
    nil (ba/incorrect missing-property-msg)
    (ba/unsupported (unsupported-property-msg property))))

(defn- satisfies-filter
  [{{:keys [module-dependency-index parent-index]} :sct/context
    :sct/keys [module-id version]} value code]
  (if (nil? value)
    (ba/incorrect missing-concept-filter-value-msg)
    (if-let [start-code (parse-sctid value)]
      (or (= code start-code)
          (context/find-transitive-neighbor module-dependency-index parent-index
                                            module-id version code start-code))
      (ba/incorrect (invalid-value-msg value)))))

(defmethod core/satisfies-filter :is-a
  [code-system {{property :value} :property {:keys [value]} :value} code]
  (condp = property
    "concept" (satisfies-filter code-system value code)
    nil (ba/incorrect missing-property-msg)
    (ba/unsupported (unsupported-property-msg property))))
