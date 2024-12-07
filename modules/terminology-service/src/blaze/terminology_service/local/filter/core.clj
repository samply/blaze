(ns blaze.terminology-service.local.filter.core
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec.type :as type]))

(defmulti filter-concepts
  {:arglists '([filter concepts])}
  (fn [{:keys [op]} _concepts] (-> op type/value keyword)))

(defmethod filter-concepts :default
  [{:keys [op]} _concepts]
  (ba/unsupported (format "The filter operation `%s` is not supported." op)))
