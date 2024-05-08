(ns blaze.http-client
  (:require
   [blaze.http-client.spec]
   [blaze.module :as m]
   [clojure.spec.alpha :as s]
   [hato.client :as hc]
   [integrant.core :as ig]
   [taoensso.timbre :as log]))

(defmethod m/pre-init-spec :blaze/http-client [_]
  (s/keys :opt-un [::connect-timeout ::trust-store ::trust-store-pass]))

(defn init-msg [connect-timeout trust-store]
  (if trust-store
    (format "Init HTTP client with trust store %s and a connect timeout of %d ms"
            trust-store connect-timeout)
    (format "Init HTTP client with a connect timeout of %d ms" connect-timeout)))

(defmethod ig/init-key :blaze/http-client
  [_ {:keys [connect-timeout trust-store trust-store-pass]
      :or {connect-timeout 5000}}]
  (log/info (init-msg connect-timeout trust-store))
  (hc/build-http-client
   (cond-> {:connect-timeout connect-timeout}
     trust-store
     (assoc :ssl-context {:trust-store trust-store
                          :trust-store-pass trust-store-pass}))))
