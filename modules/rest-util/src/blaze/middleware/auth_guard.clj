(ns blaze.middleware.auth-guard
  "Non-FHIR auth guard middleware.

  Returns a JSON map containing a message as body of 401 responses."
  (:require
   [blaze.async.comp :as ac]
   [blaze.handler.util :as handler-util]
   [buddy.auth :as auth]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(defn- not-authenticated-response [request]
  (log/warn (handler-util/format-request request) "- 401 - Unauthorized")
  (ac/completed-future
   (-> (ring/response {:msg "Authentication required."})
       (ring/status 401))))

(defn wrap-auth-guard
  "If the request is unauthenticated return a 401 response."
  [handler]
  (fn [request]
    (if (auth/authenticated? request)
      (handler request)
      (not-authenticated-response request))))
