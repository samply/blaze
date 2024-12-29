(ns blaze.terminology-service.local.code-system.util
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec.type :as type]))

(defn extract-code [request url]
  (or (:code request)
      (let [{:keys [system code]} (-> request :coding)]
        (if (= url (type/value system))
          (type/value code)
          (ba/incorrect (format "The system of the provided coding `%s` does not match the code system `%s`." (type/value system) url))))))

(defn parameter [name value]
  {:fhir/type :fhir.Parameters/parameter
   :name name
   :value value})
