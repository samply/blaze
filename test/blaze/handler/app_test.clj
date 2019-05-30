(ns blaze.handler.app-test
  (:require
    [blaze.handler.app :refer [handler-intern]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]))


(st/instrument)


(def handlers
  {:handler/cql-evaluation (constantly ::cql-evaluation-handler)
   :handler/health (constantly ::health-handler)
   :handler.fhir/capabilities (constantly ::fhir-capabilities-handler)
   :handler.fhir/create (constantly ::fhir-create-handler)
   :handler.fhir/delete (constantly ::fhir-delete-handler)
   :handler.fhir/history (constantly ::fhir-history-handler)
   :handler.fhir/read (constantly ::fhir-read-handler)
   :handler.fhir/search (constantly ::fhir-search-handler)
   :handler.fhir/transaction (constantly ::fhir-transaction-handler)
   :handler.fhir/update (constantly ::fhir-update-handler)})


(defn app-handler [uri request-method]
  ((handler-intern handlers) {:request-method request-method :uri uri}))


(deftest handler-test
  (testing "Routing"
    (are [uri request-method handler] (= (app-handler uri request-method) handler)
      "/cql/evaluate" :options ::cql-evaluation-handler
      "/cql/evaluate" :post ::cql-evaluation-handler
      "/health" :head ::health-handler
      "/health" :get ::health-handler
      "/fhir" :post ::fhir-transaction-handler
      "/fhir/" :post ::fhir-transaction-handler
      "/fhir/metadata" :get ::fhir-capabilities-handler
      "/fhir/Patient" :get ::fhir-search-handler
      "/fhir/Patient" :post ::fhir-create-handler
      "/fhir/Patient/0" :get ::fhir-read-handler
      "/fhir/Patient/0" :put ::fhir-update-handler
      "/fhir/Patient/0" :delete ::fhir-delete-handler
      "/fhir/Patient/0/_history" :get ::fhir-history-handler
      "/fhir/Patient/0/_history/42" :get ::fhir-read-handler)))
