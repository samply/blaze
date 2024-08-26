(ns blaze.rest-api.middleware.sync
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [cognitect.anomalies :as anom]))

(defn wrap-sync
  "This middleware acts as a translation layer between the future-based asnyc
  handlers and traditional async ring handlers."
  [handler]
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
