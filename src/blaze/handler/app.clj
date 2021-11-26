(ns blaze.handler.app
  (:require
    [blaze.async.comp :as ac]
    [blaze.handler.health.spec]
    [blaze.rest-api.spec]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [reitit.ring]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(defn- options-handler [_]
  (-> (ring/response nil)
      (ring/status 405)))


(defn- router [health-handler]
  (reitit.ring/router
    [["/health"
      {:head health-handler
       :get health-handler}]]
    {:syntax :bracket
     :reitit.ring/default-options-endpoint {:handler options-handler}}))


(defn- handler
  "Whole app Ring handler."
  [rest-api health-handler]
  (reitit.ring/ring-handler
    (router health-handler)
    rest-api))


(defn- wrap-sync [handler]
  (fn [request respond raise]
    (-> (handler request)
        (ac/when-complete
          (fn [response e]
            (if response
              (respond response)
              (raise e)))))))


(defmethod ig/pre-init-spec :blaze.handler/app [_]
  (s/keys :req-un [:blaze/rest-api :blaze/health-handler]))


(defmethod ig/init-key :blaze.handler/app
  [_ {:keys [rest-api health-handler]}]
  (log/info "Init app handler")
  (wrap-sync (handler rest-api health-handler)))
