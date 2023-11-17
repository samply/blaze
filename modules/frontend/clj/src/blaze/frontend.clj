(ns blaze.frontend
  (:require
   [blaze.frontend.spec]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [integrant.core :as ig]
   [reitit.ring]
   [ring.middleware.content-type :refer [wrap-content-type]]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(defn- index-response []
  (-> (ring/resource-response "public/index.html")
      (update :headers assoc "Content-Type" "text/html" "Cache-Control" "no-cache")))

(defn- cache-control-response [path response]
  (assoc-in response [:headers "Cache-Control"]
            (if (str/starts-with? path "/__frontend/immutable")
              "public, max-age=604800, immutable"
              "no-cache")))

(defn- handle [path respond]
  (if-let [response (ring/resource-response path {:root "public"})]
    (respond (cache-control-response path response))
    (respond (index-response))))

(defn- handler [context-path]
  (fn [{:keys [uri]} respond _]
    (log/debug "GET" (str "[base]" uri))
    (handle (subs uri (count context-path)) respond)))

(defmethod ig/pre-init-spec :blaze/frontend [_]
  (s/keys :opt-un [:blaze/context-path]))

(defmethod ig/init-key :blaze/frontend
  [_ {:keys [context-path]}]
  (log/info "Init Frontend")
  (wrap-content-type (handler context-path)))
