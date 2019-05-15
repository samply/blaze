(ns blaze.handler.app
  (:require
    [bidi.ring :as bidi-ring]
    [clojure.spec.alpha :as s]))


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


(s/def ::handlers
  (s/keys :req [:handler/cql-evaluation
                :handler/health
                :handler.fhir/read
                :handler.fhir/transaction
                :handler.fhir/update]))


(s/fdef handler
  :args (s/cat :handlers ::handlers))

(defn handler
  "Whole app Ring handler."
  [handlers]
  (bidi-ring/make-handler routes handlers))
