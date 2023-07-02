(ns blaze.handler.app
  (:require
    [blaze.handler.health.spec]
    [blaze.rest-api.spec]
    [blaze.spec]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [integrant.core :as ig]
    [muuntaja.parse :as parse]
    [reitit.ring]
    [ring.middleware.params :refer [wrap-params]]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(defn- options-handler [_ respond _]
  (-> (ring/response nil)
      (ring/status 405)
      respond))


(defn- router [health-handler]
  (reitit.ring/router
    [["/health"
      {:head health-handler
       :get health-handler}]]
    {:syntax :bracket
     :reitit.ring/default-options-endpoint {:handler options-handler}}))


(def ^:private parse-accept (parse/fast-memoize 1000 parse/parse-accept))


(defn- handler
  "Whole app Ring handler."
  [default-handler health-handler]
  (reitit.ring/ring-handler
    (router health-handler)
    default-handler))


(def ^:private rest-api-format?
  #{"xml" "text/xml" "application/xml" "application/fhir+xml"
    "json" "application/json" "application/fhir+json"})


(def ^:private frontend-format?
  #{"html" "text/html"})


(defn- frontend-request-fn [context-path]
  (let [prefix (str context-path "/__frontend/")]
    (fn [{:keys [uri] {:strs [accept]} :headers {format "_format"} :query-params}]
      (or (str/starts-with? uri prefix)
          (and (->> (parse-accept accept) first #{"text/html" "application/xhtml+xml"})
               (not (rest-api-format? format)))
          (frontend-format? format)))))


(defn- combined-handler [rest-api frontend-request? frontend]
  (fn [request respond raise]
    ((if (frontend-request? request)
       frontend
       rest-api)
     request respond raise)))


(defmethod ig/pre-init-spec :blaze.handler/app [_]
  (s/keys :req-un [:blaze/rest-api :blaze/health-handler]
          :opt-un [:blaze/frontend :blaze/context-path]))


(defmethod ig/init-key :blaze.handler/app
  [_
   {:keys [rest-api health-handler frontend context-path]
    :or {context-path ""}}]
  (log/info "Init app handler")
  (-> (handler
        (cond-> rest-api
          frontend (combined-handler (frontend-request-fn context-path) frontend))
        health-handler)
      (wrap-params)))
