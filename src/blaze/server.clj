(ns blaze.server
  "HTTP Server

  Call `init!` to initialize an HTTP server and `shutdown!` to release its port
  again."
  (:require
    [blaze.async.comp :as ac :refer [do-sync]]
    [ring.adapter.jetty :as ring-jetty]
    [ring.util.response :as ring])
  (:import
    [org.eclipse.jetty.server Server]))


(defn- wrap-server [handler server]
  (fn [request]
    (do-sync [response (handler request)]
      (ring/header response "Server" server))))


(defn- wrap-sync [handler]
  (fn [request respond raise]
    (-> (handler request)
        (ac/when-complete
          (fn [response e]
            (if response
              (respond response)
              (raise e)))))))


(defn init!
  "Creates a new HTTP server listening on `port` serving from `handler`.

  Call `shutdown!` on the returned server to stop listening and releasing its
  port."
  [port handler version]
  (ring-jetty/run-jetty
    (-> handler
        (wrap-server (str "Blaze/" version))
        (wrap-sync))
    {:port port
     :async? true
     :join? false
     :send-server-version? false}))


(defn shutdown!
  "Shuts `server` down, releasing its port."
  [server]
  (.stop ^Server server))
