(ns blaze.interaction.vread-test
  "Specifications relevant for the FHIR read interaction:

  https://www.hl7.org/fhir/http.html#read
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
   [blaze.anomaly-spec]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.db.spec]
   [blaze.interaction.test-util :refer [wrap-error]]
   [blaze.interaction.vread]
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
  (assoc api-stub/mem-node-config :blaze.interaction/vread {}))

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
                         handler# :blaze.interaction/vread} config]
       ~txs
       (let [~handler-binding (-> handler# wrap-defaults (wrap-db node# 100)
                                  wrap-error)]
         ~@body))))

(deftest handler-test
  (with-handler [handler]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]]
     [[:delete "Patient" "0"]]]

    (testing "initial version"
      (let [{:keys [status headers body]}
            @(handler {:path-params {:id "0" :vid "1"}})]

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
          [:meta :lastUpdated] := #fhir/instant #system/date-time "1970-01-01T00:00:00Z")))

    (testing "deleted version"
      (let [{:keys [status headers body]}
            @(handler {:path-params {:id "0" :vid "2"}})]

        (is (= 410 status))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "Version in ETag header"
          ;; 2 is the T of the transaction of the resource deletion
          (is (= "W/\"2\"" (get headers "ETag"))))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "deleted"
          [:issue 0 :diagnostics] := #fhir/string "Resource `Patient/0` was deleted in version `2`.")))

    (testing "non existing version"
      (let [{:keys [status headers body]}
            @(handler {:path-params {:id "0" :vid "3"}})]

        (is (= 404 status))

        (testing "has no Last-Modified header"
          (is (nil? (get headers "Last-Modified"))))

        (testing "has no ETag header"
          (is (nil? (get headers "ETag"))))

        (testing "disallows caching"
          (is (= "no-cache" (get headers "Cache-Control"))))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "not-found"
          [:issue 0 :diagnostics] := #fhir/string "Resource `Patient/0` with version `3` was not found.")))

    (testing "invalid version"
      (let [{:keys [status headers body]}
            @(handler {:path-params {:id "0" :vid "a"}})]

        (is (= 404 status))

        (testing "has no Last-Modified header"
          (is (nil? (get headers "Last-Modified"))))

        (testing "has no ETag header"
          (is (nil? (get headers "ETag"))))

        (testing "disallows caching"
          (is (= "no-cache" (get headers "Cache-Control"))))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "not-found"
          [:issue 0 :diagnostics] := #fhir/string "Resource `Patient/0` with the given version was not found."))))

  (testing "with deleted history"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean false}]]
       [[:put {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean true}]]
       [[:delete-history "Patient" "0"]]]

      (testing "initial version doesn't exist anymore"
        (let [{:keys [status headers body]}
              @(handler {:path-params {:id "0" :vid "1"}})]

          (is (= 404 status))

          (testing "has no Last-Modified header"
            (is (nil? (get headers "Last-Modified"))))

          (testing "has no ETag header"
            (is (nil? (get headers "ETag"))))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "not-found"
            [:issue 0 :diagnostics] := #fhir/string "Resource `Patient/0` with version `1` was not found.")))

      (testing "current version still exists"
        (let [{:keys [status headers body]}
              @(handler {:path-params {:id "0" :vid "2"}})]

          (is (= 200 status))

          (testing "Transaction time in Last-Modified header"
            (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

          (testing "Version in ETag header"
            ;; 2 is the T of the transaction of the resource deletion
            (is (= "W/\"2\"" (get headers "ETag"))))

          (given body
            :fhir/type := :fhir/Patient
            :id := "0"
            :active := #fhir/boolean true)))

      (testing "version 3 doesn't exist"
        (let [{:keys [status headers body]}
              @(handler {:path-params {:id "0" :vid "3"}})]

          (is (= 404 status))

          (testing "has no Last-Modified header"
            (is (nil? (get headers "Last-Modified"))))

          (testing "has no ETag header"
            (is (nil? (get headers "ETag"))))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "not-found"
            [:issue 0 :diagnostics] := #fhir/string "Resource `Patient/0` with version `3` was not found."))))))
