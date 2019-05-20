(ns blaze.handler.app
  (:require
    [bidi.ring :as bidi-ring]
    [clojure.spec.alpha :as s]
    [manifold.deferred :as md]
    [ring.util.response :as ring]))


(def ^:private routes
  ["/"
   {"health"
    {:head :handler/health
     :get :handler/health}
    "cql"
    {"/evaluate"
     {:options :handler/cql-evaluation
      :post :handler/cql-evaluation}}
    "fhir"
    {"" {:post :handler.fhir/transaction}
     "/metadata" {:get :handler.fhir/capabilities}
     ["/" :type]
     {:get :handler.fhir/search}
     ["/" :type "/" :id]
     {:get :handler.fhir/read
      :put :handler.fhir/update
      :delete :handler.fhir/delete}}}])


(defn wrap-server [handler server]
  (fn [request]
    (-> (handler request)
        (md/chain' #(ring/header % "Server" server)))))


(defn handler-intern [handlers]
  (bidi-ring/make-handler routes handlers))


(s/def ::handlers
  (s/keys :req [:handler/cql-evaluation
                :handler/health
                :handler.fhir/delete
                :handler.fhir/read
                :handler.fhir/search
                :handler.fhir/transaction
                :handler.fhir/update]))


(s/fdef handler
  :args (s/cat :handlers ::handlers :version string?))

(defn handler
  "Whole app Ring handler."
  [handlers version]
  (-> (handler-intern handlers)
      (wrap-server (str "Blaze/" version))))
