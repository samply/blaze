(ns blaze.server
  "HTTP Server

  Call `init!` to initialize an HTTP server and `shutdown!` to release its port
  again."
  (:require
    [aleph.http :as http]
    [blaze.executors :as ex]
    [clojure.spec.alpha :as s]
    [manifold.deferred :as md]
    [ring.util.response :as ring])
  (:import
    [java.io Closeable]))


(s/def ::port
  (s/and nat-int? #(<= % 65535)))


(defn- wrap-server [handler server]
  (fn [request]
    (-> (handler request)
        (md/chain' #(ring/header % "Server" server)))))


(s/fdef init!
  :args (s/cat :port ::port :executor ex/executor? :handle fn?
               :version string? :ssl-context any?))

(defn init!
  "Creates a new HTTP server listening on `port` serving from `handler`.

  Call `shutdown!` on the returned server to stop listening and releasing its
  port."
  [port executor handler version ssl-context]
  (http/start-server
    (wrap-server handler (str "Blaze/" version))
    {:port port :executor executor :ssl-context ssl-context}))


(s/fdef shutdown!
  :args (s/cat :server #(instance? Closeable %)))

(defn shutdown!
  "Shuts `server` down, releasing its port."
  [server]
  (.close ^Closeable server))
