(ns blaze.handler.app
  (:require
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [reitit.ring]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(defn router [health-handler]
  (reitit.ring/router
    [["/health"
      {:head health-handler
       :get health-handler}]]
    {:syntax :bracket
     :reitit.ring/default-options-endpoint
     {:handler
      (fn [_]
        (-> (ring/response nil)
            (ring/status 405)))}}))


(s/fdef handler
  :args (s/cat :rest-api fn? :health-handler fn?))

(defn handler
  "Whole app Ring handler."
  [rest-api health-handler]
  (reitit.ring/ring-handler
    (router health-handler)
    rest-api))


(defmethod ig/init-key :blaze.handler/app
  [_ {:keys [rest-api health-handler]}]
  (log/info "Init app handler")
  (handler rest-api health-handler))
