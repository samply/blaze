(ns blaze.handler.app
  (:require
    [blaze.middleware.json :refer [wrap-json]]
    [clojure.spec.alpha :as s]
    [reitit.ring :as reitit-ring]
    [ring.util.response :as ring]))


(defn router [handlers]
  (reitit-ring/router
    [["/health"
      {:head (:handler/health handlers)
       :get (:handler/health handlers)}]
     ["/metrics"
      {:head (:handler/metrics handlers)
       :get (:handler/metrics handlers)}]
     ["/cql/evaluate"
      {:options (:handler/cql-evaluation handlers)
       :post (:handler/cql-evaluation handlers)}]
     ["/fhir" {:middleware [wrap-json]}
      [""
       {:post (:handler.fhir/transaction handlers)}]
      ["/metadata"
       {:get (:handler.fhir/capabilities handlers)}]
      ["/{type}"
       [""
        {:get (:handler.fhir/search handlers)
         :post (:handler.fhir/create handlers)}]
       ["/{id}"
        [""
         {:get (:handler.fhir/read handlers)
          :put (:handler.fhir/update handlers)
          :delete (:handler.fhir/delete handlers)}]
        ["/_history"
         [""
          {:get (:handler.fhir/history handlers)}]
         ["/{vid}"
          {:get (:handler.fhir/read handlers)}]]]]]]
    {:conflicts nil}))


(s/def ::handlers
  (s/keys :req [:handler/cql-evaluation
                :handler/health
                :handler/metrics
                :handler.fhir/create
                :handler.fhir/delete
                :handler.fhir/history
                :handler.fhir/read
                :handler.fhir/search
                :handler.fhir/transaction
                :handler.fhir/update]))


(s/fdef handler
  :args (s/cat :handlers ::handlers))

(defn handler
  "Whole app Ring handler."
  [handlers]
  (reitit-ring/ring-handler
    (router handlers)
    (fn [_]
      (ring/not-found "Not-Found"))))
