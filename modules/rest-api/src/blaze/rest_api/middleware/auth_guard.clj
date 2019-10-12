(ns blaze.rest-api.middleware.auth-guard
  (:require
    [buddy.auth :refer [authenticated?]]
    [ring.util.response :as ring]))


(defn wrap-auth-guard
  "If the request is unauthenticated return a 401 response."
  [handler]
  (fn [request]
    (if (authenticated? request)
      (handler request)
      (-> (ring/response
            {:resourceType "OperationOutcome"
             :issue
             [{:severity "error"
               :code "login"
               :details
               {:coding
                [{:system "http://terminology.hl7.org/CodeSystem/operation-outcome"
                  :code "MSG_AUTH_REQUIRED"}]}}]})
          (ring/status 401)))))
