(ns blaze.terminology-service.local.code-system.filter.core
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec.type :as type]))

(defmulti filter-concepts
  {:arglists '([filter code-system])}
  (fn [{:keys [op]} _] (-> op type/value keyword)))

(defmethod filter-concepts :default
  [{:keys [op]} _]
  (ba/unsupported (format "The filter operation `%s` is not supported." op)))
