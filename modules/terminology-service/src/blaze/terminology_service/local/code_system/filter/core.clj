(ns blaze.terminology-service.local.code-system.filter.core
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec.type :as type]))

(defmulti filter-concepts
  "Returns all concepts that satisfy `filter` or an anomaly in case of errors."
  {:arglists '([code-system filter])}
  (fn [_ {:keys [op]}] (-> op type/value keyword)))

(defn unsupported-filter-op-msg [{:keys [url]} {:keys [op]}]
  (format "Unsupported filter operator `%s` in code system `%s`."
          (type/value op) (type/value url)))

(defmethod filter-concepts :default
  [code-system filter]
  (ba/unsupported (unsupported-filter-op-msg code-system filter)))

(defmulti find-concept
  "Returns the concept with `code` if it satisfies `filter` or an anomaly in
  case of errors."
  {:arglists '([code-system filter code])}
  (fn [_ {:keys [op]} _] (-> op type/value keyword)))

(defmethod find-concept :default
  [code-system filter _]
  (ba/unsupported (unsupported-filter-op-msg code-system filter)))
