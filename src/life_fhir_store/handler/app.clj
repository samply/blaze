(ns life-fhir-store.handler.app
  (:require
    [bidi.ring :as bidi-ring]
    [clojure.spec.alpha :as s]
    [life-fhir-store.handler.cql-evaluation]
    [life-fhir-store.handler.fhir.transaction]
    [ring.util.response :as ring]))


(def ^:private routes
  ["/"
   {"health" :handler/health
    "cql" {"/evaluate" :handler/cql-evaluation}
    "fhir" {"" :handler.fhir/transaction}}])


(defn- wrap-not-found [handler]
  (fn [req]
    (if-let [resp (handler req)]
      resp
      (-> (ring/not-found "Not Found")
          (ring/content-type "text/plain")))))


(s/def ::handlers
  (s/keys :req [:handler/health :handler/cql-evaluation
                :handler.fhir/transaction]))


(s/fdef handler
  :args (s/cat :handlers ::handlers))

(defn handler
  "Whole app Ring handler."
  [handlers]
  (-> (bidi-ring/make-handler routes handlers)
      (wrap-not-found)))


(comment
  (bidi.bidi/match-route routes "/health")
  (bidi.bidi/match-route routes "/cql/evaluate")
  (bidi.bidi/match-route routes "/fhir")
  )
