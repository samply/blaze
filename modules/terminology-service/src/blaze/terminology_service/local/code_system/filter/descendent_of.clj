(ns blaze.terminology-service.local.code-system.filter.descendent-of
  "Includes all concepts that have a transitive is-a relationship with the
  concept provided as the value (include descendant codes)."
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system.filter.core :as core]
   [blaze.terminology-service.local.graph :as graph]))

(defn- expand-filter
  [{:keys [url] :default/keys [graph]} value]
  (if (nil? value)
    (ba/incorrect (format "Missing concept descendent-of filter value in code system `%s`." (type/value url)))
    (graph/descendent-of graph (type/value value))))

(defmethod core/filter-concepts :descendent-of
  [{:keys [url] :as code-system} {:keys [property value]}]
  (condp = (type/value property)
    "concept" (expand-filter code-system (type/value value))
    nil (ba/incorrect (format "Missing descendent-of filter property in code system `%s`." (type/value url)))
    (ba/unsupported (format "Unsupported descendent-of filter property `%s` in code system `%s`." (type/value property) (type/value url)))))

(defn- find-filter
  [{:keys [url] :default/keys [graph]} value code]
  (if (nil? value)
    (ba/incorrect (format "Missing concept descendent-of filter value in code system `%s`." (type/value url)))
    (graph/find-descendent-of graph value code)))

(defmethod core/find-concept :descendent-of
  [{:keys [url] :as code-system} {:keys [property value]} code]
  (condp = (type/value property)
    "concept" (find-filter code-system (type/value value) code)
    nil (ba/incorrect (format "Missing descendent-of filter property in code system `%s`." (type/value url)))
    (ba/unsupported (format "Unsupported descendent-of filter property `%s` in code system `%s`." (type/value property) (type/value url)))))
