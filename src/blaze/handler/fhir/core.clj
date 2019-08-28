(ns blaze.handler.fhir.core
  (:require
    [blaze.middleware.fhir.type :refer [wrap-type]]
    [clojure.spec.alpha :as s]
    [datomic-spec.core :as ds]
    [reitit.ring :as reitit-ring]
    [ring.util.response :as ring]))


(defn router [base-url conn handlers]
  (reitit-ring/router
    ["" {:blaze/base-url base-url}
     ["" {:post (:handler.fhir/transaction handlers)}]
     ["metadata"
      {:get (:handler.fhir/capabilities handlers)}]
     ["_history"
      {:get (:handler.fhir/history-system handlers)}]
     ["{type}" {:middleware [[wrap-type conn]]}
      [""
       {:name :fhir/type
        :get (:handler.fhir/search handlers)
        :post (:handler.fhir/create handlers)}]
      ["/_history" {:get (:handler.fhir/history-type handlers)}]
      ["/_search" {:post (:handler.fhir/search handlers)}]
      ["/{id}"
       [""
        {:name :fhir/instance
         :get (:handler.fhir/read handlers)
         :put (:handler.fhir/update handlers)
         :delete (:handler.fhir/delete handlers)}]
       ["/_history"
        [""
         {:get (:handler.fhir/history-instance handlers)}]
        ["/{vid}"
         {:name :fhir/versioned-instance
          :get (:handler.fhir/read handlers)}]]]]
     ["Measure/{id}/$evaluate-measure"
      {:get
       (:handler.fhir.operation/evaluate-measure handlers)
       :post
       (:handler.fhir.operation/evaluate-measure handlers)}]]
    {:syntax :bracket
     :conflicts nil
     ::reitit-ring/default-options-handler
     (fn [_]
       (-> (ring/response nil)
           (ring/status 405)))}))


(def ^:private default-handler
  (reitit-ring/create-default-handler
    {:not-found
     (fn [_]
       (ring/not-found
         {:resourceType "OperationOutcome"
          :issue
          [{:severity "information"
            :code "not-found"}]}))
     :method-not-allowed
     (fn [_]
       (-> (ring/response
             {:resourceType "OperationOutcome"
              :issue
              [{:severity "information"
                :code "not-found"}]})
           (ring/status 405)))
     :not-acceptable
     (fn [_]
       (-> (ring/response
             {:resourceType "OperationOutcome"
              :issue
              [{:severity "error"
                :code "structure"}]})
           (ring/status 406)))}))


(s/def ::handlers
  (s/keys :req [:handler.fhir/capabilities
                :handler.fhir/create
                :handler.fhir/delete
                :handler.fhir/history-instance
                :handler.fhir/history-type
                :handler.fhir/history-system
                :handler.fhir/read
                :handler.fhir/search
                :handler.fhir/transaction
                :handler.fhir/update
                :handler.fhir.operation/evaluate-measure]))


(s/def :handler.fhir/core fn?)


(s/fdef handler
  :args (s/cat :base-url string? :conn ::ds/conn :handlers ::handlers)
  :ret :handler.fhir/core)

(defn handler
  "Whole app Ring handler."
  [base-url conn handlers]
  (reitit-ring/ring-handler
    (router base-url conn handlers)
    default-handler))
