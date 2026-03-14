(ns blaze.terminology-service.local.code-system.filter.is-a
  "Includes all concepts that have a transitive is-a relationship with the
  concept provided as the value, including the provided concept itself
  (include descendant codes and self)."
  (:require
   [blaze.anomaly :as ba]
   [blaze.terminology-service.local.code-system.filter.core :as core]
   [blaze.terminology-service.local.graph :as graph]))

(defn- expand-filter
  [{{url :value} :url :default/keys [graph]} value]
  (if (nil? value)
    (ba/incorrect (format "Missing concept is-a filter value in code system `%s`." url))
    (graph/is-a graph value)))

(defmethod core/filter-concepts :is-a
  [{{url :value} :url :as code-system} {{property :value} :property {value :value} :value}]
  (condp = property
    "concept" (expand-filter code-system value)
    nil (ba/incorrect (format "Missing is-a filter property in code system `%s`." url))
    (ba/unsupported (format "Unsupported is-a filter property `%s` in code system `%s`." property url))))

(defn- find-filter
  [{{url :value} :url :default/keys [graph]} value code]
  (if (nil? value)
    (ba/incorrect (format "Missing concept is-a filter value in code system `%s`." url))
    (graph/find-is-a graph value code)))

(defmethod core/find-concept :is-a
  [{{url :value} :url :as code-system}
   {{property :value} :property {value :value} :value} code]
  (condp = property
    "concept" (find-filter code-system value code)
    nil (ba/incorrect (format "Missing is-a filter property in code system `%s`." url))
    (ba/unsupported (format "Unsupported is-a filter property `%s` in code system `%s`." property url))))
