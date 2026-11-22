(ns blaze.middleware.auth-guard
  "Non-FHIR auth guard middleware.

  Returns a JSON map containing a message as body of 401 responses."
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.auth :as auth]
   [blaze.handler.util :as handler-util]
   [cognitect.anomalies :as anom]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(defn- not-authenticated-response
  {:arglists '([request])}
  [{:blaze.auth/keys [failure] :as request}]
  (log/warn (handler-util/format-request request) "- 401 - Unauthorized")
  (ac/completed-future
   (-> (ring/response {:msg "Authentication required."})
       (ring/status 401)
       (cond-> failure
         (ring/header "WWW-Authenticate" (auth/www-authenticate failure))))))

(defn- unavailable-response
  "Returns a 503 response, because the credentials couldn't be checked."
  {:arglists '([request])}
  [{:blaze.auth/keys [failure] :as request}]
  (log/warn (handler-util/format-request request) "- 503 - Service Unavailable")
  (ac/completed-future
   (-> (ring/response {:msg (::anom/message failure)})
       (ring/status 503))))

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
