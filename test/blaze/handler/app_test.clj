(ns blaze.handler.app-test
  (:require
    [blaze.handler.app :refer [handler]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]))


(st/instrument)


(def handlers
  {:handler/cql-evaluation (constantly ::cql-evaluation-handler)
   :handler/health (constantly ::health-handler)
   :handler.fhir/capabilities (constantly ::fhir-capabilities-handler)
   :handler.fhir/read (constantly ::fhir-read-handler)
   :handler.fhir/transaction (constantly ::fhir-transaction-handler)
   :handler.fhir/update (constantly ::fhir-update-handler)})


(defn app-handler [uri request-method]
  ((handler handlers) {:request-method request-method :uri uri}))


(deftest handler-test
  (testing "Routing"
    (are [uri request-method handler] (= (app-handler uri request-method) handler)
      "/cql/evaluate" :post ::cql-evaluation-handler
      "/health" :get ::health-handler
      "/health" :head ::health-handler
      "/fhir" :post ::fhir-transaction-handler
      "/fhir/metadata" :get ::fhir-capabilities-handler
      "/fhir/Patient/0" :get ::fhir-read-handler
      "/fhir/Patient/0" :put ::fhir-update-handler
      "/fhir/Observation/0" :get ::fhir-read-handler
      "/fhir/Observation/0" :put ::fhir-update-handler)))
