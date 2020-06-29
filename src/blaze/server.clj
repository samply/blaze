(ns blaze.server
  "HTTP Server

  Call `init!` to initialize an HTTP server and `shutdown!` to release its port
  again."
  (:require
    [aleph.http :as http]
    [manifold.deferred :as md]
    [ring.util.response :as ring])
  (:import
    [java.io Closeable]))


(defn- wrap-server [handler server]
  (fn [request]
    (-> (handler request)
        (md/chain #(ring/header % "Server" server)))))


(defn init!
  "Creates a new HTTP server listening on `port` serving from `handler`.

  Call `shutdown!` on the returned server to stop listening and releasing its
  port."
  [port executor handler version]
  (http/start-server
    (wrap-server handler (str "Blaze/" version))
    {:port port :executor executor}))


(defn shutdown!
  "Shuts `server` down, releasing its port."
  [server]
  (.close ^Closeable server))
