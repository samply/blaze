(ns blaze.handler.health
  (:require
    [blaze.async.comp :as ac]
    [integrant.core :as ig]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(def ^:private response
  (-> (ring/response "OK")
      (ring/content-type "text/plain")
      (ac/completed-future)))


(defmethod ig/init-key :blaze.handler/health
  [_ _]
  (log/info "Init health handler")
  (constantly response))
