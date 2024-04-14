(ns blaze.rest-api.middleware.sync
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [cognitect.anomalies :as anom]))

(defn wrap-sync [handler]
  (fn [request respond raise]
    (-> (try
          (handler request)
          (catch Throwable e
            (ac/completed-future (ba/anomaly e))))
        (ac/when-complete
         (fn [response e]
           (if e
             (raise (ex-info (::anom/message e) e))
             (respond response)))))))
