(ns blaze.rest-api.middleware.cors
  (:require
    [blaze.async.comp :refer [do-sync]]
    [ring.util.response :as ring]))


(defn wrap-cors
  [handler]
  (fn [request]
    (do-sync [response (handler request)]
      (ring/header response "Access-Control-Allow-Origin" "*"))))
