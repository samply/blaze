(ns blaze.server
  "HTTP Server."
  (:require
   [blaze.module :as m]
   [blaze.server.spec]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [ring.adapter.jetty :as ring-jetty]
   [ring.util.jakarta.servlet]
   [ring.util.response :as ring]
   [taoensso.timbre :as log])
  (:import
   [blaze.server ContextOutputStream]
   [jakarta.servlet AsyncContext]
   [jakarta.servlet.http HttpServletResponse]
   [org.eclipse.jetty.server Server]))

(set! *warn-on-reflection* true)

(alter-var-root
 #'ring.util.jakarta.servlet/make-output-stream
 (constantly
  (fn [^HttpServletResponse response ^AsyncContext context]
    (let [os (.getOutputStream response)]
      (if (nil? context)
        os
        (ContextOutputStream. os context))))))

(defn- server-request [request]
  (assoc request :blaze/request-arrived (System/nanoTime)))

(defn- wrap-server [handler server]
  (fn
    ([request]
     (-> request server-request handler (ring/header "Server" server)))
    ([request respond raise]
     (-> (server-request request)
         (handler #(respond (ring/header % "Server" server)) raise)))))

(defmethod m/pre-init-spec :blaze/server [_]
  (s/keys :req-un [::port ::handler ::version]
          :opt-un [::name ::async? ::min-threads ::max-threads]))

(defmethod ig/init-key :blaze/server
  [_ {:keys [name port handler version async? min-threads max-threads]
      :or {name "main" async? false min-threads 8 max-threads 50}}]
  (log/info (format "Start %s server on port %d" name port))
  (ring-jetty/run-jetty
   (wrap-server handler (str "Blaze/" version))
   {:port port
    :async? async?
    ;; TODO: remove such a long timeout only here because of FHIR_OPERATION_EVALUATE_MEASURE_TIMEOUT
    :async-timeout 3610000                                  ; 1 h and 10 s
    :join? false
    :send-server-version? false
    :min-threads min-threads
    :max-threads max-threads}))

(defmethod ig/halt-key! :blaze/server
  [_ server]
  (log/info "Shutdown main server")
  (.stop ^Server server))
