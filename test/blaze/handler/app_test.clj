(ns blaze.handler.app-test
  (:require
    [blaze.handler.app :refer [handler router]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log]))


(st/instrument)


(def ^:private handlers
  {:handler/cql-evaluation (fn [_] ::cql-evaluation-handler)
   :handler/health (fn [_] ::health-handler)
   :handler/metrics (fn [_] ::metrics-handler)
   :handler.fhir/capabilities (fn [_] ::fhir-capabilities-handler)
   :handler.fhir/create (fn [_] ::fhir-create-handler)
   :handler.fhir/delete (fn [_] ::fhir-delete-handler)
   :handler.fhir/history (fn [_] ::fhir-history-handler)
   :handler.fhir/read (fn [_] ::fhir-read-handler)
   :handler.fhir/search (fn [_] ::fhir-search-handler)
   :handler.fhir/transaction (fn [_] ::fhir-transaction-handler)
   :handler.fhir/update (fn [_] ::fhir-update-handler)})


(defn- match [path request-method]
  ((:handler (request-method (:data (reitit/match-by-path (router handlers) path)))) {}))


(deftest router-test
  (are [path request-method handler] (= handler (match path request-method))
    "/cql/evaluate" :options ::cql-evaluation-handler
    "/cql/evaluate" :post ::cql-evaluation-handler
    "/health" :head ::health-handler
    "/health" :get ::health-handler
    "/metrics" :head ::metrics-handler
    "/metrics" :get ::metrics-handler
    "/fhir" :post ::fhir-transaction-handler
    "/fhir/metadata" :get ::fhir-capabilities-handler
    "/fhir/Patient" :get ::fhir-search-handler
    "/fhir/Patient" :post ::fhir-create-handler
    "/fhir/Patient/0" :get ::fhir-read-handler
    "/fhir/Patient/0" :put ::fhir-update-handler
    "/fhir/Patient/0" :delete ::fhir-delete-handler
    "/fhir/Patient/0/_history" :get ::fhir-history-handler
    "/fhir/Patient/0/_history/42" :get ::fhir-read-handler))


(def ^:private handler-throwing
  (assoc handlers :handler.fhir/capabilities (fn [_] (throw (Exception. "")))))


(deftest exception-test
  (testing "Exceptions from handlers are converted to OperationOutcomes."
    (given (log/with-merged-config
             {:level :fatal}
             @((handler handler-throwing)
                {:uri "/fhir/metadata"
                 :request-method :get}))
      :status := 500
      [:headers "Content-Type"] := "application/fhir+json;charset=utf-8"
      :body :# #".*OperationOutcome.*")))
