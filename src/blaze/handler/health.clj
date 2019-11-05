(ns blaze.handler.health
  (:require
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(s/fdef handler
  :args (s/cat)
  :ret fn?)

(defn handler []
  (fn [_]
    (-> (ring/response "OK")
        (ring/content-type "text/plain"))))


(defmethod ig/init-key :blaze.handler/health
  [_ _]
  (log/info "Init health handler")
  (handler))
