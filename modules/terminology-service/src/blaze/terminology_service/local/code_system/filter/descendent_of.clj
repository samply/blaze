(ns blaze.terminology-service.local.code-system.filter.descendent-of
  "Includes all concepts that have a transitive is-a relationship with the
  concept provided as the value (include descendant codes)."
  (:require
   [blaze.anomaly :as ba]
   [blaze.terminology-service.local.code-system.filter.core :as core]
   [blaze.terminology-service.local.graph :as graph]))

(defn- expand-filter
  [{{url :value} :url :default/keys [graph]} value]
  (if (nil? value)
    (ba/incorrect (format "Missing concept descendent-of filter value in code system `%s`." url))
    (graph/descendent-of graph value)))

(defmethod core/filter-concepts :descendent-of
  [{{url :value} :url :as code-system} {{property :value} :property {value :value} :value}]
  (condp = property
    "concept" (expand-filter code-system value)
    nil (ba/incorrect (format "Missing descendent-of filter property in code system `%s`." url))
    (ba/unsupported (format "Unsupported descendent-of filter property `%s` in code system `%s`." property url))))

(defn- find-filter
  [{{url :value} :url :default/keys [graph]} value code]
  (if (nil? value)
    (ba/incorrect (format "Missing concept descendent-of filter value in code system `%s`." url))
    (graph/find-descendent-of graph value code)))

(defmethod core/find-concept :descendent-of
  [{{url :value} :url :as code-system} {{property :value} :property {value :value} :value} code]
  (condp = property
    "concept" (find-filter code-system value code)
    nil (ba/incorrect (format "Missing descendent-of filter property in code system `%s`." url))
    (ba/unsupported (format "Unsupported descendent-of filter property `%s` in code system `%s`." property url))))
