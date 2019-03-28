(ns life-fhir-store.server
  "HTTP Server

  Call `init!` to initialize an HTTP server and `shutdown!` to release its port
  again."
  (:require
    [aleph.http :as http]
    [clojure.spec.alpha :as s]
    [taoensso.timbre :as log])
  (:import
    [java.io Closeable]))



;; ---- Specs -----------------------------------------------------------------

(s/def ::port
  (s/and nat-int? #(<= % 65535)))



;; ---- Functions -------------------------------------------------------------

(s/fdef init!
  :args (s/cat :port ::port :handler fn?))

(defn init!
  "Creates a new HTTP server listening on `port` serving from `handler`.

  Call `shutdown!` on the returned server to stop listening and releasing its
  port."
  [port handler]
  (log/info "Start server on port" port)
  (http/start-server handler {:port port}))


(s/fdef shutdown!
  :args (s/cat :server #(instance? Closeable %)))

(defn shutdown!
  "Shuts the server down releasing its port."
  [server]
  (log/info "Stop server")
  (.close ^Closeable server))
