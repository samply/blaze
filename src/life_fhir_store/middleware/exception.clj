(ns life-fhir-store.middleware.exception
  (:require
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(defn wrap-exception [handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable e
        (log/error (.getMessage e) e)
        (-> (ring/response {:error (.getMessage e)})
            (ring/status 500))))))
