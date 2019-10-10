(ns blaze.handler.app-test
  (:require
    [blaze.handler.app :refer [handler router]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [juxt.iota :refer [given]]
    [manifold.deferred :as md]
    [reitit.ring :as reitit-ring]
    [taoensso.timbre :as log]))


(defn fixture [f]
  (st/instrument)
  (st/instrument
    [`handler]
    {:spec
     {`handler
      (s/fspec
        :args (s/cat :handlers map? :middleware map?))}})
  (log/with-merged-config {:level :fatal} (f))
  (st/unstrument))


(use-fixtures :each fixture)


(def ^:private handlers
  {:handler/cql-evaluation (fn [_] ::cql-evaluation-handler)
   :handler/health (fn [_] ::health-handler)
   :handler.fhir/core (fn [_] ::fhir-core-handler)})


(def ^:private middleware
  {:middleware/authentication identity})


(def ^:private test-handler
  (reitit-ring/ring-handler (router handlers middleware)))


(defn- match [path request-method]
  (let [response (test-handler {:request-method request-method :uri path})]
    (if (md/deferred? response)
      @response
      response)))


(deftest router-test
  (are [path request-method handler] (= handler (match path request-method))
    "/cql/evaluate" :options ::cql-evaluation-handler
    "/cql/evaluate" :post ::cql-evaluation-handler
    "/fhir" :get ::fhir-core-handler
    "/fhir/" :get ::fhir-core-handler
    "/fhir/foo" :get ::fhir-core-handler
    "/fhir" :post ::fhir-core-handler
    "/fhir" :put ::fhir-core-handler
    "/fhir" :delete ::fhir-core-handler))


(def ^:private handlers-throwing
  (assoc handlers :handler.fhir/core (fn [_] (throw (Exception. "")))))


(deftest exception-test
  (testing "Exceptions from handlers are converted to OperationOutcomes."
    (given @((handler handlers-throwing middleware)
             {:uri "/fhir"
              :request-method :get})
      :status := 500
      [:headers "Content-Type"] := "application/fhir+json;charset=utf-8"
      :body :# #".*OperationOutcome.*")))
