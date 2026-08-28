(ns blaze.server.thread-pool
  "The thread pool of the main HTTP server.

  Blaze owns this pool instead of letting Jetty create one, so that its size is
  configurable and its saturation observable."
  (:require
   [blaze.module :as m]
   [blaze.server.spec]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [taoensso.timbre :as log])
  (:import
   [org.eclipse.jetty.util.thread QueuedThreadPool]))

(set! *warn-on-reflection* true)

(def ^:private min-threads
  "The number of threads the pool doesn't shrink below.

  Not configurable because it only decides how many idle threads are kept alive
  between bursts, not how much work the server can do at once."
  8)

(defmethod m/pre-init-spec :blaze.server/thread-pool [_]
  (s/keys :opt-un [:blaze.server/max-threads]))

(defmethod ig/init-key :blaze.server/thread-pool
  [_ {:keys [max-threads] :or {max-threads 50}}]
  (log/info "Start HTTP server thread pool with at most" max-threads "threads")
  (doto (QueuedThreadPool. max-threads min-threads)
    (.setName "http")
    (.start)))

(defmethod ig/halt-key! :blaze.server/thread-pool
  [_ pool]
  (log/info "Stop HTTP server thread pool")
  (.stop ^QueuedThreadPool pool))
