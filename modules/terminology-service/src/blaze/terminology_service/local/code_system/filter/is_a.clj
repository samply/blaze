(ns blaze.terminology-service.local.code-system.filter.is-a
  "Includes all concepts that have a transitive is-a relationship with the
  concept provided as the value, including the provided concept itself
  (include descendant codes and self)."
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system.filter.core :as core]
   [blaze.terminology-service.local.graph :as graph]))

(defn- expand-filter
  [{:keys [url] :default/keys [graph]} value]
  (if (nil? value)
    (ba/incorrect (format "Missing concept is-a filter value in code system `%s`." (type/value url)))
    (graph/is-a graph value)))

(defmethod core/filter-concepts :is-a
  [{:keys [url] :as code-system} {:keys [property value]}]
  (condp = (type/value property)
    "concept" (expand-filter code-system (type/value value))
    nil (ba/incorrect (format "Missing is-a filter property in code system `%s`." (type/value url)))
    (ba/unsupported (format "Unsupported is-a filter property `%s` in code system `%s`." (type/value property) (type/value url)))))

(defn- find-filter
  [{:keys [url] :default/keys [graph]} value code]
  (if (nil? value)
    (ba/incorrect (format "Missing concept is-a filter value in code system `%s`." (type/value url)))
    (graph/find-is-a graph value code)))

(defmethod core/find-concept :is-a
  [{:keys [url] :as code-system} {:keys [property value]} code]
  (condp = (type/value property)
    "concept" (find-filter code-system (type/value value) code)
    nil (ba/incorrect (format "Missing is-a filter property in code system `%s`." (type/value url)))
    (ba/unsupported (format "Unsupported is-a filter property `%s` in code system `%s`." (type/value property) (type/value url)))))
