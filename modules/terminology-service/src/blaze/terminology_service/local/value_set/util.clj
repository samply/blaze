(ns blaze.terminology-service.local.value-set.util
  (:require
   [blaze.fhir.spec.type :as type]
   [clojure.string :as str]))

(defn find-version [{:keys [system-versions]} system]
  (some
   #(let [[s v] (str/split (type/value %) #"\|")]
      (when (= system s) v))
   system-versions))
