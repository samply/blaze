(ns blaze.handler.fhir.capabilities
  "FHIR capabilities endpoint.

  https://www.hl7.org/fhir/http.html#capabilities"
  (:require
    [blaze.middleware.exception :refer [wrap-exception]]
    [blaze.middleware.json :refer [wrap-json]]
    [clojure.spec.alpha :as s]
    [ring.util.response :as ring]))


(defn- resource [id]
  {:type id
   :interaction
   [{:code "read"}
    {:code "update"}]})


(defn handler-intern [version structure-definitions]
  (fn [_]
    (ring/response
      {:resourceType "CapabilityStatement"
       :status "active"
       :date "2019-05-15T00:00:00Z"
       :software
       {:name "Blaze"
        :version version}
       :fhirVersion "4.0.0"
       :format ["application/fhir+json"]
       :rest
       [{:mode "server"
         :resource (into [] (map resource) (keys structure-definitions))
         :interaction
         [{:code "transaction"}]}]})))


(s/def :handler.fhir/capabilities fn?)


(s/fdef handler
  :args (s/cat :version string? :structure-definitions map?)
  :ret :handler.fhir/capabilities)

(defn handler
  ""
  [version structure-definitions]
  (-> (handler-intern version structure-definitions)
      (wrap-json)
      (wrap-exception)))
