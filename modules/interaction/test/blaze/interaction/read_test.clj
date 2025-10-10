(ns blaze.interaction.read-test
  "Specifications relevant for the FHIR read interaction:

  https://www.hl7.org/fhir/http.html#read
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
   [blaze.anomaly-spec]
   [blaze.async.comp :as ac]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.db.resource-store :as rs]
   [blaze.db.spec]
   [blaze.interaction.read]
   [blaze.interaction.test-util :refer [wrap-error]]
   [blaze.middleware.fhir.db :refer [wrap-db]]
   [blaze.middleware.fhir.db-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [juxt.iota :refer [given]]
   [reitit.core :as reitit]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def config
  (assoc api-stub/mem-node-config :blaze.interaction/read {}))

(def match
  (reitit/map->Match {:data {:fhir.resource/type "Patient"}}))

(def operation-outcome
  #fhir/uri "http://terminology.hl7.org/CodeSystem/operation-outcome")

(defn wrap-defaults [handler]
  (fn [request]
    (handler (assoc request ::reitit/match match))))

(defmacro with-handler [[handler-binding] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         handler# :blaze.interaction/read} config]
       ~txs
       (let [~handler-binding (-> handler# wrap-defaults (wrap-db node# 100)
                                  wrap-error)]
         ~@body))))

(deftest handler-test
  (testing "returns Not-Found on non-existing resource"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}})]

        (is (= 404 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "not-found"
          [:issue 0 :diagnostics] := #fhir/string "Resource `Patient/0` was not found."))))

  (testing "returns Bad-Request on invalid id"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler {:path-params {:id "A_B"}})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "value"
          [:issue 0 :details :coding 0 :system] := operation-outcome
          [:issue 0 :details :coding 0 :code] := #fhir/code "MSG_ID_INVALID"
          [:issue 0 :diagnostics] := #fhir/string "Resource id `A_B` is invalid."))))

  (testing "returns Gone on deleted resource"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:delete "Patient" "0"]]]

      (let [{:keys [status body headers]}
            @(handler {:path-params {:id "0"}})]

        (is (= 410 status))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "t in ETag header"
          (is (= "W/\"2\"" (get headers "ETag"))))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "deleted"
          [:issue 0 :diagnostics] := #fhir/string "Resource `Patient/0` was deleted."))))

  (testing "returns Internal Server Error on missing resource content"
    (with-redefs [rs/get (fn [_ _] (ac/completed-future nil))]
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

        (let [{:keys [status body]}
              @(handler {:path-params {:id "0"}})]

          (is (= 500 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "incomplete"
            [:issue 0 :diagnostics] := #fhir/string "The resource content of `Patient/0` with hash `C9ADE22457D5AD750735B6B166E3CE8D6878D09B64C2C2868DCB6DE4C9EFBD4F` was not found.")))))

  (testing "returns existing resource"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (let [{:keys [status headers body]}
            @(handler {:path-params {:id "0"}})]

        (is (= 200 status))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "Version in ETag header"
          ;; 1 is the T of the transaction of the resource update
          (is (= "W/\"1\"" (get headers "ETag"))))

        (given body
          :fhir/type := :fhir/Patient
          :id := "0"
          [:meta :versionId] := #fhir/id "1"
          [:meta :lastUpdated] := #fhir/instant #system/date-time "1970-01-01T00:00:00Z")))))
