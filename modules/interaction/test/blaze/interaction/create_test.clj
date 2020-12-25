(ns blaze.interaction.create-test
  "Specifications relevant for the FHIR create interaction:

  https://www.hl7.org/fhir/http.html#create
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.executors :as ex]
    [blaze.interaction.create]
    [blaze.interaction.create-spec]
    [blaze.luid :refer [luid]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log])
  (:import
    [java.time Instant]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (log/with-level :trace (f))
  (st/unstrument))


(test/use-fixtures :each fixture)


(def executor (ex/single-thread-executor))


(def router
  (reitit/router
    [["/Patient/{id}" {:name :Patient/instance}]
     ["/Patient/{id}/_history/{vid}" {:name :Patient/versioned-instance}]]
    {:syntax :bracket}))


(defn- handler [node]
  (-> (ig/init
        {:blaze.interaction/create
         {:node node
          :executor executor}})
      (:blaze.interaction/create)))


(defn- handler-with [txs]
  (fn [request]
    (with-open [node (mem-node-with txs)]
      @((handler node) request))))


(deftest handler-test
  (testing "Returns Error on missing body"
    (let [{:keys [status body]}
          ((handler-with [])
           {::reitit/match {:data {:fhir.resource/type "Patient"}}})]

      (is (= 400 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"invalid"
        [:issue 0 :diagnostics] := "Missing HTTP body.")))

  (testing "Returns Error on type mismatch"
    (let [{:keys [status body]}
          ((handler-with [])
           {::reitit/match {:data {:fhir.resource/type "Patient"}}
            :body {:fhir/type :fhir/Observation}})]

      (is (= 400 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"invariant"
        [:issue 0 :details :coding 0 :system] := #fhir/uri"http://terminology.hl7.org/CodeSystem/operation-outcome"
        [:issue 0 :details :coding 0 :code] := #fhir/code"MSG_RESOURCE_TYPE_MISMATCH"
        [:issue 0 :diagnostics] := "Resource type `Observation` doesn't match the endpoint type `Patient`.")))

  (testing "Returns Error violated referential integrity"
    (let [{:keys [status body]}
          ((handler-with [])
           {::reitit/match {:data {:fhir.resource/type "Observation"}}
            :body {:fhir/type :fhir/Observation :id "0"
                   :subject
                   {:fhir/type :fhir/Reference
                    :reference "Patient/0"}}})]

      (is (= 409 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"conflict"
        [:issue 0 :diagnostics] :=
        "Referential integrity violated. Resource `Patient/0` doesn't exist.")))

  (testing "On newly created resource"
    (testing "with no Prefer header"
      (with-redefs
        [luid (constantly "C5OCZL276FGI3QCL")]
        (let [{:keys [status headers body]}
              ((handler-with [])
               {::reitit/router router
                ::reitit/match {:data {:fhir.resource/type "Patient"}}
                :body {:fhir/type :fhir/Patient}})]

          (is (= 201 status))

          (testing "Location header"
            (is (= "/Patient/C5OCZL276FGI3QCL/_history/1"
                   (get headers "Location"))))

          (testing "Transaction time in Last-Modified header"
            (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

          (testing "Version in ETag header"
            ;; 1 is the T of the transaction of the resource creation
            (is (= "W/\"1\"" (get headers "ETag"))))

          (given body
            :fhir/type := :fhir/Patient
            :id := "C5OCZL276FGI3QCL"
            [:meta :versionId] := #fhir/id"1"
            [:meta :lastUpdated] := Instant/EPOCH))))

    (testing "with return=minimal Prefer header"
      (with-redefs
        [luid (constantly "C5OCZOCCRNUQOESF")]
        (let [{:keys [status headers body]}
              ((handler-with [])
               {::reitit/router router
                ::reitit/match {:data {:fhir.resource/type "Patient"}}
                :headers {"prefer" "return=minimal"}
                :body {:fhir/type :fhir/Patient}})]

          (is (= 201 status))

          (testing "Location header"
            (is (= "/Patient/C5OCZOCCRNUQOESF/_history/1"
                   (get headers "Location"))))

          (testing "Transaction time in Last-Modified header"
            (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

          (testing "Version in ETag header"
            ;; 1 is the T of the transaction of the resource creation
            (is (= "W/\"1\"" (get headers "ETag"))))

          (is (nil? body)))))

    (testing "with return=representation Prefer header"
      (with-redefs
        [luid (constantly "C5OCZPIRR7XVOD2A")]
        (let [{:keys [status headers body]}
              ((handler-with [])
               {::reitit/router router
                ::reitit/match {:data {:fhir.resource/type "Patient"}}
                :headers {"prefer" "return=representation"}
                :body {:fhir/type :fhir/Patient}})]

          (is (= 201 status))

          (testing "Location header"
            (is (= "/Patient/C5OCZPIRR7XVOD2A/_history/1"
                   (get headers "Location"))))

          (testing "Transaction time in Last-Modified header"
            (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

          (testing "Version in ETag header"
            ;; 1 is the T of the transaction of the resource creation
            (is (= "W/\"1\"" (get headers "ETag"))))

          (given body
            :fhir/type := :fhir/Patient
            :id := "C5OCZPIRR7XVOD2A"
            [:meta :versionId] := #fhir/id"1"
            [:meta :lastUpdated] := Instant/EPOCH))))

    (testing "with return=OperationOutcome Prefer header"
      (with-redefs
        [luid (constantly "C5OCZQ32A6MGMNR6")]
        (let [{:keys [status headers body]}
              ((handler-with [])
               {::reitit/router router
                ::reitit/match {:data {:fhir.resource/type "Patient"}}
                :headers {"prefer" "return=OperationOutcome"}
                :body {:fhir/type :fhir/Patient}})]

          (is (= 201 status))

          (testing "Location header"
            (is (= "/Patient/C5OCZQ32A6MGMNR6/_history/1"
                   (get headers "Location"))))

          (testing "Transaction time in Last-Modified header"
            (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

          (testing "Version in ETag header"
            ;; 1 is the T of the transaction of the resource creation
            (is (= "W/\"1\"" (get headers "ETag"))))

          (is (= :fhir/OperationOutcome (:fhir/type body))))))))
