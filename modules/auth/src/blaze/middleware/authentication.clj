(ns blaze.middleware.authentication
  (:require
   [blaze.anomaly :as ba]
   [blaze.auth :as auth]))

(defn- assoc-authentication [request backend]
  (let [result (auth/authenticate backend request)]
    (if (ba/anomaly? result)
      (assoc request :blaze.auth/failure result)
      (assoc request :identity result))))

(defn wrap-authentication
  "Middleware authenticating requests with `backend`.

  Assocs the identity as `:identity` onto the request, if `backend`
  authenticates it, and the failure anomaly as `:blaze.auth/failure`
  otherwise. Never rejects a request itself. That is the job of the auth
  guards."
  [handler backend]
  (fn [request respond raise]
    (handler (assoc-authentication request backend) respond raise)))
