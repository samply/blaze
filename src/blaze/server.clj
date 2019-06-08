(ns blaze.server
  "HTTP Server

  Call `init!` to initialize an HTTP server and `shutdown!` to release its port
  again."
  (:require
    [aleph.http :as http]
    [clojure.spec.alpha :as s]
    [manifold.deferred :as md]
    [manifold.executor :as ex]
    [ring.util.response :as ring])
  (:import
    [java.io Closeable]
    [java.util.concurrent ForkJoinPool ForkJoinWorkerThread
                          ForkJoinPool$ForkJoinWorkerThreadFactory]))


(s/def ::port
  (s/and nat-int? #(<= % 65535)))


(defn- wrap-server [handler server]
  (fn [request]
    (-> (handler request)
        (md/chain' #(ring/header % "Server" server)))))


(defn- thread
  "Creates a ForkJoinWorkerThread which sets `pool` into
  `manifold.executor/executor-thread-local` for deferreds to consume."
  [pool]
  (proxy [ForkJoinWorkerThread] [pool]
    (onStart []
      (.set ex/executor-thread-local pool))))


(defn- executor
  "Like `(java.util.concurrent.Executors/newWorkStealingPool)` but uses
  `manifold.executor/executor-thread-local` to put all deferreds onto the pool.

  That means, one can create a future in between request processing but
  subsequent deferreds through chain will be executed in the pool again."
  []
  (ForkJoinPool.
    (.availableProcessors (Runtime/getRuntime))
    (reify ForkJoinPool$ForkJoinWorkerThreadFactory
      (newThread [_ pool]
        (thread pool)))
    nil
    true))


(s/fdef init!
  :args (s/cat :port ::port :handler fn? :version string?))

(defn init!
  "Creates a new HTTP server listening on `port` serving from `handler`.

  Call `shutdown!` on the returned server to stop listening and releasing its
  port."
  [port handler version]
  (let [executor (executor)]
    {:server
     (http/start-server
       (wrap-server handler (str "Blaze/" version))
       {:port port :executor executor})
     :executor executor}))


(s/fdef shutdown!
  :args (s/cat :server map?))

(defn shutdown!
  "Shuts the server down releasing its port."
  [{:keys [server]}]
  (.close ^Closeable server))
