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

(def subsetted
  #fhir/Coding
   {:system #fhir/uri"http://terminology.hl7.org/CodeSystem/v3-ObservationValue"
    :code #fhir/code"SUBSETTED"})

(defn subsetted?
  "Checks whether `coding` is a SUBSETTED coding."
  {:arglists '([coding])}
  [{:keys [system code]}]
  (and (= #fhir/uri"http://terminology.hl7.org/CodeSystem/v3-ObservationValue" system)
       (= #fhir/code"SUBSETTED" code)))
