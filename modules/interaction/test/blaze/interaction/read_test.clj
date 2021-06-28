(ns blaze.interaction.read-test
  "Specifications relevant for the FHIR read interaction:

  https://www.hl7.org/fhir/http.html#read
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.interaction.read]
    [blaze.interaction.read-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log])
  (:import
    [java.time Instant]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn- handler [node]
  (-> (ig/init
        {:blaze.interaction/read
         {:node node}})
      (:blaze.interaction/read)))


(defn- handler-with [txs]
  (fn [request]
    (with-open [node (mem-node-with txs)]
      @((handler node) request))))


(def ^:private match
  {:data {:fhir.resource/type "Patient"}})


(deftest handler-test
  (testing "Returns Not Found on Non-Existing Resource"
    (let [{:keys [status body]}
          ((handler-with [])
            {:path-params {:id "0"}
             ::reitit/match match})]

      (is (= 404 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"not-found"
        [:issue 0 :diagnostics] := "Resource `/Patient/0` not found")))


  (testing "Returns Not Found on Invalid Version ID"
    (let [{:keys [status body]}
          ((handler-with [])
            {:path-params {:id "0" :vid "a"}
             ::reitit/match match})]

      (is (= 404 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"not-found"
        [:issue 0 :diagnostics] := "Resource `/Patient/0` with versionId `a` was not found.")))


  (testing "Returns Gone on Deleted Resource"
    (let [{:keys [status body headers]}
          ((handler-with
              [[[:put {:fhir/type :fhir/Patient :id "0"}]]
               [[:delete "Patient" "0"]]])
            {:path-params {:id "0"}
             ::reitit/match match})]

      (is (= 410 status))

      (testing "Transaction time in Last-Modified header"
        (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"deleted")))


  (testing "Returns Existing Resource"
    (let [{:keys [status headers body]}
          ((handler-with [[[:put {:fhir/type :fhir/Patient :id "0"}]]])
            {:path-params {:id "0"}
             ::reitit/match match})]

      (is (= 200 status))

      (testing "Transaction time in Last-Modified header"
        (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

      (testing "Version in ETag header"
        ;; 1 is the T of the transaction of the resource update
        (is (= "W/\"1\"" (get headers "ETag"))))

      (given body
        :fhir/type := :fhir/Patient
        :id := "0"
        [:meta :versionId] := #fhir/id"1"
        [:meta :lastUpdated] := Instant/EPOCH)))


  (testing "Returns Existing Resource on versioned read"
    (let [{:keys [status headers body]}
          ((handler-with [[[:put {:fhir/type :fhir/Patient :id "0"}]]])
            {:path-params {:id "0" :vid "1"}
             ::reitit/match match})]

      (is (= 200 status))

      (testing "Transaction time in Last-Modified header"
        (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

      (testing "Version in ETag header"
        ;; 1 is the T of the transaction of the resource update
        (is (= "W/\"1\"" (get headers "ETag"))))

      (given body
        :fhir/type := :fhir/Patient
        :id := "0"
        [:meta :versionId] := #fhir/id"1"
        [:meta :lastUpdated] := Instant/EPOCH))))
