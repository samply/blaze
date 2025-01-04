(ns blaze.terminology-service.local.code-system.filter.core
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec.type :as type]))

(defmulti filter-concepts
  "Returns all concepts that satisfy `filter` or an anomaly in case of errors."
  {:arglists '([filter code-system])}
  (fn [{:keys [op]} _] (-> op type/value keyword)))

(defn- unsupported-filter-op-msg [{:keys [op]} {:keys [url]}]
  (format "Unsupported filter operator `%s` in code system `%s`."
          (type/value op) (type/value url)))

(defmethod filter-concepts :default
  [filter code-system]
  (ba/unsupported (unsupported-filter-op-msg filter code-system)))

(defmulti find-concept
  "Returns the concept with `code` if it satisfies `filter` or an anomaly in
  case of errors."
  {:arglists '([filter code-system code])}
  (fn [{:keys [op]} _ _] (-> op type/value keyword)))

(defmethod find-concept :default
  [filter code-system _]
  (ba/unsupported (unsupported-filter-op-msg filter code-system)))
