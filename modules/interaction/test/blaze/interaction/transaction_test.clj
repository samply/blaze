(ns blaze.interaction.transaction-test
  "Specifications relevant for the FHIR batch/transaction interaction:

  https://www.hl7.org/fhir/http.html#transaction
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.executors :as ex]
    [blaze.interaction.transaction :refer [handler]]
    [blaze.interaction.transaction-spec]
    [blaze.log]
    [blaze.luid :as luid]
    [blaze.uuid :refer [random-uuid]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [ring.util.response :as ring]
    [taoensso.timbre :as log])
  (:import
    [java.time Instant]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (log/set-level! :trace)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def ^:private router
  (reitit/router
    [["/Patient/{id}" {:name :Patient/instance}]
     ["/Patient/{id}/_history/{vid}" {:name :Patient/versioned-instance}]]
    {:syntax :bracket}))


(def ^:private operation-outcome
  #fhir/uri"http://terminology.hl7.org/CodeSystem/operation-outcome")


(defonce executor (ex/single-thread-executor))


(defn- handler-with [txs]
  (fn [request]
    (with-open [node (mem-node-with txs)]
      @((handler node executor) request))))


(deftest handler-test
  (testing "Returns Error on missing body"
    (let [{:keys [status body]}
          ((handler-with [])
           {})]

      (is (= 400 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"invalid"
        [:issue 0 :diagnostics] := "Missing Bundle.")))

  (testing "Returns Error on from resource type."
    (let [{:keys [status body]}
          ((handler-with [])
           {:body
            {:fhir/type :fhir/Patient}})]

      (is (= 400 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"value"
        [:issue 0 :diagnostics] := "Expected a Bundle resource but got a Patient resource.")))

  (testing "Returns Error on missing request"
    (let [{:keys [status body]}
          ((handler-with [])
           {:body
            {:fhir/type :fhir/Bundle
             :type #fhir/code"transaction"
             :entry
             [{}]}})]

      (is (= 400 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"value"
        [:issue 0 :expression 0] := "Bundle.entry[0]"
        [:issue 0 :diagnostics] := "Missing request.")))

  (testing "Returns Error on missing request url"
    (let [{:keys [status body]}
          ((handler-with [])
           {:body
            {:fhir/type :fhir/Bundle
             :type #fhir/code"transaction"
             :entry
             [{:fhir/type :fhir.Bundle/entry
               :request {}}]}})]

      (is (= 400 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"value"
        [:issue 0 :expression 0] := "Bundle.entry[0].request"
        [:issue 0 :diagnostics] := "Missing url.")))

  (testing "Returns Error on missing request method"
    (let [{:keys [status body]}
          ((handler-with [])
           {:body
            {:fhir/type :fhir/Bundle
             :type #fhir/code"transaction"
             :entry
             [{:fhir/type :fhir.Bundle/entry
               :request
               {:fhir/type :fhir.Bundle.entry/request
                :url #fhir/uri"Patient/0"}}]}})]

      (is (= 400 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"value"
        [:issue 0 :expression 0] := "Bundle.entry[0].request"
        [:issue 0 :diagnostics] := "Missing method.")))

  (testing "Returns Error on unknown method"
    (let [{:keys [status body]}
          ((handler-with [])
           {:body
            {:fhir/type :fhir/Bundle
             :type #fhir/code"transaction"
             :entry
             [{:fhir/type :fhir.Bundle/entry
               :request
               {:fhir/type :fhir.Bundle.entry/request
                :method #fhir/code"FOO"
                :url #fhir/uri"Patient/0"}}]}})]

      (is (= 400 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"value"
        [:issue 0 :expression 0] := "Bundle.entry[0].request.method"
        [:issue 0 :diagnostics] := "Unknown method `FOO`.")))

  (testing "Returns Error on unsupported method"
    (let [{:keys [status body]}
          ((handler-with [])
           {:body
            {:fhir/type :fhir/Bundle
             :type #fhir/code"transaction"
             :entry
             [{:fhir/type :fhir.Bundle/entry
               :request
               {:fhir/type :fhir.Bundle.entry/request
                :method #fhir/code"PATCH"
                :url #fhir/uri"Patient/0"}}]}})]

      (is (= 422 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"not-supported"
        [:issue 0 :expression 0] := "Bundle.entry[0].request.method"
        [:issue 0 :diagnostics] := "Unsupported method `PATCH`.")))

  (testing "Returns Error on missing type"
    (let [{:keys [status body]}
          ((handler-with [])
           {:body
            {:fhir/type :fhir/Bundle
             :type #fhir/code"transaction"
             :entry
             [{:fhir/type :fhir.Bundle/entry
               :request
               {:fhir/type :fhir.Bundle.entry/request
                :method #fhir/code"PUT"
                :url #fhir/uri""}}]}})]

      (is (= 400 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"value"
        [:issue 0 :expression 0] := "Bundle.entry[0].request.url"
        [:issue 0 :diagnostics] := "Can't parse type from `entry.request.url` ``.")))

  (testing "Returns Error on unknown type"
    (let [{:keys [status body]}
          ((handler-with [])
           {:body
            {:fhir/type :fhir/Bundle
             :type #fhir/code"transaction"
             :entry
             [{:fhir/type :fhir.Bundle/entry
               :request
               {:fhir/type :fhir.Bundle.entry/request
                :method #fhir/code"PUT"
                :url #fhir/uri"Foo/0"}}]}})]

      (is (= 400 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"value"
        [:issue 0 :expression 0] := "Bundle.entry[0].request.url"
        [:issue 0 :diagnostics] := "Unknown type `Foo` in bundle entry URL `Foo/0`.")))


  (testing "Returns Error on type mismatch of a update"
    (let [{:keys [status body]}
          ((handler-with [])
           {:body
            {:fhir/type :fhir/Bundle
             :type #fhir/code"transaction"
             :entry
             [{:fhir/type :fhir.Bundle/entry
               :resource
               {:fhir/type :fhir/Observation}
               :request
               {:fhir/type :fhir.Bundle.entry/request
                :method #fhir/code"PUT"
                :url #fhir/uri"Patient/0"}}]}})]

      (is (= 400 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"invariant"
        [:issue 0 :details :coding 0 :system] := operation-outcome
        [:issue 0 :details :coding 0 :code] := #fhir/code"MSG_RESOURCE_TYPE_MISMATCH"
        [:issue 0 :expression 0] := "Bundle.entry[0].request.url"
        [:issue 0 :expression 1] := "Bundle.entry[0].resource.resourceType")))


  (testing "Returns Error on missing ID of a update"
    (let [{:keys [status body]}
          ((handler-with [])
           {:body
            {:fhir/type :fhir/Bundle
             :type #fhir/code"transaction"
             :entry
             [{:fhir/type :fhir.Bundle/entry
               :resource
               {:fhir/type :fhir/Patient}
               :request
               {:fhir/type :fhir.Bundle.entry/request
                :method #fhir/code"PUT"
                :url #fhir/uri"Patient/0"}}]}})]

      (is (= 400 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"required"
        [:issue 0 :details :coding 0 :system] := operation-outcome
        [:issue 0 :details :coding 0 :code] := #fhir/code"MSG_RESOURCE_ID_MISSING"
        [:issue 0 :expression 0] := "Bundle.entry[0].resource.id")))


  (testing "Returns Error on invalid ID of a update"
    (let [{:keys [status body]}
          ((handler-with [])
           {:body
            {:fhir/type :fhir/Bundle
             :type #fhir/code"transaction"
             :entry
             [{:fhir/type :fhir.Bundle/entry
               :resource
               {:fhir/type :fhir/Patient
                :id "A_B"}
               :request
               {:fhir/type :fhir.Bundle.entry/request
                :method #fhir/code"PUT"
                :url #fhir/uri"Patient/0"}}]}})]

      (is (= 400 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"value"
        [:issue 0 :details :coding 0 :system] := operation-outcome
        [:issue 0 :details :coding 0 :code] := #fhir/code"MSG_ID_INVALID"
        [:issue 0 :expression 0] := "Bundle.entry[0].resource.id")))


  (testing "Returns Error on ID mismatch of a update"
    (let [{:keys [status body]}
          ((handler-with [])
           {:body
            {:fhir/type :fhir/Bundle
             :type #fhir/code"transaction"
             :entry
             [{:fhir/type :fhir.Bundle/entry
               :resource
               {:fhir/type :fhir/Patient
                :id "1"}
               :request
               {:fhir/type :fhir.Bundle.entry/request
                :method #fhir/code"PUT"
                :url #fhir/uri"Patient/0"}}]}})]

      (is (= 400 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"invariant"
        [:issue 0 :details :coding 0 :system] := operation-outcome
        [:issue 0 :details :coding 0 :code] := #fhir/code"MSG_RESOURCE_ID_MISMATCH"
        [:issue 0 :expression 0] := "Bundle.entry[0].request.url"
        [:issue 0 :expression 1] := "Bundle.entry[0].resource.id")))

  (testing "Returns Error on Optimistic Locking Failure of a update"
    (let [{:keys [status body]}
          ((handler-with [[[:create {:fhir/type :fhir/Patient :id "0"}]]
                          [[:put {:fhir/type :fhir/Patient :id "0"}]]])
           {:body
            {:fhir/type :fhir/Bundle
             :type #fhir/code"transaction"
             :entry
             [{:fhir/type :fhir.Bundle/entry
               :resource
               {:fhir/type :fhir/Patient
                :id "0"}
               :request
               {:fhir/type :fhir.Bundle.entry/request
                :method #fhir/code"PUT"
                :url #fhir/uri"Patient/0"
                :ifMatch "W/\"1\""}}]}})]

      (is (= 412 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"conflict"
        [:issue 0 :diagnostics] := "Precondition `W/\"1\"` failed on `Patient/0`.")))


  (testing "Returns Error on duplicate resources"
    (let [{:keys [status body]}
          ((handler-with [])
           {:body
            {:fhir/type :fhir/Bundle
             :type #fhir/code"transaction"
             :entry
             [{:fhir/type :fhir.Bundle/entry
               :resource
               {:fhir/type :fhir/Patient
                :id "0"}
               :request
               {:fhir/type :fhir.Bundle.entry/request
                :method #fhir/code"PUT"
                :url #fhir/uri"Patient/0"}}
              {:fhir/type :fhir.Bundle/entry
               :resource
               {:fhir/type :fhir/Patient
                :id "0"}
               :request
               {:fhir/type :fhir.Bundle.entry/request
                :method #fhir/code"PUT"
                :url #fhir/uri"Patient/0"}}]}})]

      (is (= 400 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"invariant"
        [:issue 0 :diagnostics] := "Duplicate resource `Patient/0`.")))


  (testing "Returns Error violated referential integrity"
    (let [{:keys [status body]}
          ((handler-with [])
           {:body
            {:fhir/type :fhir/Bundle
             :type #fhir/code"transaction"
             :entry
             [{:fhir/type :fhir.Bundle/entry
               :resource
               {:fhir/type :fhir/Observation :id "0"
                :subject
                {:fhir/type :fhir/Reference
                 :reference "Patient/0"}}
               :request
               {:fhir/type :fhir.Bundle.entry/request
                :method #fhir/code"POST"
                :url #fhir/uri"Observation"}}]}})]

      (is (= 409 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"conflict"
        [:issue 0 :diagnostics] := "Referential integrity violated. Resource `Patient/0` doesn't exist.")))


  (testing "On newly created resource of a update in transaction"
    (let [entries
          [{:fhir/type :fhir.Bundle/entry
            :resource
            {:fhir/type :fhir/Patient
             :id "0"}
            :request
            {:fhir/type :fhir.Bundle.entry/request
             :method #fhir/code"PUT"
             :url #fhir/uri"Patient/0"}}]

          {:keys [status body]}
          ((handler-with [])
           {::reitit/router router
            ::reitit/match {:data {:blaze/context-path ""}}
            :body
            {:fhir/type :fhir/Bundle
             :type #fhir/code"transaction"
             :entry entries}})]

      (is (= 200 status))

      (given body
        :fhir/type := :fhir/Bundle
        :id :? string?
        :type := #fhir/code"transaction-response"
        [:entry 0 :response :status] := "201"
        [:entry 0 :response :location] := #fhir/uri"/Patient/0/_history/1"
        [:entry 0 :response :etag] := "W/\"1\""
        [:entry 0 :response :lastModified] := Instant/EPOCH)))


  (testing "On updated resource in transaction"
    (let [entries
          [{:resource
            {:fhir/type :fhir/Patient
             :id "0"
             :gender #fhir/code"male"}
            :request
            {:fhir/type :fhir.Bundle.entry/request
             :method #fhir/code"PUT"
             :url #fhir/uri"Patient/0"}}]]

      (testing "with no Prefer header"
        (let [{:keys [status body]}
              ((handler-with
                 [[[:put {:fhir/type :fhir/Patient :id "0"
                          :gender #fhir/code"female"}]]])
               {:body
                {:fhir/type :fhir/Bundle
                 :type #fhir/code"transaction"
                 :entry entries}})]

          (is (= 200 status))

          (given body
            :fhir/type := :fhir/Bundle
            :id :? string?
            :type := #fhir/code"transaction-response"
            [:entry 0 :response :status] := "200"
            [:entry 0 :response :etag] := "W/\"2\""
            [:entry 0 :response :lastModified] := Instant/EPOCH)

          (testing "there is no resource embedded in the entry"
            (is (nil? (-> body :entry first :resource))))))


      (testing "with return=representation Prefer header"
        (let [{:keys [status body]}
              ((handler-with
                 [[[:put {:fhir/type :fhir/Patient :id "0"
                          :gender #fhir/code"female"}]]])
               {:headers {"prefer" "return=representation"}
                :body
                {:fhir/type :fhir/Bundle
                 :type #fhir/code"transaction"
                 :entry entries}})]

          (is (= 200 status))

          (given body
            :fhir/type := :fhir/Bundle
            :id :? string?
            :type := #fhir/code"transaction-response"
            [:entry 0 :response :status] := "200"
            [:entry 0 :response :etag] := "W/\"2\""
            [:entry 0 :response :lastModified] := Instant/EPOCH)

          (given (-> body :entry first :resource)
            :fhir/type := :fhir/Patient
            :id := "0"
            :gender := #fhir/code"male"
            [:meta :versionId] := #fhir/id"2"
            [:meta :lastUpdated] := Instant/EPOCH)))))


  (testing "On created resource in transaction"
    (let [entries
          [{:resource
            {:fhir/type :fhir/Patient}
            :request
            {:method #fhir/code"POST"
             :url #fhir/uri"Patient"}}]]

      (testing "with no Prefer header"
        (with-redefs
          [luid/init (constantly [0 0])
           random-uuid (constantly "b11daf6d-4c7b-4f81-980e-8c599bb6bf2d")]
          (let [{:keys [status body]}
                ((handler-with [])
                 {::reitit/router router
                  ::reitit/match {:data {:blaze/context-path ""}}
                  :body
                  {:fhir/type :fhir/Bundle
                   :type #fhir/code"transaction"
                   :entry entries}})]

            (is (= 200 status))

            (given body
              :fhir/type := :fhir/Bundle
              :id := "b11daf6d-4c7b-4f81-980e-8c599bb6bf2d"
              :type := #fhir/code"transaction-response"
              [:entry 0 :response :status] := "201"
              [:entry 0 :response :location] := #fhir/uri"/Patient/AAAAAAAAAAAAAAAB/_history/1"
              [:entry 0 :response :etag] := "W/\"1\""
              [:entry 0 :response :lastModified] := Instant/EPOCH)

            (testing "there is no resource embedded in the entry"
              (is (nil? (-> body :entry first :resource)))))))))

  (testing "creates sequential identifiers"
    (let [entries
          [{:resource
            {:fhir/type :fhir/Patient}
            :request
            {:method #fhir/code"POST"
             :url #fhir/uri"Patient"}}
           {:resource
            {:fhir/type :fhir/Patient}
            :request
            {:method #fhir/code"POST"
             :url #fhir/uri"Patient"}}]]

      (with-redefs
        [luid/init (constantly [0 0])]
        (let [{:keys [body]}
              ((handler-with [])
               {::reitit/router router
                ::reitit/match {:data {:blaze/context-path ""}}
                :headers {"prefer" "return=representation"}
                :body
                {:fhir/type :fhir/Bundle
                 :type #fhir/code"transaction"
                 :entry entries}})]

          (given body
            [:entry 0 :resource :id] := "AAAAAAAAAAAAAAAB"
            [:entry 1 :resource :id] := "AAAAAAAAAAAAAAAC"))))))


(defn- stub-match-by-path [router path match]
  (st/instrument
    [`reitit/match-by-path]
    {:spec
     {`reitit/match-by-path
      (s/fspec
        :args (s/cat :router #{router} :path #{path})
        :ret #{match})}
     :stub
     #{`reitit/match-by-path}}))


(deftest handler-batch-create-test
  (testing "Successful"
    (let [handler
          (fn [{:keys [body]}]
            (is (= {:fhir/type :fhir/Patient} body))
            (ac/completed-future
              (-> (ring/created "location" ::response-body)
                  (ring/header "Last-Modified" "Mon, 24 Jun 2019 09:54:26 GMT")
                  (ring/header "ETag" "etag"))))]
      (stub-match-by-path
        ::router "/Patient" {:result {:post {:handler handler}}}))

    (let [{:keys [status body]}
          ((handler-with [])
           {::reitit/router ::router
            ::reitit/match {:data {:blaze/context-path ""}}
            :body
            {:fhir/type :fhir/Bundle
             :type #fhir/code"batch"
             :entry
             [{:resource
               {:fhir/type :fhir/Patient}
               :request
               {:method #fhir/code"POST"
                :url #fhir/uri"Patient"}}]}})]

      (is (= 200 status))

      (given body
        :fhir/type := :fhir/Bundle
        :id :? string?
        :type := #fhir/code"batch-response"
        [:entry 0 :response :status] := "201"
        [:entry 0 :response :location] := #fhir/uri"location"
        [:entry 0 :response :etag] := "etag"
        [:entry 0 :response :lastModified] := (Instant/parse "2019-06-24T09:54:26Z"))

      (is (= ::response-body (-> body :entry first :resource)))))

  (testing "Failing"
    (let [handler
          (fn [_]
            (ac/completed-future
              (ring/bad-request ::operation-outcome)))]
      (stub-match-by-path
        ::router "/Patient" {:result {:post {:handler handler}}}))

    (let [{:keys [status body]}
          ((handler-with [])
           {::reitit/router ::router
            ::reitit/match {:data {:blaze/context-path ""}}
            :body
            {:fhir/type :fhir/Bundle
             :type #fhir/code"batch"
             :entry
             [{:resource
               {:fhir/type :fhir/Patient}
               :request
               {:method #fhir/code"POST"
                :url #fhir/uri"Patient"}}]}})]

      (is (= 200 status))

      (given body
        :fhir/type := :fhir/Bundle
        :id :? string?
        :type := #fhir/code"batch-response"
        [:entry 0 :response :status] := "400"
        [:entry 0 :response :outcome] := ::operation-outcome))))


(deftest handler-batch-read-test
  (testing "Successful"
    (let [handler
          (fn [_]
            (ac/completed-future
              (-> (ring/response ::response-body)
                  (ring/header "Last-Modified" "Mon, 24 Jun 2019 09:54:26 GMT")
                  (ring/header "ETag" "etag"))))]
      (stub-match-by-path
        ::router "/Patient/0" {:result {:get {:handler handler}}}))

    (let [{:keys [status body]}
          ((handler-with [])
           {::reitit/router ::router
            ::reitit/match {:data {:blaze/context-path ""}}
            :body
            {:fhir/type :fhir/Bundle
             :type #fhir/code"batch"
             :entry
             [{:request
               {:method #fhir/code"GET"
                :url #fhir/uri"Patient/0"}}]}})]

      (is (= 200 status))

      (given body
        :fhir/type := :fhir/Bundle
        :id :? string?
        :type := #fhir/code"batch-response"
        [:entry 0 :response :status] := "200"
        [:entry 0 :response :etag] := "etag"
        [:entry 0 :response :lastModified] := (Instant/parse "2019-06-24T09:54:26Z"))

      (is (= ::response-body (-> body :entry first :resource)))))

  (testing "Failing"
    (let [handler
          (fn [_]
            (ac/completed-future
              (ring/bad-request ::operation-outcome)))]
      (stub-match-by-path
        ::router "/Patient/0" {:result {:get {:handler handler}}}))

    (let [{:keys [status body]}
          ((handler-with [])
           {::reitit/router ::router
            ::reitit/match {:data {:blaze/context-path ""}}
            :body
            {:fhir/type :fhir/Bundle
             :type #fhir/code"batch"
             :entry
             [{:request
               {:method #fhir/code"GET"
                :url #fhir/uri"Patient/0"}}]}})]

      (is (= 200 status))

      (given body
        :fhir/type := :fhir/Bundle
        :id :? string?
        :type := #fhir/code"batch-response"
        [:entry 0 :response :status] := "400"
        [:entry 0 :response :outcome] := ::operation-outcome))))


(deftest handler-batch-search-type-test
  (testing "Successful"
    (let [handler
          (fn [_]
            (ac/completed-future
              (ring/response ::response-body)))]
      (stub-match-by-path
        ::router "/Patient" {:result {:get {:handler handler}}}))

    (let [{:keys [status body]}
          ((handler-with [])
           {::reitit/router ::router
            ::reitit/match {:data {:blaze/context-path ""}}
            :body
            {:fhir/type :fhir/Bundle
             :type #fhir/code"batch"
             :entry
             [{:request
               {:method #fhir/code"GET"
                :url #fhir/uri"Patient"}}]}})]

      (is (= 200 status))

      (given body
        :fhir/type := :fhir/Bundle
        :id :? string?
        :type := #fhir/code"batch-response"
        [:entry 0 :response :status] := "200")

      (is (= ::response-body (-> body :entry first :resource))))))


(deftest handler-batch-update-test
  (testing "Successful"
    (let [handler
          (fn [{:keys [body]}]
            (is (= {:fhir/type :fhir/Patient} body))
            (ac/completed-future
              (-> (ring/response ::response-body)
                  (ring/header "Last-Modified" "Mon, 24 Jun 2019 09:54:26 GMT")
                  (ring/header "ETag" "etag"))))]
      (stub-match-by-path
        ::router "/Patient/0" {:result {:put {:handler handler}}}))

    (let [{:keys [status body]}
          ((handler-with [])
           {::reitit/router ::router
            ::reitit/match {:data {:blaze/context-path ""}}
            :body
            {:fhir/type :fhir/Bundle
             :type #fhir/code"batch"
             :entry
             [{:resource
               {:fhir/type :fhir/Patient}
               :request
               {:method #fhir/code"PUT"
                :url #fhir/uri"Patient/0"}}]}})]

      (is (= 200 status))

      (given body
        :fhir/type := :fhir/Bundle
        :id :? string?
        :type := #fhir/code"batch-response"
        [:entry 0 :response :status] := "200"
        [:entry 0 :response :etag] := "etag"
        [:entry 0 :response :lastModified] := (Instant/parse "2019-06-24T09:54:26Z"))

      (is (= ::response-body (-> body :entry first :resource))))))
