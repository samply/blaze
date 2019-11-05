(ns blaze.interaction.capabilities
  "FHIR capabilities interaction.

  https://www.hl7.org/fhir/http.html#capabilities

  We use `array-map` here to keep the order of keys in maps to gain human
  readability."
  (:require
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [blaze.spec]
    [blaze.structure-definition]
    [clojure.spec.alpha :as s]
    [ring.util.response :as ring]))


(defn- resource [{:keys [id]}]
  (cond->
    {:type id
     :interaction
     [{:code "read"}
      {:code "vread"}
      {:code "update"}
      {:code "delete"}
      {:code "history-instance"}
      {:code "history-type"}
      {:code "create"}
      {:code "search-type"}]
     :versioning "versioned"
     :readHistory true
     :updateCreate true
     :conditionalCreate false
     :conditionalRead "not-supported"
     :conditionalUpdate false
     :conditionalDelete "not-supported"
     :referencePolicy
     ["literal"
      "enforced"
      "local"]
     :searchParam
     [{:name "identifier"
       :definition (str "http://hl7.org/fhir/SearchParameter/" id "-identifier")
       :type "token"}]}

    (= "Measure" id)
    (assoc
      :operation
      [{:name "evaluate-measure"
        :definition "http://hl7.org/fhir/OperationDefinition/Measure-evaluate-measure"}])))


(s/def :handler.fhir/capabilities fn?)


(s/fdef handler
  :args
  (s/cat
    :base-url :blaze/base-url
    :version string?
    :structure-definitions (s/coll-of :fhir.un/StructureDefinition))
  :ret :handler.fhir/capabilities)

(defn handler
  ""
  [base-url version structure-definitions]
  (-> (fn [_]
        (ring/response
          (array-map
            :resourceType "CapabilityStatement"
            :status "active"
            :kind "instance"
            :date "2019-08-28T00:00:00Z"
            :software
            {:name "Blaze"
             :version version}
            :implementation
            {:description (str "Blaze running at " base-url "/fhir")
             :url (str base-url "/fhir")}
            :fhirVersion "4.0.0"
            :format ["application/fhir+json"]
            :rest
            [{:mode "server"
              :resource
              (into
                []
                (comp
                  (filter #(= "resource" (:kind %)))
                  (remove :experimental)
                  (remove :abstract)
                  (map resource))
                structure-definitions)
              :interaction
              [{:code "transaction"}
               {:code "batch"}
               {:code "history-system"}]}])))
      (wrap-observe-request-duration "capabilities")))
