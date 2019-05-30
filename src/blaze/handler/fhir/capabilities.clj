(ns blaze.handler.fhir.capabilities
  "FHIR capabilities interaction.

  https://www.hl7.org/fhir/http.html#capabilities

  We use `array-map` here to keep the order of keys in maps to gain human
  readability."
  (:require
    [blaze.middleware.exception :refer [wrap-exception]]
    [blaze.middleware.json :refer [wrap-json]]
    [clojure.spec.alpha :as s]
    [ring.util.response :as ring]))


(defn- resource [{:keys [id]}]
  (array-map
    :type id
    :interaction
    [{:code "read"}
     {:code "vread"}
     {:code "update"}
     {:code "delete"}
     {:code "history-instance"}
     {:code "create"}
     {:code "search-type"}]
    :versioning "versioned"
    :readHistory true
    :updateCreate true
    :conditionalCreate false
    :conditionalRead false
    :conditionalUpdate false
    :conditionalDelete "not-supported"
    :referencePolicy
    ["literal"
     "enforced"
     "local"]
    :searchParam
    [{:name "identifier"
      :definition (str "http://hl7.org/fhir/SearchParameter/" id "-identifier")
      :type "token"}]))


(defn handler-intern [base-uri version structure-definitions]
  (fn [_]
    (ring/response
      (array-map
        :resourceType "CapabilityStatement"
        :status "active"
        :kind "instance"
        :date "2019-05-22T00:00:00Z"
        :software
        {:name "Blaze"
         :version version}
        :implementation
        {:description (str "Blaze running at " base-uri "/fhir")
         :url (str base-uri "/fhir")}
        :fhirVersion "4.0.0"
        :format ["application/fhir+json"]
        :rest
        [{:mode "server"
          :resource
          (into
            []
            (comp
              (filter #(= "resource" (:kind %)))
              (remove #(= "Resource" (:id %)))
              (map resource))
            structure-definitions)
          :interaction
          [{:code "transaction"}
           {:code "batch"}]}]))))


(s/def :handler.fhir/capabilities fn?)


(s/fdef handler
  :args (s/cat :base-uri string? :version string?
               :structure-definitions (s/coll-of :fhir.un/StructureDefinition))
  :ret :handler.fhir/capabilities)

(defn handler
  ""
  [base-uri version structure-definitions]
  (-> (handler-intern base-uri version structure-definitions)
      (wrap-json)
      (wrap-exception)))
