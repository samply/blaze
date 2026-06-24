(ns blaze.job.test-util
  (:require
   [blaze.fhir.spec.type :as type]))

(defn concept [system code]
  (type/codeable-concept
   {:coding [(type/coding {:system (type/uri system) :code (type/code code)})]}))

(defn job
  "A minimal re-index job on `base` with optional `profile`/`job-type`/`param`
  overrides (to simulate elements added after version 0.1.0)."
  [base & {:keys [profile job-type param]
           :or {profile "ReIndexJob" job-type "re-index" param "search-param-url"}}]
  {:fhir/type :fhir/Task
   :meta (type/meta {:profile [(type/canonical (str base "/StructureDefinition/" profile))]})
   :status #fhir/code "ready"
   :intent #fhir/code "order"
   :code (concept (str base "/CodeSystem/JobType") job-type)
   :input
   [{:fhir/type :fhir.Task/input
     :type (concept (str base "/CodeSystem/ReIndexJobParameter") param)}]})
