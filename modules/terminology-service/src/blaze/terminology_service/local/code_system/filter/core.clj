(ns blaze.terminology-service.local.code-system.filter.core
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec.type :as type]))

(defmulti filter-concepts
  {:arglists '([filter code-system])}
  (fn [{:keys [op]} _] (-> op type/value keyword)))

(defn- unsupported-filter-op-msg [{:keys [op]} {:keys [url]}]
  (format "Unsupported filter operator `%s` in code system `%s`."
          (type/value op) (type/value url)))

(defmethod filter-concepts :default
  [filter code-system]
  (ba/unsupported (unsupported-filter-op-msg filter code-system)))
