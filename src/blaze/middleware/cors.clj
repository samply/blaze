(ns blaze.middleware.cors
  (:require
    [manifold.deferred :as md]))


(defn assoc-header [response]
  (assoc-in response [:headers "Access-Control-Allow-Origin"] "*"))


(defn wrap-cors
  "Adds an Access-Control-Allow-Origin header with the value * to responses."
  [handler]
  (fn [request]
    (if (= :options (:request-method request))
      {:status 204
       :headers {"Access-Control-Allow-Origin" "*"
                 "Access-Control-Allow-Headers" "Accept, Content-Type"
                 "Access-Control-Max-Age" "3600"}}
      (md/let-flow' [response (handler request)]
        (assoc-header response)))))
