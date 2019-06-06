(ns blaze.server
  "HTTP Server

  Call `init!` to initialize an HTTP server and `shutdown!` to release its port
  again."
  (:require
    [aleph.http :as http]
    [clojure.spec.alpha :as s]
    [manifold.deferred :as md]
    [ring.util.response :as ring]
    [taoensso.timbre :as log])
  (:import
    [java.io Closeable]
    [java.util.concurrent Executors]))


(s/def ::port
  (s/and nat-int? #(<= % 65535)))


(defn- wrap-server [handler server]
  (fn [request]
    (-> (handler request)
        (md/chain' #(ring/header % "Server" server)))))


(s/fdef init!
  :args (s/cat :port ::port :handler fn? :version string?))

(defn init!
  "Creates a new HTTP server listening on `port` serving from `handler`.

  Call `shutdown!` on the returned server to stop listening and releasing its
  port."
  [port handler version]
  (log/info (str "Start Blaze v" version " on port " port))
  (http/start-server
    (wrap-server handler (str "Blaze/" version))
    {:port port :executor (Executors/newWorkStealingPool)}))


(s/fdef shutdown!
  :args (s/cat :server #(instance? Closeable %)))

(defn shutdown!
  "Shuts the server down releasing its port."
  [server]
  (log/info "Stop server")
  (.close ^Closeable server))
