(ns blaze.middleware.guard
  (:require
   [buddy.auth :refer [authenticated?]]
   [ring.util.response :as ring]))


(defn wrap-guard
  "If the request is unauthenticated return a 401 response."
  [handler]
  (fn [request]
    (if (authenticated? request)
      (handler request)
      (-> (ring/response {:message "Unauthenticated"})
          (ring/status 401)))))
