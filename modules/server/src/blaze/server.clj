(ns blaze.server
  "HTTP Server."
  (:require
    [blaze.server.spec]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [ring.adapter.jetty :as ring-jetty]
    [ring.util.response :as ring]
    [taoensso.timbre :as log])
  (:import
    [org.eclipse.jetty.server Server]))


(defn- wrap-server [handler server]
  (fn
    ([request]
     (ring/header (handler request) "Server" server))
    ([request respond raise]
     (handler request #(respond (ring/header % "Server" server)) raise))))


(defmethod ig/pre-init-spec :blaze/server [_]
  (s/keys :req-un [::port ::handler ::version]
          :opt-un [::async? ::min-threads ::max-threads]))


(defmethod ig/init-key :blaze/server
  [_ {:keys [port handler version async? min-threads max-threads]
      :or {async? false min-threads 8 max-threads 50}}]
  (log/info "Start main server on port" port)
  (ring-jetty/run-jetty
    (wrap-server handler (str "Blaze/" version))
    {:port port
     :async? async?
     :join? false
     :send-server-version? false
     :min-threads min-threads
     :max-threads max-threads}))


(defmethod ig/halt-key! :blaze/server
  [_ server]
  (log/info "Shutdown main server")
  (.stop ^Server server))
