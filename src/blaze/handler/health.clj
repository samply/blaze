(ns blaze.handler.health
  (:require
    [integrant.core :as ig]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(def ^:private response
  (-> (ring/response "OK")
      (ring/content-type "text/plain")))


(defmethod ig/init-key :blaze.handler/health
  [_ _]
  (log/info "Init health handler")
  (fn [_ respond _] (respond response)))
