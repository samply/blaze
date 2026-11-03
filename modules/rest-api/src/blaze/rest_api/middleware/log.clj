(ns blaze.rest-api.middleware.log
  (:require
   [blaze.handler.util :as handler-util]
   [taoensso.timbre :as log]))

(defn wrap-log
  [handler]
  (fn [request respond raise]
    (log/debug (handler-util/format-request request))
    (handler request respond raise)))
