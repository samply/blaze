(ns life-fhir-store.handler.health
  (:require
    [clojure.spec.alpha :as s]
    [ring.util.response :as ring]))


(s/def :handler/health fn?)


(s/fdef handler
  :args (s/cat)
  :ret :handler/health)

(defn handler []
  (fn [_]
    (-> (ring/response "OK")
        (ring/content-type "text/plain"))))
