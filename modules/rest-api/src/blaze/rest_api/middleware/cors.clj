(ns blaze.rest-api.middleware.cors
  (:require
   [ring.util.response :as ring]))

(defn- append-cors-header [response]
  (ring/header response "Access-Control-Allow-Origin" "*"))

(defn wrap-cors [handler]
  (fn [request respond raise]
    (handler request #(respond (append-cors-header %)) raise)))
