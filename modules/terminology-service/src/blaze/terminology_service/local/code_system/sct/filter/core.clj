(ns blaze.terminology-service.local.code-system.sct.filter.core
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system.filter.core :refer [unsupported-filter-op-msg]]))

(defmulti expand-filter
  "Returns all codes that satisfy `filter` or an anomaly in case of errors."
  {:arglists '([code-system filter])}
  (fn [_ {:keys [op]}] (-> op type/value keyword)))

(defmethod expand-filter :default
  [code-system filter]
  (ba/unsupported (unsupported-filter-op-msg code-system filter)))

(defmulti satisfies-filter
  "Returns true if the concept with `code` satisfies `filter` or an anomaly in
  case of errors."
  {:arglists '([code-system filter code])}
  (fn [_ {:keys [op]} _] (-> op type/value keyword)))

(defmethod satisfies-filter :default
  [code-system filter _]
  (ba/unsupported (unsupported-filter-op-msg code-system filter)))
