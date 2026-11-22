(ns blaze.middleware.fhir.auth-guard
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.auth :as auth]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.util :as handler-util]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(def ^:private ^:const msg-auth-required
  #fhir/Coding
   {:system #fhir/uri-interned "http://terminology.hl7.org/CodeSystem/operation-outcome"
    :code #fhir/code "MSG_AUTH_REQUIRED"})

(defn- not-authenticated-response
  {:arglists '([request])}
  [{:blaze.auth/keys [failure] :as request}]
  (log/warn (handler-util/format-request request) "- 401 - Unauthorized")
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
       (ring/status 401)
       (cond-> failure
         (ring/header "WWW-Authenticate" (auth/www-authenticate failure))))))

(defn- unavailable-response
  "Returns a 503 response, because the credentials couldn't be checked."
  {:arglists '([request])}
  [{:blaze.auth/keys [failure] :as request}]
  (log/warn (handler-util/format-request request) "- 503 - Service Unavailable")
  (ac/completed-future
   (handler-util/error-response failure)))

(defn- unauthenticated-response
  {:arglists '([request])}
  [{:blaze.auth/keys [failure] :as request}]
  (if (ba/unavailable? failure)
    (unavailable-response request)
    (not-authenticated-response request)))

(defn wrap-auth-guard
  "If the request is unauthenticated return a 401 response.

  The response has a `WWW-Authenticate` header built from the
  `:blaze.auth/failure` of the request, if present. If that failure has the
  category `unavailable`, because the credentials couldn't be checked, the
  response is a 503 without `WWW-Authenticate` header."
  [handler]
  (fn [request]
    (if (auth/authenticated? request)
      (handler request)
      (unauthenticated-response request))))
