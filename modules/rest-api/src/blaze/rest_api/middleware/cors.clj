(ns blaze.rest-api.middleware.cors
  (:require
    [manifold.deferred :as md]
    [ring.util.response :as ring]))


(defn wrap-cors
  [handler]
  (fn [request]
    (-> (handler request)
        (md/chain' #(ring/header % "Access-Control-Allow-Origin" "*")))))
