(ns blaze.interaction.transaction-test
  "Specifications relevant for the FHIR batch/transaction interaction:

  https://www.hl7.org/fhir/http.html#transaction
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.executors :as ex]
    [blaze.fhir.spec.type :as type]
    [blaze.handler.util :as handler-util]
    [blaze.interaction.create :as create]
    [blaze.interaction.delete :as delete]
    [blaze.interaction.read :as read]
    [blaze.interaction.search-type :as search-type]
    [blaze.interaction.transaction]
    [blaze.interaction.transaction-spec]
    [blaze.interaction.update :as update]
    [blaze.log]
    [blaze.luid :as luid]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [reitit.ring]
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


(def ^:private operation-outcome
  #fhir/uri"http://terminology.hl7.org/CodeSystem/operation-outcome")


(defonce executor (ex/single-thread-executor))


(defn router [node]
  (reitit.ring/router
    [["/Observation"
      {:name :Observation/type
       :fhir.resource/type "Observation"
       :post (create/handler node executor)}]
     ["/Patient"
      {:name :Patient/type
       :fhir.resource/type "Patient"
       :get (search-type/handler node)
       :post (create/handler node executor)}]
     ["/Patient/{id}"
      {:name :Patient/instance
       :fhir.resource/type "Patient"
       :get (read/handler node)
       :delete (delete/handler node executor)
       :put (update/handler node executor)}]
     ["/Patient/{id}/_history/{vid}"
      {:name :Patient/versioned-instance}]]
    {:syntax :bracket}))


(defn batch-handler [router]
  (reitit.ring/ring-handler router handler-util/default-handler))


(defn- handler [node]
  (-> (ig/init
        {:blaze.interaction/transaction
         {:node node
          :executor executor}})
      (:blaze.interaction/transaction)))


(defn- handler-with [txs]
  (fn [request]
    (with-open [node (mem-node-with txs)]
      (let [router (router node)]
        @((handler node)
          (assoc request
            ::reitit/router router
            :batch-handler (batch-handler router)))))))


(deftest handler-test
  (testing "Returns error on missing body"
    (let [{:keys [status body]}
          ((handler-with [])
           {})]

      (is (= 400 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"invalid"
        [:issue 0 :diagnostics] := "Missing Bundle.")))

  (testing "Returns error on wrong resource type."
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

  (testing "Returns error on wrong Bundle type."
    (let [{:keys [status body]}
          ((handler-with [])
           {:body
            {:fhir/type :fhir/Bundle
             :type #fhir/code"foo"}})]

      (is (= 400 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"value"
        [:issue 0 :diagnostics] := "Expected a Bundle type of batch or transaction but was `foo`.")))

  (doseq [type ["transaction" "batch"]]
    (testing (format "On %s bundle" type)
      (testing "empty bundle"
        (let [{:keys [status body]}
              ((handler-with [])
               {:body
                {:fhir/type :fhir/Bundle
                 :type (type/->Code type)}})]

          (is (= 200 status))

          (testing "bundle"
            (given body
              :fhir/type := :fhir/Bundle
              :id :? string?
              :type := (type/->Code (str type "-response"))
              :entry :? empty?))))

      (testing "and update interaction"
        (testing "and newly created resource"
          (let [entries
                [{:fhir/type :fhir.Bundle/entry
                  :resource
                  {:fhir/type :fhir/Patient
                   :id "0"}
                  :request
                  {:fhir/type :fhir.Bundle.entry/request
                   :method #fhir/code"PUT"
                   :url #fhir/uri"Patient/0"}}]]

            (testing "without return preference"
              (let [{:keys [status body]
                     {[{:keys [resource response]}] :entry} :body}
                    ((handler-with [])
                     {:body
                      {:fhir/type :fhir/Bundle
                       :type (type/->Code type)
                       :entry entries}})]

                (is (= 200 status))

                (testing "bundle"
                  (given body
                    :fhir/type := :fhir/Bundle
                    :id :? string?
                    :type := (type/->Code (str type "-response"))))

                (testing "entry resource"
                  (is (nil? resource)))

                (testing "entry response"
                  (given response
                    :status := "201"
                    :location := #fhir/uri"/Patient/0/_history/1"
                    :etag := "W/\"1\""
                    :lastModified := Instant/EPOCH))))

            (testing "with representation return preference"
              (let [{:keys [status body]
                     {[{:keys [resource response]}] :entry} :body}
                    ((handler-with [])
                     {:headers {"prefer" "return=representation"}
                      :body
                      {:fhir/type :fhir/Bundle
                       :type (type/->Code type)
                       :entry entries}})]

                (is (= 200 status))

                (testing "bundle"
                  (given body
                    :fhir/type := :fhir/Bundle
                    :id :? string?
                    :type := (type/->Code (str type "-response"))))

                (testing "entry resource"
                  (given resource
                    :fhir/type := :fhir/Patient
                    :id := "0"
                    [:meta :versionId] := #fhir/id"1"
                    [:meta :lastUpdated] := Instant/EPOCH))

                (testing "entry response"
                  (given response
                    :status := "201"
                    :location := #fhir/uri"/Patient/0/_history/1"
                    :etag := "W/\"1\""
                    :lastModified := Instant/EPOCH))))))

        (testing "and updated resource"
          (let [entries
                [{:resource
                  {:fhir/type :fhir/Patient
                   :id "0"
                   :gender #fhir/code"male"}
                  :request
                  {:fhir/type :fhir.Bundle.entry/request
                   :method #fhir/code"PUT"
                   :url #fhir/uri"Patient/0"}}]]

            (testing "without return preference"
              (let [{:keys [status body]
                     {[{:keys [resource response]}] :entry} :body}
                    ((handler-with
                       [[[:put {:fhir/type :fhir/Patient :id "0"
                                :gender #fhir/code"female"}]]])
                     {:body
                      {:fhir/type :fhir/Bundle
                       :type (type/->Code type)
                       :entry entries}})]

                (is (= 200 status))

                (testing "bundle"
                  (given body
                    :fhir/type := :fhir/Bundle
                    :id :? string?
                    :type := (type/->Code (str type "-response"))))

                (testing "entry resource"
                  (is (nil? resource)))

                (testing "entry response"
                  (given response
                    :status := "200"
                    :etag := "W/\"2\""
                    :lastModified := Instant/EPOCH))))

            (testing "with representation return preference"
              (let [{:keys [status body]
                     {[{:keys [resource response]}] :entry} :body}
                    ((handler-with
                       [[[:put {:fhir/type :fhir/Patient :id "0"
                                :gender #fhir/code"female"}]]])
                     {:headers {"prefer" "return=representation"}
                      :body
                      {:fhir/type :fhir/Bundle
                       :type (type/->Code type)
                       :entry entries}})]

                (is (= 200 status))

                (testing "bundle"
                  (given body
                    :fhir/type := :fhir/Bundle
                    :id :? string?
                    :type := (type/->Code (str type "-response"))))

                (testing "entry resource"
                  (given resource
                    :fhir/type := :fhir/Patient
                    :id := "0"
                    :gender := #fhir/code"male"
                    [:meta :versionId] := #fhir/id"2"
                    [:meta :lastUpdated] := Instant/EPOCH))

                (testing "entry response"
                  (given response
                    :status := "200"
                    :etag := "W/\"2\""
                    :lastModified := Instant/EPOCH)))))))

      (testing "and create interaction"
        (let [entries
              [{:fhir/type :fhir.Bundle/entry
                :resource
                {:fhir/type :fhir/Patient}
                :request
                {:fhir/type :fhir.Bundle.entry/request
                 :method #fhir/code"POST"
                 :url #fhir/uri"Patient"}}]]
          (with-redefs
            [luid/init (constantly [100606 100608])
             luid/luid (constantly "AAAAAGEP4AAADCIB")]

            (testing "without return preference"
              (let [{:keys [status body]
                     {[{:keys [resource response]}] :entry} :body}
                    ((handler-with [])
                     {:body
                      {:fhir/type :fhir/Bundle
                       :type (type/->Code type)
                       :entry entries}})]

                (is (= 200 status))

                (testing "bundle"
                  (given body
                    :fhir/type := :fhir/Bundle
                    :id :? string?
                    :type := (type/->Code (str type "-response"))))

                (testing "entry resource"
                  (is (nil? resource)))

                (testing "entry response"
                  (given response
                    :status := "201"
                    :location := #fhir/uri"/Patient/AAAAAGEP4AAADCIB/_history/1"
                    :etag := "W/\"1\""
                    :lastModified := Instant/EPOCH))))

            (testing "with representation return preference"
              (let [{:keys [status body]
                     {[{:keys [resource response]}] :entry} :body}
                    ((handler-with [])
                     {:headers {"prefer" "return=representation"}
                      :body
                      {:fhir/type :fhir/Bundle
                       :type (type/->Code type)
                       :entry entries}})]

                (is (= 200 status))

                (testing "bundle"
                  (given body
                    :fhir/type := :fhir/Bundle
                    :id :? string?
                    :type := (type/->Code (str type "-response"))))

                (testing "entry resource"
                  (given resource
                    :fhir/type := :fhir/Patient
                    :id := "AAAAAGEP4AAADCIB"
                    [:meta :versionId] := #fhir/id"1"
                    [:meta :lastUpdated] := Instant/EPOCH))

                (testing "entry response"
                  (given response
                    :status := "201"
                    :location := #fhir/uri"/Patient/AAAAAGEP4AAADCIB/_history/1"
                    :etag := "W/\"1\""
                    :lastModified := Instant/EPOCH)))))))

      (testing "and delete interaction"
        (let [entries
              [{:fhir/type :fhir.Bundle/entry
                :request
                {:fhir/type :fhir.Bundle.entry/request
                 :method #fhir/code"DELETE"
                 :url #fhir/uri"Patient/0"}}]]

          (testing "without return preference"
            (let [{:keys [status body]
                   {[{:keys [resource response]}] :entry} :body}
                  ((handler-with
                     [[[:put {:fhir/type :fhir/Patient :id "0"}]]])
                   {:body
                    {:fhir/type :fhir/Bundle
                     :type (type/->Code type)
                     :entry entries}})]

              (is (= 200 status))

              (testing "bundle"
                (given body
                  :fhir/type := :fhir/Bundle
                  :id :? string?
                  :type := (type/->Code (str type "-response"))))

              (testing "entry resource"
                (is (nil? resource)))

              (testing "entry response"
                (given response
                  :status := "204"
                  :etag := "W/\"2\""
                  :lastModified := Instant/EPOCH))))))))

  (testing "On transaction bundle"
    (testing "returns error on missing request"
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

    (testing "returns error on missing request url"
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

    (testing "returns error on missing request method"
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

    (testing "returns error on unknown method"
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
          [:issue 0 :diagnostics] := "Unknown method `FOO`."))))

  (testing "returns error on unsupported method"
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

  (testing "and update interaction"
    (testing "returns error on missing type in URL"
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

    (testing "returns error on unknown type"
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

    (testing "returns error on type mismatch"
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

    (testing "returns error on missing ID"
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

    (testing "returns error on missing ID in URL"
      (let [{:keys [status body]}
            ((handler-with [])
             {:body
              {:fhir/type :fhir/Bundle
               :type #fhir/code"transaction"
               :entry
               [{:fhir/type :fhir.Bundle/entry
                 :resource
                 {:fhir/type :fhir/Patient :id "0"}
                 :request
                 {:fhir/type :fhir.Bundle.entry/request
                  :method #fhir/code"PUT"
                  :url #fhir/uri"Patient"}}]}})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code"error"
          [:issue 0 :code] := #fhir/code"value"
          [:issue 0 :expression 0] := "Bundle.entry[0].request.url"
          [:issue 0 :diagnostics] := "Can't parse id from URL `Patient`.")))

    (testing "returns error on invalid ID"
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

    (testing "returns error on ID mismatch"
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

    (testing "returns error on optimistic locking failure"
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

    (testing "Returns error on duplicate resources"
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

    (testing "Returns error violated referential integrity"
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
          [:issue 0 :diagnostics] := "Referential integrity violated. Resource `Patient/0` doesn't exist."))))

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
               {:headers {"prefer" "return=representation"}
                :body
                {:fhir/type :fhir/Bundle
                 :type #fhir/code"transaction"
                 :entry entries}})]

          (given body
            [:entry 0 :resource :id] := "AAAAAAAAAAAAAAAB"
            [:entry 1 :resource :id] := "AAAAAAAAAAAAAAAC")))))

  (testing "On batch bundle"
    (testing "returns error on missing request"
      (let [{:keys [status] {[{:keys [response]}] :entry} :body}
            ((handler-with [])
             {:body
              {:fhir/type :fhir/Bundle
               :type #fhir/code"batch"
               :entry
               [{}]}})]

        (is (= 200 status))

        (testing "entry response"
          (testing "status"
            (is (= "400" (:status response))))

          (testing "outcome"
            (given (:outcome response)
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"value"
              [:issue 0 :expression 0] := "Bundle.entry[0]"
              [:issue 0 :diagnostics] := "Missing request.")))))

    (testing "returns error on missing request url"
      (let [{:keys [status] {[{:keys [response]}] :entry} :body}
            ((handler-with [])
             {:body
              {:fhir/type :fhir/Bundle
               :type #fhir/code"batch"
               :entry
               [{:fhir/type :fhir.Bundle/entry
                 :request {}}]}})]

        (is (= 200 status))

        (testing "entry response"
          (testing "status"
            (is (= "400" (:status response))))

          (testing "outcome"
            (given (:outcome response)
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"value"
              [:issue 0 :expression 0] := "Bundle.entry[0].request"
              [:issue 0 :diagnostics] := "Missing url.")))))

    (testing "returns error on missing request method"
      (let [{:keys [status] {[{:keys [response]}] :entry} :body}
            ((handler-with [])
             {:body
              {:fhir/type :fhir/Bundle
               :type #fhir/code"batch"
               :entry
               [{:fhir/type :fhir.Bundle/entry
                 :request
                 {:fhir/type :fhir.Bundle.entry/request
                  :url #fhir/uri"Patient/0"}}]}})]

        (is (= 200 status))

        (testing "entry response"
          (testing "status"
            (is (= "400" (:status response))))

          (testing "outcome"
            (given (:outcome response)
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"value"
              [:issue 0 :expression 0] := "Bundle.entry[0].request"
              [:issue 0 :diagnostics] := "Missing method.")))))

    (testing "returns error on unknown method"
      (let [{:keys [status] {[{:keys [response]}] :entry} :body}
            ((handler-with [])
             {:body
              {:fhir/type :fhir/Bundle
               :type #fhir/code"batch"
               :entry
               [{:fhir/type :fhir.Bundle/entry
                 :request
                 {:fhir/type :fhir.Bundle.entry/request
                  :method #fhir/code"FOO"
                  :url #fhir/uri"Patient/0"}}]}})]

        (is (= 200 status))

        (testing "entry response"
          (testing "status"
            (is (= "400" (:status response))))

          (testing "outcome"
            (given (:outcome response)
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"value"
              [:issue 0 :expression 0] := "Bundle.entry[0].request.method"
              [:issue 0 :diagnostics] := "Unknown method `FOO`.")))))

    (testing "returns error on unsupported method"
      (let [{:keys [status] {[{:keys [response]}] :entry} :body}
            ((handler-with [])
             {:body
              {:fhir/type :fhir/Bundle
               :type #fhir/code"batch"
               :entry
               [{:fhir/type :fhir.Bundle/entry
                 :request
                 {:fhir/type :fhir.Bundle.entry/request
                  :method #fhir/code"PATCH"
                  :url #fhir/uri"Patient/0"}}]}})]

        (is (= 200 status))

        (testing "entry response"
          (testing "status"
            (is (= "422" (:status response))))

          (testing "outcome"
            (given (:outcome response)
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"not-supported"
              [:issue 0 :expression 0] := "Bundle.entry[0].request.method"
              [:issue 0 :diagnostics] := "Unsupported method `PATCH`.")))))

    (testing "and update interaction"
      (testing "returns error on invalid type-level URL"
        (let [{:keys [status] {[{:keys [response]}] :entry} :body}
              ((handler-with [])
               {:body
                {:fhir/type :fhir/Bundle
                 :type #fhir/code"batch"
                 :entry
                 [{:fhir/type :fhir.Bundle/entry
                   :resource
                   {:fhir/type :fhir/Patient}
                   :request
                   {:fhir/type :fhir.Bundle.entry/request
                    :method #fhir/code"PUT"
                    :url #fhir/uri"Patient"}}]}})]

          (is (= 200 status))

          (testing "entry response"
            (testing "status"
              (is (= "400" (:status response))))

            (testing "outcome"
              (given (:outcome response)
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code"error"
                [:issue 0 :code] := #fhir/code"value"
                [:issue 0 :expression 0] := "Bundle.entry[0].request.url"
                [:issue 0 :diagnostics] :=
                "Can't parse id from URL `Patient`.")))))

      (testing "returns entry with error on optimistic locking failure"
        (let [{:keys [status] {[{:keys [response]}] :entry} :body}
              ((handler-with [[[:create {:fhir/type :fhir/Patient :id "0"}]]
                              [[:put {:fhir/type :fhir/Patient :id "0"}]]])
               {:body
                {:fhir/type :fhir/Bundle
                 :type #fhir/code"batch"
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

          (is (= 200 status))

          (testing "entry response"
            (testing "status"
              (is (= "412" (:status response))))

            (testing "outcome"
              (given (:outcome response)
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code"error"
                [:issue 0 :code] := #fhir/code"conflict"
                [:issue 0 :expression 0] := "Bundle.entry[0]"
                [:issue 0 :diagnostics] :=
                "Precondition `W/\"1\"` failed on `Patient/0`.")))))

      (testing "without return preference"
        (let [{:keys [status] {[{:keys [resource response]}] :entry} :body}
              ((handler-with [])
               {:body
                {:fhir/type :fhir/Bundle
                 :type #fhir/code"batch"
                 :entry
                 [{:fhir/type :fhir.Bundle/entry
                   :resource
                   {:fhir/type :fhir/Patient :id "0"}
                   :request
                   {:fhir/type :fhir.Bundle.entry/request
                    :method #fhir/code"PUT"
                    :url #fhir/uri"Patient/0"}}]}})]

          (is (= 200 status))

          (testing "entry resource"
            (is (nil? resource)))

          (testing "entry response"
            (given response
              :status := "201"
              :location := #fhir/uri"/Patient/0/_history/1"
              :etag := "W/\"1\""
              :lastModified := Instant/EPOCH))))

      (testing "with representation return preference"
        (let [{:keys [status] {[{:keys [resource response]}] :entry} :body}
              ((handler-with [])
               {:headers {"prefer" "return=representation"}
                :body
                {:fhir/type :fhir/Bundle
                 :type #fhir/code"batch"
                 :entry
                 [{:fhir/type :fhir.Bundle/entry
                   :resource
                   {:fhir/type :fhir/Patient :id "0"}
                   :request
                   {:fhir/type :fhir.Bundle.entry/request
                    :method #fhir/code"PUT"
                    :url #fhir/uri"Patient/0"}}]}})]

          (is (= 200 status))

          (testing "entry resource"
            (given resource
              :fhir/type := :fhir/Patient
              :id := "0"
              [:meta :versionId] := #fhir/id"1"
              [:meta :lastUpdated] := Instant/EPOCH))

          (testing "entry response"
            (given response
              :status := "201"
              :location := #fhir/uri"/Patient/0/_history/1"
              :etag := "W/\"1\""
              :lastModified := Instant/EPOCH)))))

    (testing "and create interaction"
      (testing "returns error on invalid instance-level URL"
        (let [{:keys [status] {[{:keys [response]}] :entry} :body}
              ((handler-with [])
               {:body
                {:fhir/type :fhir/Bundle
                 :type #fhir/code"batch"
                 :entry
                 [{:fhir/type :fhir.Bundle/entry
                   :resource
                   {:fhir/type :fhir/Patient}
                   :request
                   {:fhir/type :fhir.Bundle.entry/request
                    :method #fhir/code"POST"
                    :url #fhir/uri"Patient/0"}}]}})]

          (is (= 200 status))

          (testing "entry response"
            (testing "status"
              (is (= "405" (:status response))))

            (testing "outcome"
              (given (:outcome response)
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code"error"
                [:issue 0 :code] := #fhir/code"processing"
                [:issue 0 :expression 0] := "Bundle.entry[0]"
                [:issue 0 :diagnostics] :=
                "Method POST not allowed on `/Patient/0` endpoint.")))))

      (testing "returns error violated referential integrity"
        (let [{:keys [status] {[{:keys [response]}] :entry} :body}
              ((handler-with [])
               {:body
                {:fhir/type :fhir/Bundle
                 :type #fhir/code"batch"
                 :entry
                 [{:fhir/type :fhir.Bundle/entry
                   :resource
                   {:fhir/type :fhir/Observation
                    :subject
                    {:fhir/type :fhir/Reference
                     :reference "Patient/0"}}
                   :request
                   {:fhir/type :fhir.Bundle.entry/request
                    :method #fhir/code"POST"
                    :url #fhir/uri"Observation"}}]}})]

          (is (= 200 status))

          (testing "entry response"
            (testing "status"
              (is (= "409" (:status response))))

            (testing "outcome"
              (given (:outcome response)
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code"error"
                [:issue 0 :code] := #fhir/code"conflict"
                [:issue 0 :expression 0] := "Bundle.entry[0]"
                [:issue 0 :diagnostics] :=
                "Referential integrity violated. Resource `Patient/0` doesn't exist."))))))

    (testing "and read interaction"
      (let [{:keys [status] {[{:keys [resource response]}] :entry} :body}
            ((handler-with [[[:create {:fhir/type :fhir/Patient :id "0"}]]])
             {:body
              {:fhir/type :fhir/Bundle
               :type #fhir/code"batch"
               :entry
               [{:fhir/type :fhir.Bundle/entry
                 :request
                 {:fhir/type :fhir.Bundle.entry/request
                  :method #fhir/code"GET"
                  :url #fhir/uri"Patient/0"}}]}})]

        (is (= 200 status))

        (testing "entry resource"
          (given resource
            :fhir/type := :fhir/Patient
            :id := "0"
            [:meta :versionId] := #fhir/id"1"
            [:meta :lastUpdated] := Instant/EPOCH))

        (testing "entry response"
          (given response
            :status := "200"
            :etag := "W/\"1\""
            :lastModified := Instant/EPOCH))))

    (testing "and search-type interaction"
      (let [{:keys [status] {[{:keys [resource response]}] :entry} :body}
            ((handler-with [[[:create {:fhir/type :fhir/Patient :id "0"}]]])
             {:body
              {:fhir/type :fhir/Bundle
               :type #fhir/code"batch"
               :entry
               [{:fhir/type :fhir.Bundle/entry
                 :request
                 {:fhir/type :fhir.Bundle.entry/request
                  :method #fhir/code"GET"
                  :url #fhir/uri"Patient?_id=0"}}]}})]

        (is (= 200 status))

        (testing "entry resource"
          (given resource
            :fhir/type := :fhir/Bundle
            :type := #fhir/code"searchset"
            [:entry 0 :resource :fhir/type] := :fhir/Patient
            [:entry 0 :resource :id] := "0"
            [:entry 0 :resource :meta :versionId] := #fhir/id"1"
            [:entry 0 :resource :meta :lastUpdated] := Instant/EPOCH))

        (testing "entry response"
          (given response
            :status := "200"))))))
