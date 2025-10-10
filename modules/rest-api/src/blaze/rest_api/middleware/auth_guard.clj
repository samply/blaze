(ns blaze.rest-api.middleware.auth-guard
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir.spec.type :as type]
   [blaze.rest-api.middleware.log :refer [format-request]]
   [buddy.auth :as auth]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(def ^:private ^:const msg-auth-required
  #fhir/Coding
   {:system #fhir/uri "http://terminology.hl7.org/CodeSystem/operation-outcome"
    :code #fhir/code "MSG_AUTH_REQUIRED"})

(defn not-authenticated-response [request]
  (log/warn (format-request request) "- 401 - Unauthorized")
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
       (ring/status 401))))

(defn wrap-auth-guard
  "If the request is unauthenticated return a 401 response."
  [handler]
  (fn [request]
    (if (auth/authenticated? request)
      (handler request)
      (not-authenticated-response request))))
