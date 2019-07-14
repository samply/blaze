(ns blaze.handler.app
  (:require
    [blaze.middleware.json :refer [wrap-json]]
    [blaze.middleware.fhir.type :refer [wrap-type]]
    [clojure.spec.alpha :as s]
    [reitit.core :as reitit]
    [reitit.ring :as reitit-ring]))


(defn- wrap-remove-context-path [handler]
  (fn [{{{:keys [more] :or {more ""}} :path-params} ::reitit/match :as request}]
    (handler (assoc request :uri more))))


(defn router [handlers]
  (reitit-ring/router
    [["/health"
      {:head (:handler/health handlers)
       :get (:handler/health handlers)}]
     ["/cql/evaluate"
      {:options (:handler/cql-evaluation handlers)
       :post (:handler/cql-evaluation handlers)}]
     ["/fhir"
      {:middleware [wrap-json wrap-remove-context-path]
       :handler (:handler.fhir/core handlers)}]
     ["/fhir/{*more}"
      {:middleware [wrap-json wrap-remove-context-path]
       :handler (:handler.fhir/core handlers)}]]
    {:syntax :bracket}))


(s/def ::handlers
  (s/keys :req [:handler/cql-evaluation
                :handler/health
                :handler.fhir/core]))


(s/fdef handler
  :args (s/cat :handlers ::handlers))

(defn handler
  "Whole app Ring handler."
  [handlers]
  (reitit-ring/ring-handler (router handlers)))
