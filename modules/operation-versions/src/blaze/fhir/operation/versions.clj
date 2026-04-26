(ns blaze.fhir.operation.versions
  "Main entry point into the $versions operation."
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir.spec.type]
   [integrant.core :as ig]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(def ^:private body
  {:fhir/type :fhir/Parameters
   :parameter
   [{:fhir/type :fhir.Parameters/parameter
     :name #fhir/string "version"
     :value #fhir/string "4.0"}
    {:fhir/type :fhir.Parameters/parameter
     :name #fhir/string "default"
     :value #fhir/string "4.0"}]})

(defn- handler [_request]
  (ac/completed-future (ring/response body)))

(defmethod ig/init-key :blaze.fhir.operation/versions [_ _]
  (log/info "Init FHIR $versions operation handler")
  handler)
