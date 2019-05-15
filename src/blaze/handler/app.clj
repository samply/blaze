(ns blaze.handler.app
  (:require
    [bidi.ring :as bidi-ring]
    [clojure.spec.alpha :as s]
    [manifold.deferred :as md]
    [ring.util.response :as ring]))


(def ^:private routes
  ["/"
   {"health" :handler/health
    "cql" {"/evaluate" {:post :handler/cql-evaluation}}
    "fhir"
    {"" {:post :handler.fhir/transaction}
     "/metadata" {:get :handler.fhir/capabilities}
     ["/" :type "/" :id]
     {:get :handler.fhir/read
      :put :handler.fhir/update}}}])


(defn wrap-server [handler server]
  (fn [request]
    (-> (handler request)
        (md/chain' #(ring/header % "Server" server)))))


(defn handler-intern [handlers]
  (bidi-ring/make-handler routes handlers))


(s/def ::handlers
  (s/keys :req [:handler/cql-evaluation
                :handler/health
                :handler.fhir/read
                :handler.fhir/transaction
                :handler.fhir/update]))


(s/fdef handler
  :args (s/cat :handlers ::handlers :version string?))

(defn handler
  "Whole app Ring handler."
  [handlers version]
  (-> (handler-intern handlers)
      (wrap-server (str "Blaze/" version))))
