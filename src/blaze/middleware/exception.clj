(ns blaze.middleware.exception
  (:require
    [blaze.handler.util :as handler-util]))


(defn wrap-exception [handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable e
        (handler-util/error-response e)))))
