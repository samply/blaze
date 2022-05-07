(ns blaze.server
  "HTTP Server."
  (:require
    [blaze.server.jetty :as jetty]
    [blaze.server.spec]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
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
          :opt-un [::name ::async? ::min-threads ::max-threads]))


(defmethod ig/init-key :blaze/server
  [_ {:keys [name port handler version async? min-threads max-threads]
      :or {name "main" async? false min-threads 8 max-threads 50}}]
  (log/info (format "Start %s server on port %d" name port))
  (jetty/run-jetty
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
