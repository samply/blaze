(ns blaze.rest-api.middleware.auth-guard
  (:require
    [blaze.fhir.spec.type]
    [buddy.auth :refer [authenticated?]]
    [ring.util.response :as ring]))


(defn wrap-auth-guard
  "If the request is unauthenticated return a 401 response."
  [handler]
  (fn [request]
    (if (authenticated? request)
      (handler request)
      (-> (ring/response
            {:fhir/type :fhir/OperationOutcome
             :issue
             [{:fhir/type :fhir.OperationOutcome/issue
               :severity #fhir/code"error"
               :code #fhir/code"login"
               :details
               {:fhir/type :fhir/CodeableConcept
                :coding
                [{:fhir/type :fhir/Coding
                  :system #fhir/uri"http://terminology.hl7.org/CodeSystem/operation-outcome"
                  :code #fhir/code"MSG_AUTH_REQUIRED"}]}}]})
          (ring/status 401)))))
