(ns blaze.handler.app
  (:require
    [blaze.middleware.json :refer [wrap-json]]
    [clojure.spec.alpha :as s]
    [reitit.core :as reitit]
    [reitit.ring :as reitit-ring]
    [ring.util.response :as ring]))


(defn- wrap-remove-context-path [handler]
  (fn [{{{:keys [more] :or {more ""}} :path-params} ::reitit/match :as request}]
    (handler (-> request (assoc :uri more) (dissoc ::reitit/match :path-params)))))


(defn router [handlers middleware]
  (reitit-ring/router
    [["/health"
      {:head (:handler/health handlers)
       :get (:handler/health handlers)}]
     ["/fhir"
      {:middleware [wrap-json (:middleware/authentication middleware) wrap-remove-context-path]
       :handler (:handler.fhir/core handlers)}]
     ["/fhir/{*more}"
      {:middleware [wrap-json (:middleware/authentication middleware) wrap-remove-context-path]
       :handler (:handler.fhir/core handlers)}]]
    {:syntax :bracket
     ::reitit-ring/default-options-handler
     (fn [_]
       (-> (ring/response nil)
           (ring/status 405)))}))


(s/def ::handlers
  (s/keys :req [:handler/health
                :handler.fhir/core]))


(s/def ::middleware
  (s/keys :req [:middleware/authentication]))


(s/fdef handler
  :args (s/cat :handlers ::handlers
               :middleware ::middleware))

(defn handler
  "Whole app Ring handler."
  [handlers middleware]
  (reitit-ring/ring-handler (router handlers middleware)))
