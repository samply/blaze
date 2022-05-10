(ns blaze.server.jetty
  (:require
    [ring.core.protocols :as p]
    [ring.util.servlet :as servlet])
  (:import
    [blaze.server.jetty AsyncQueuedWriteListener QueuedOutputStream]
    [java.util.concurrent LinkedBlockingQueue]
    [javax.servlet AsyncContext]
    [javax.servlet.http HttpServletResponse]
    [org.eclipse.jetty.server
     ConnectionFactory HttpConfiguration HttpConnectionFactory Request Server
     ServerConnector]
    [org.eclipse.jetty.server.handler AbstractHandler]
    [org.eclipse.jetty.util BlockingArrayQueue]
    [org.eclipse.jetty.util.thread QueuedThreadPool ThreadPool]))


(set! *warn-on-reflection* true)


(defn- http-config ^HttpConfiguration [options]
  (doto (HttpConfiguration.)
    (.setSendDateHeader (:send-date-header? options true))
    (.setOutputBufferSize (:output-buffer-size options 32768))
    (.setRequestHeaderSize (:request-header-size options 8192))
    (.setResponseHeaderSize (:response-header-size options 8192))
    (.setSendServerVersion (:send-server-version? options true))))


(defn- server-connector ^ServerConnector [^Server server & factories]
  (ServerConnector. server #^"[Lorg.eclipse.jetty.server.ConnectionFactory;"
                    (into-array ConnectionFactory factories)))


(defn- http-connector ^ServerConnector [server options]
  (let [http-factory (HttpConnectionFactory. (http-config options))]
    (doto (server-connector server http-factory)
      (.setPort (options :port 80))
      (.setHost (options :host))
      (.setIdleTimeout (options :max-idle-time 200000)))))


(defn- set-headers
  "Update a HttpServletResponse with a map of headers."
  [^HttpServletResponse response, headers]
  (doseq [[key val-or-vals] headers]
    (if (string? val-or-vals)
      (.setHeader response key val-or-vals)
      (doseq [val val-or-vals]
        (.addHeader response key val))))
  ; Some headers must be set through specific methods
  (when-let [content-type (get headers "Content-Type")]
    (.setContentType response content-type)))


(defn- buffer-body! [queue body response]
  (let [out (QueuedOutputStream. queue)]
    (p/write-body-to-stream body response out)))


(defn- sync-handler [handler]
  (proxy [AbstractHandler] []
    (handle [_ ^Request jetty-request servlet-request
             ^HttpServletResponse servlet-response]
      (let [response (handler (servlet/build-request-map servlet-request))]
        (servlet/update-servlet-response servlet-response response))
      (.setHandled jetty-request true))))


(defn- async-handler [handler timeout]
  (proxy [AbstractHandler] []
    (handle [_ ^Request jetty-request servlet-request
             ^HttpServletResponse servlet-response]
      (let [^AsyncContext context (.startAsync jetty-request)]
        (.setTimeout context timeout)
        (handler
          (servlet/build-request-map servlet-request)
          (fn [{:keys [status headers body] :as response}]
            (when status
              (.setStatus servlet-response status))
            (set-headers servlet-response headers)
            (if body
              (let [queue (LinkedBlockingQueue.)
                    out (.getOutputStream servlet-response)]
                (.setWriteListener out (AsyncQueuedWriteListener. context out
                                                                  queue))
                (buffer-body! queue body response))
              (.complete context)))
          (fn [e]
            (.sendError servlet-response 500 (ex-message e))
            (.complete context))))
      (.setHandled jetty-request true))))


(defn- create-thread-pool ^ThreadPool [options]
  (let [min-threads (options :min-threads 8)
        max-threads (options :max-threads 50)
        queue-max-capacity (-> (options :max-queued-requests Integer/MAX_VALUE) (max 8))
        queue-capacity (-> min-threads (max 8) (min queue-max-capacity))
        blocking-queue (BlockingArrayQueue. queue-capacity
                                            queue-capacity
                                            queue-max-capacity)
        thread-idle-timeout (options :thread-idle-timeout 60000)
        pool (QueuedThreadPool. max-threads
                                min-threads
                                thread-idle-timeout
                                blocking-queue)]
    (when (:daemon? options false)
      (.setDaemon pool true))
    pool))


(defn- create-server ^Server [options]
  (let [pool (create-thread-pool options)
        server (Server. pool)]
    (.addConnector server (http-connector server options))
    server))


(defn run-jetty
  ^Server
  [handler options]
  (let [server (create-server options)]
    (.setHandler
      server
      (if (:async? options)
        (async-handler handler (:async-timeout options 0))
        (sync-handler handler)))
    (.start server)
    server))
