(ns blaze.rest-api.middleware.cors
  (:require
    [blaze.async-comp :as ac]
    [ring.util.response :as ring]))


(defn wrap-cors
  [handler]
  (fn [request]
    (-> (handler request)
        (ac/then-apply #(ring/header % "Access-Control-Allow-Origin" "*")))))
