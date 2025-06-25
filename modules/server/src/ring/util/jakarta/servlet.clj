(ns ring.util.jakarta.servlet
  "Compatibility functions for turning a ring handler into a Java servlet.

  This is a copy of the original ring.util.jakarta.servlet namespace with the
  function make-output-stream replaced for performance reasons."
  (:require
   [clojure.string :as str]
   [ring.core.protocols :as protocols])
  (:import
   [blaze.server ContextOutputStream]
   [jakarta.servlet AsyncContext]
   [jakarta.servlet.http HttpServletRequest HttpServletResponse]
   [java.util Locale]))

(set! *warn-on-reflection*  true)

(defn- get-headers [^HttpServletRequest request]
  (reduce
   (fn [headers ^String name]
     (assoc headers
            (.toLowerCase name Locale/ENGLISH)
            (->> (.getHeaders request name)
                 (enumeration-seq)
                 (str/join ","))))
   {}
   (enumeration-seq (.getHeaderNames request))))

(defn- get-content-length [^HttpServletRequest request]
  (let [length (.getContentLength request)]
    (when (>= length 0) length)))

(defn- get-client-cert [^HttpServletRequest request]
  (first (.getAttribute request "jakarta.servlet.request.X509Certificate")))

(defn build-request-map
  "Create the request map from the HttpServletRequest object."
  [^HttpServletRequest request]
  {:server-port (.getServerPort request)
   :server-name (.getServerName request)
   :remote-addr (.getRemoteAddr request)
   :uri (.getRequestURI request)
   :query-string (.getQueryString request)
   :scheme (keyword (.getScheme request))
   :request-method (keyword (.toLowerCase (.getMethod request) Locale/ENGLISH))
   :protocol (.getProtocol request)
   :headers (get-headers request)
   :content-type (.getContentType request)
   :content-length (get-content-length request)
   :character-encoding (.getCharacterEncoding request)
   :ssl-client-cert (get-client-cert request)
   :body (.getInputStream request)})

(defn- set-headers [^HttpServletResponse response, headers]
  (doseq [[key val-or-vals] headers]
    (if (string? val-or-vals)
      (.setHeader response key val-or-vals)
      (doseq [val val-or-vals]
        (.addHeader response key val))))
  ; Some headers must be set through specific methods
  (when-let [content-type (get headers "Content-Type")]
    (.setContentType response content-type)))

(defn- make-output-stream
  [^HttpServletResponse response ^AsyncContext context]
  (let [os (.getOutputStream response)]
    (if (nil? context)
      os
      (ContextOutputStream. os context))))

(defn update-servlet-response
  "Update the HttpServletResponse using a response map. Takes an optional
  AsyncContext."
  ([response response-map]
   (update-servlet-response response nil response-map))
  ([^HttpServletResponse response context response-map]
   (let [{:keys [status headers body]} response-map]
     (when (nil? response)
       (throw (NullPointerException. "HttpServletResponse is nil")))
     (when (nil? response-map)
       (throw (NullPointerException. "Response map is nil")))
     (when status
       (.setStatus response status))
     (set-headers response headers)
     (let [output-stream (make-output-stream response context)]
       (protocols/write-body-to-stream body response-map output-stream)))))
