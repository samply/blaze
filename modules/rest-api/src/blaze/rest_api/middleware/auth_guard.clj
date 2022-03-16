(ns blaze.rest-api.middleware.auth-guard
  (:require
    [blaze.async.comp :as ac]
    [blaze.fhir.spec.type :as type]
    [buddy.auth :as auth]
    [ring.util.response :as ring]))


(def ^:private ^:const msg-auth-required
  #fhir/Coding
      {:system #fhir/uri "http://terminology.hl7.org/CodeSystem/operation-outcome"
       :code #fhir/code "MSG_AUTH_REQUIRED"})


(defn wrap-auth-guard
  "If the request is unauthenticated return a 401 response."
  [handler]
  (fn [request]
    (if (auth/authenticated? request)
      (handler request)
      (ac/completed-future
        (-> (ring/response
              {:fhir/type :fhir/OperationOutcome
               :issue
               [{:fhir/type :fhir.OperationOutcome/issue
                 :severity #fhir/code "error"
                 :code #fhir/code "login"
                 :details
                 (type/codeable-concept
                   {:coding [msg-auth-required]})}]})
            (ring/status 401))))))
