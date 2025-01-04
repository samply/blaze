(ns blaze.fhir.util
  (:require
   [blaze.fhir.spec.type :as type]))

(defn parameters [& nvs]
  {:fhir/type :fhir/Parameters
   :parameter
   (into
    []
    (keep
     (fn [[name value]]
       (when (some? value)
         {:fhir/type :fhir.Parameters/parameter
          :name (type/string name)
          (if (:fhir/type value) :resource :value) value})))
    (partition 2 nvs))})
