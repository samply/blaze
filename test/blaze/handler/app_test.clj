(ns blaze.handler.app-test
  (:require
    [blaze.handler.app :refer [handler router]]
    [blaze.middleware.fhir.type :refer [wrap-type]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log]))


(defn fixture [f]
  (st/instrument)
  (st/instrument
    [`handler]
    {:spec
     {`handler
      (s/fspec
        :args (s/cat :base-url #{::base-url} :conn #{::conn} :handlers map?))}})
  (st/instrument
    [`wrap-type]
    {:spec
     {`wrap-type
      (s/fspec
        :args (s/cat :handler fn? :conn #{::conn}))}})
  (log/with-merged-config {:level :fatal} (f))
  (st/unstrument))


(use-fixtures :each fixture)


(def ^:private handlers
  {:handler/cql-evaluation (fn [_] ::cql-evaluation-handler)
   :handler/health (fn [_] ::health-handler)
   :handler.fhir/capabilities (fn [_] ::fhir-capabilities-handler)
   :handler.fhir/create (fn [_] ::fhir-create-handler)
   :handler.fhir/delete (fn [_] ::fhir-delete-handler)
   :handler.fhir/history-instance (fn [_] ::fhir-history-instance-handler)
   :handler.fhir/history-type (fn [_] ::fhir-history-type-handler)
   :handler.fhir/history-system (fn [_] ::fhir-history-system-handler)
   :handler.fhir/read (fn [_] ::fhir-read-handler)
   :handler.fhir/search (fn [_] ::fhir-search-handler)
   :handler.fhir/transaction (fn [_] ::fhir-transaction-handler)
   :handler.fhir/update (fn [_] ::fhir-update-handler)})


(defn- match [path request-method]
  ((:handler (request-method (:data (reitit/match-by-path (router ::base-url ::conn handlers) path)))) {}))


(deftest router-test
  (are [path request-method handler] (= handler (match path request-method))
    "/cql/evaluate" :options ::cql-evaluation-handler
    "/cql/evaluate" :post ::cql-evaluation-handler
    "/health" :head ::health-handler
    "/health" :get ::health-handler
    "/fhir" :post ::fhir-transaction-handler
    "/fhir/metadata" :get ::fhir-capabilities-handler
    "/fhir/_history" :get ::fhir-history-system-handler
    "/fhir/Patient" :get ::fhir-search-handler
    "/fhir/Patient" :post ::fhir-create-handler
    "/fhir/Patient/_history" :get ::fhir-history-type-handler
    "/fhir/Patient/_search" :post ::fhir-search-handler
    "/fhir/Patient/0" :get ::fhir-read-handler
    "/fhir/Patient/0" :put ::fhir-update-handler
    "/fhir/Patient/0" :delete ::fhir-delete-handler
    "/fhir/Patient/0/_history" :get ::fhir-history-instance-handler
    "/fhir/Patient/0/_history/42" :get ::fhir-read-handler))


(deftest router-match-by-name-test
  (let [router (router ::base-url ::conn handlers)]
    (are [name params path]
      (= (reitit/match->path (reitit/match-by-name router name params)) path)

      :fhir/type
      {:type "Patient"}
      "/fhir/Patient"

      :fhir/instance
      {:type "Patient" :id "23"}
      "/fhir/Patient/23"

      :fhir/versioned-instance
      {:type "Patient" :id "23" :vid "42"}
      "/fhir/Patient/23/_history/42")))

(def ^:private handler-throwing
  (assoc handlers :handler.fhir/capabilities (fn [_] (throw (Exception. "")))))


(deftest exception-test
  (testing "Exceptions from handlers are converted to OperationOutcomes."
    (given @((handler ::base-url ::conn handler-throwing)
             {:uri "/fhir/metadata"
              :request-method :get})
      :status := 500
      [:headers "Content-Type"] := "application/fhir+json;charset=utf-8"
      :body :# #".*OperationOutcome.*")))
