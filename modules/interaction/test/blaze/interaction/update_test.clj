(ns blaze.interaction.update-test
  "Specifications relevant for the FHIR update interaction:

  https://www.hl7.org/fhir/http.html#update
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.executors :as ex]
    [blaze.interaction.update :refer [handler]]
    [blaze.interaction.update-spec]
    [blaze.log]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log]))


(defn fixture [f]
  (st/instrument)
  (log/set-level! :trace)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def executor (ex/single-thread-executor))


(defn- handler-with [txs]
  (fn [request]
    (with-open [node (mem-node-with txs)]
      @((handler node executor) request))))


(def ^:private router
  (reitit/router
    [["/Patient/{id}" {:name :Patient/instance}]
     ["/Patient/{id}/_history/{vid}" {:name :Patient/versioned-instance}]]
    {:syntax :bracket}))


(def ^:private operation-outcome
  "http://terminology.hl7.org/CodeSystem/operation-outcome")


(deftest handler-test
  (testing "Returns Error on type mismatch"
    (let [{:keys [status body]}
          ((handler-with [])
            {:path-params {:id "0"}
             ::reitit/match {:data {:fhir.resource/type "Patient"}}
             :body {:resourceType "Observation"}})]

      (is (= 400 status))

      (given body
        :resourceType := "OperationOutcome"
        [:issue 0 :severity] := "error"
        [:issue 0 :code] := "invariant"
        [:issue 0 :details :coding 0 :system] := operation-outcome
        [:issue 0 :details :coding 0 :code] := "MSG_RESOURCE_TYPE_MISMATCH")))

  (testing "Returns Error on missing id"
    (let [{:keys [status body]}
          ((handler-with [])
            {:path-params {:id "0"}
             ::reitit/match {:data {:fhir.resource/type "Patient"}}
             :body {:resourceType "Patient"}})]

      (is (= 400 status))

      (given body
        :resourceType := "OperationOutcome"
        [:issue 0 :severity] := "error"
        [:issue 0 :code] := "required"
        [:issue 0 :details :coding 0 :system] := operation-outcome
        [:issue 0 :details :coding 0 :code] := "MSG_RESOURCE_ID_MISSING")))

  (testing "Returns Error on invalid id"
    (let [{:keys [status body]}
          ((handler-with [])
            {:path-params {:id "0"}
             ::reitit/match {:data {:fhir.resource/type "Patient"}}
             :body {:resourceType "Patient" :id "A_B"}})]

      (is (= 400 status))

      (given body
        :resourceType := "OperationOutcome"
        [:issue 0 :severity] := "error"
        [:issue 0 :code] := "value"
        [:issue 0 :details :coding 0 :system] := operation-outcome
        [:issue 0 :details :coding 0 :code] := "MSG_ID_INVALID")))


  (testing "Returns Error on ID mismatch"
    (let [{:keys [status body]}
          ((handler-with [])
            {:path-params {:id "0"}
             ::reitit/match {:data {:fhir.resource/type "Patient"}}
             :body {:resourceType "Patient" :id "1"}})]

      (is (= 400 status))

      (given body
        :resourceType := "OperationOutcome"
        [:issue 0 :severity] := "error"
        [:issue 0 :code] := "invariant"
        [:issue 0 :details :coding 0 :system] := operation-outcome
        [:issue 0 :details :coding 0 :code] := "MSG_RESOURCE_ID_MISMATCH")))

  (testing "Returns Error on invalid resource"
    (let [{:keys [status body]}
          ((handler-with [])
            {:path-params {:id "0"}
             ::reitit/match {:data {:fhir.resource/type "Patient"}}
             :body {:resourceType "Patient" :id "0" :gender {}}})]

      (is (= 400 status))

      (given body
        :resourceType := "OperationOutcome"
        [:issue 0 :severity] := "error"
        [:issue 0 :code] := "invariant"
        [:issue 0 :diagnostics] := "Resource invalid.")))

  (testing "Returns Error on Optimistic Locking Failure"
    (let [{:keys [status body]}
          ((handler-with [[[:create {:resourceType "Patient" :id "0"}]]
                           [[:put {:resourceType "Patient" :id "0"}]]])
            {:path-params {:id "0"}
             ::reitit/match {:data {:fhir.resource/type "Patient"}}
             :headers {"if-match" "W/\"1\""}
             :body {:resourceType "Patient" :id "0"}})]

      (is (= 412 status))

      (given body
        :resourceType := "OperationOutcome"
        [:issue 0 :severity] := "error"
        [:issue 0 :code] := "conflict"
        [:issue 0 :diagnostics] := "Precondition `W/\"1\"` failed on `Patient/0`.")))

  (testing "Returns Error violated referential integrity"
    (let [{:keys [status body]}
          ((handler-with [])
            {:path-params {:id "0"}
             ::reitit/match {:data {:fhir.resource/type "Observation"}}
             :body {:resourceType "Observation" :id "0"
                    :subject {:reference "Patient/0"}}})]

      (is (= 409 status))

      (given body
        :resourceType := "OperationOutcome"
        [:issue 0 :severity] := "error"
        [:issue 0 :code] := "conflict"
        [:issue 0 :diagnostics] := "Referential integrity violated. Resource `Patient/0` doesn't exist.")))

  (testing "On newly created resource"
    (testing "with no Prefer header"
      (let [{:keys [status headers body]}
            ((handler-with [])
              {::reitit/router router
               :path-params {:id "0"}
               ::reitit/match {:data {:fhir.resource/type "Patient"}}
               :body {:resourceType "Patient" :id "0"}})]

        (testing "Returns 201"
          (is (= 201 status)))

        (testing "Location header"
          (is (= "/Patient/0/_history/1" (get headers "Location"))))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "VersionId in ETag header"
          (is (= "W/\"1\"" (get headers "ETag"))))

        (testing "Location header"
          (is (= "/Patient/0/_history/1" (get headers "Location"))))

        (testing "Contains the resource as body"
          (given body
            :resourceType := "Patient"
            :id := "0"
            [:meta :versionId] := "1")))))


  (testing "On successful update of an existing resource"
    (testing "with no Prefer header"
      (let [{:keys [status headers body]}
            ((handler-with
                [[[:create {:resourceType "Patient" :id "0"}]]])
              {:path-params {:id "0"}
               ::reitit/match {:data {:fhir.resource/type "Patient"}}
               :body {:resourceType "Patient" :id "0"}})]

        (testing "Returns 200"
          (is (= 200 status)))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "VersionId in ETag header"
          (is (= "W/\"2\"" (get headers "ETag"))))

        (testing "Contains the resource as body"
          (given body
            :resourceType := "Patient"
            :id := "0"
            [:meta :versionId] := "2"))))))
