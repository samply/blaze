(ns blaze.http-client
  (:require
   [blaze.http-client.spec]
   [clojure.spec.alpha :as s]
   [hato.client :as hc]
   [integrant.core :as ig]
   [taoensso.timbre :as log]))

(defmethod ig/pre-init-spec :blaze/http-client [_]
  (s/keys :opt-un [::connect-timeout]))

(defmethod ig/init-key :blaze/http-client
  [_ {:keys [connect-timeout] :or {connect-timeout 5000}}]
  (log/info "Init HTTP client with a connect timeout of" connect-timeout "ms")
  (hc/build-http-client {:connect-timeout connect-timeout}))
