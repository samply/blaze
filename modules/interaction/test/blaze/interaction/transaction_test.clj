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
    [blaze.interaction.create]
    [blaze.interaction.delete]
    [blaze.interaction.read]
    [blaze.interaction.search-type]
    [blaze.interaction.test-util :refer [given-thrown]]
    [blaze.interaction.transaction]
    [blaze.interaction.update]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [reitit.ring]
    [ring.middleware.params :refer [wrap-params]]
    [taoensso.timbre :as log])
  (:import
    [java.time Clock Instant ZoneId]
    [java.util Random]
    [java.util.concurrent ThreadPoolExecutor]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def ^:private operation-outcome
  #fhir/uri"http://terminology.hl7.org/CodeSystem/operation-outcome")


(defonce executor (ex/single-thread-executor))


(def clock (Clock/fixed Instant/EPOCH (ZoneId/of "UTC")))


(def ^:private base-url "base-url-115515")


(def fixed-random 
  (proxy [Random] []
    (nextLong []
      0)))


(defn- create-handler [node]
  (-> (ig/init
        {:blaze.interaction/create
         {:node node
          :executor executor
          :clock clock
          :rng-fn (constantly fixed-random)}})
      :blaze.interaction/create))


(defn- search-type-handler [node]
  (-> (ig/init
        {:blaze.interaction/search-type
         {:node node
          :clock clock
          :rng-fn (constantly fixed-random)}})
      :blaze.interaction/search-type))


(defn- delete-handler [node]
  (-> (ig/init
        {:blaze.interaction/delete
         {:node node
          :executor executor}})
      :blaze.interaction/delete))


(defn- read-handler [node]
  (-> (ig/init
        {:blaze.interaction/read
         {:node node}})
      :blaze.interaction/read))


(defn- update-handler [node]
  (-> (ig/init
        {:blaze.interaction/update
         {:node node
          :executor executor}})
      :blaze.interaction/update))


(defn router [node]
  (reitit.ring/router
    [["/Observation"
      {:name :Observation/type
       :fhir.resource/type "Observation"
       :post (create-handler node)}]
     ["/Patient"
      {:name :Patient/type
       :fhir.resource/type "Patient"
       :get (wrap-params (search-type-handler node))
       :post (create-handler node)}]
     ["/Patient/{id}"
      {:name :Patient/instance
       :fhir.resource/type "Patient"
       :get (read-handler node)
       :delete (delete-handler node)
       :put (update-handler node)}]
     ["/Patient/{id}/_history/{vid}"
      {:name :Patient/versioned-instance}]]
    {:syntax :bracket}))


(defn batch-handler [router]
  (reitit.ring/ring-handler router handler-util/default-handler))


(defn- handler [node]
  (-> (ig/init
        {:blaze.interaction/transaction
         {:node node
          :executor executor
          :clock clock
          :rng-fn (constantly fixed-random)}})
      :blaze.interaction/transaction))


(defn- handler-with [txs]
  (fn [request]
    (with-open [node (mem-node-with txs)]
      (let [router (router node)]
        @((handler node)
          (assoc request
            :blaze/base-url base-url
            ::reitit/router router
            :batch-handler (batch-handler router)))))))


(deftest init-handler-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.interaction/transaction nil})
      :key := :blaze.interaction/transaction
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.interaction/transaction {}})
      :key := :blaze.interaction/transaction
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :executor))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:explain ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))))

  (testing "invalid executor"
    (given-thrown (ig/init {:blaze.interaction/transaction {:executor "foo"}})
      :key := :blaze.interaction/transaction
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:explain ::s/problems 3 :pred] := `ex/executor?
      [:explain ::s/problems 3 :val] := "foo")))


(deftest init-executor-test
  (testing "nil config"
    (given (ig/init {:blaze.interaction.transaction/executor nil})
      :blaze.interaction.transaction/executor :instanceof ThreadPoolExecutor)))


(deftest handler-test
  (testing "on missing body"
    (let [{:keys [status body]}
          ((handler-with [])
           {})]

      (testing "returns error"
        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code"error"
          [:issue 0 :code] := #fhir/code"invalid"
          [:issue 0 :diagnostics] := "Missing Bundle."))))

  (testing "on wrong resource type."
    (let [{:keys [status body]}
          ((handler-with [])
           {:body
            {:fhir/type :fhir/Patient}})]

      (testing "returns error"
        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code"error"
          [:issue 0 :code] := #fhir/code"value"
          [:issue 0 :diagnostics] := "Expected a Bundle resource but got a Patient resource."))))

  (testing "on wrong Bundle type."
    (let [{:keys [status body]}
          ((handler-with [])
           {:body
            {:fhir/type :fhir/Bundle
             :type #fhir/code"foo"}})]

      (testing "returns error"
        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code"error"
          [:issue 0 :code] := #fhir/code"value"
          [:issue 0 :diagnostics] := "Expected a Bundle type of batch or transaction but was `foo`."))))

  (doseq [type ["transaction" "batch"]]
    (testing (format "On %s bundle" type)
      (testing "empty bundle"
        (let [{:keys [status body]}
              ((handler-with [])
               {:body
                {:fhir/type :fhir/Bundle
                 :type (type/->Code type)}})]

          (testing "response status"
            (is (= 200 status)))

          (testing "bundle"
            (given body
              :fhir/type := :fhir/Bundle
              :id := "AAAAAAAAAAAAAAAA"
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

                (testing "response status"
                  (is (= 200 status)))

                (testing "bundle"
                  (given body
                    :fhir/type := :fhir/Bundle
                    :id := "AAAAAAAAAAAAAAAA"
                    :type := (type/->Code (str type "-response"))))

                (testing "entry resource"
                  (is (nil? resource)))

                (testing "entry response"
                  (given response
                    :status := "201"
                    :location := #fhir/uri"base-url-115515/Patient/0/_history/1"
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

                (testing "response status"
                  (is (= 200 status)))

                (testing "bundle"
                  (given body
                    :fhir/type := :fhir/Bundle
                    :id := "AAAAAAAAAAAAAAAA"
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
                    :location := #fhir/uri"base-url-115515/Patient/0/_history/1"
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

                (testing "response status"
                  (is (= 200 status)))

                (testing "bundle"
                  (given body
                    :fhir/type := :fhir/Bundle
                    :id := "AAAAAAAAAAAAAAAA"
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

                (testing "response status"
                  (is (= 200 status)))

                (testing "bundle"
                  (given body
                    :fhir/type := :fhir/Bundle
                    :id := "AAAAAAAAAAAAAAAA"
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

          (testing "without return preference"
            (let [{:keys [status body]
                   {[{:keys [resource response]}] :entry} :body}
                  ((handler-with [])
                   {:body
                    {:fhir/type :fhir/Bundle
                     :type (type/->Code type)
                     :entry entries}})]

              (testing "response status"
                (is (= 200 status)))

              (testing "bundle"
                (given body
                  :fhir/type := :fhir/Bundle
                  :id := "AAAAAAAAAAAAAAAA"
                  :type := (type/->Code (str type "-response"))))

              (testing "entry resource"
                (is (nil? resource)))

              (testing "entry response"
                (given response
                  :status := "201"
                  :location := #fhir/uri"base-url-115515/Patient/AAAAAAAAAAAAAAAA/_history/1"
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

              (testing "response status"
                (is (= 200 status)))

              (testing "bundle"
                (given body
                  :fhir/type := :fhir/Bundle
                  :id := "AAAAAAAAAAAAAAAA"
                  :type := (type/->Code (str type "-response"))))

              (testing "entry resource"
                (given resource
                  :fhir/type := :fhir/Patient
                  :id := "AAAAAAAAAAAAAAAA"
                  [:meta :versionId] := #fhir/id"1"
                  [:meta :lastUpdated] := Instant/EPOCH))

              (testing "entry response"
                (given response
                  :status := "201"
                  :location := #fhir/uri"base-url-115515/Patient/AAAAAAAAAAAAAAAA/_history/1"
                  :etag := "W/\"1\""
                  :lastModified := Instant/EPOCH))))))

      (testing "and conditional create interaction"
        (testing "with non-matching patient"
          (testing "without return preference"
            (let [{:keys [status body]
                   {[{:keys [resource response]}] :entry} :body}
                  ((handler-with
                     [[[:put {:fhir/type :fhir/Patient :id "0"
                              :identifier
                              [#fhir/Identifier{:value "095156"}]}]]])
                   {:body
                    {:fhir/type :fhir/Bundle
                     :type (type/->Code type)
                     :entry
                     [{:fhir/type :fhir.Bundle/entry
                       :resource
                       {:fhir/type :fhir/Patient}
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code"POST"
                        :url #fhir/uri"Patient"
                        :ifNoneExist "identifier=150015"}}]}})]

              (testing "the new patient is returned"
                (testing "response status"
                  (is (= 200 status)))

                (testing "bundle"
                  (given body
                    :fhir/type := :fhir/Bundle
                    :id := "AAAAAAAAAAAAAAAA"
                    :type := (type/->Code (str type "-response"))))

                (testing "entry resource"
                  (is (nil? resource)))

                (testing "entry response"
                  (given response
                    :status := "201"
                    :etag := "W/\"2\""
                    :lastModified := Instant/EPOCH)))))

          (testing "with representation return preference"
            (let [{:keys [status body]
                   {[{:keys [resource response]}] :entry} :body}
                  ((handler-with
                     [[[:put {:fhir/type :fhir/Patient :id "0"
                              :identifier
                              [#fhir/Identifier{:value "095156"}]}]]])
                   {:headers {"prefer" "return=representation"}
                    :body
                    {:fhir/type :fhir/Bundle
                     :type (type/->Code type)
                     :entry
                     [{:fhir/type :fhir.Bundle/entry
                       :resource
                       {:fhir/type :fhir/Patient}
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code"POST"
                        :url #fhir/uri"Patient"
                        :ifNoneExist "identifier=150015"}}]}})]

              (testing "the new patient is returned"
                (testing "response status"
                  (is (= 200 status)))

                (testing "bundle"
                  (given body
                    :fhir/type := :fhir/Bundle
                    :id := "AAAAAAAAAAAAAAAA"
                    :type := (type/->Code (str type "-response"))))

                (testing "entry resource"
                  (given resource
                    :fhir/type := :fhir/Patient
                    :id := "AAAAAAAAAAAAAAAA"
                    [:meta :versionId] := #fhir/id"2"
                    [:meta :lastUpdated] := Instant/EPOCH))

                (testing "entry response"
                  (given response
                    :status := "201"
                    :etag := "W/\"2\""
                    :lastModified := Instant/EPOCH))))))

        (testing "with matching patient"
          (testing "without return preference"
            (let [{:keys [status body]
                   {[{:keys [resource response]}] :entry} :body}
                  ((handler-with
                     [[[:put {:fhir/type :fhir/Patient :id "0"
                              :identifier
                              [#fhir/Identifier{:value "095156"}]}]]])
                   {:body
                    {:fhir/type :fhir/Bundle
                     :type (type/->Code type)
                     :entry
                     [{:fhir/type :fhir.Bundle/entry
                       :resource
                       {:fhir/type :fhir/Patient}
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code"POST"
                        :url #fhir/uri"Patient"
                        :ifNoneExist "identifier=095156"}}]}})]

              (testing "the existing patient is returned"
                (testing "response status"
                  (is (= 200 status)))

                (testing "bundle"
                  (given body
                    :fhir/type := :fhir/Bundle
                    :id := "AAAAAAAAAAAAAAAA"
                    :type := (type/->Code (str type "-response"))))

                (testing "entry resource"
                  (is (nil? resource)))

                (testing "entry response"
                  (given response
                    :status := "200"
                    :etag := "W/\"1\""
                    :lastModified := Instant/EPOCH)))))

          (testing "with representation return preference"
            (let [{:keys [status body]
                   {[{:keys [resource response]}] :entry} :body}
                  ((handler-with
                     [[[:put {:fhir/type :fhir/Patient :id "0"
                              :identifier
                              [#fhir/Identifier{:value "095156"}]}]]])
                   {:headers {"prefer" "return=representation"}
                    :body
                    {:fhir/type :fhir/Bundle
                     :type (type/->Code type)
                     :entry
                     [{:fhir/type :fhir.Bundle/entry
                       :resource
                       {:fhir/type :fhir/Patient}
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code"POST"
                        :url #fhir/uri"Patient"
                        :ifNoneExist "identifier=095156"}}]}})]

              (testing "the existing patient is returned"
                (testing "response status"
                  (is (= 200 status)))

                (testing "bundle"
                  (given body
                    :fhir/type := :fhir/Bundle
                    :id := "AAAAAAAAAAAAAAAA"
                    :type := (type/->Code (str type "-response"))))

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

              (testing "response status"
                (is (= 200 status)))

              (testing "bundle"
                (given body
                  :fhir/type := :fhir/Bundle
                  :id := "AAAAAAAAAAAAAAAA"
                  :type := (type/->Code (str type "-response"))))

              (testing "entry resource"
                (is (nil? resource)))

              (testing "entry response"
                (given response
                  :status := "204"
                  :etag := "W/\"2\""
                  :lastModified := Instant/EPOCH)))))))

    #_(testing "and read interaction"
        (let [{:keys [status body]
               {[{:keys [resource response]}] :entry} :body}
              ((handler-with
                 [[[:put {:fhir/type :fhir/Patient :id "0"}]]])
               {:body
                {:fhir/type :fhir/Bundle
                 :type (type/->Code type)
                 :entry
                 [{:fhir/type :fhir.Bundle/entry
                   :request
                   {:fhir/type :fhir.Bundle.entry/request
                    :method #fhir/code"GET"
                    :url #fhir/uri"Patient/0"}}]}})]

          (testing "response status"
            (is (= 200 status)))

          #_(testing "bundle"
              (given body
                :fhir/type := :fhir/Bundle
                :id := "AAAAAAAAAAAAAAAA"
                :type := (type/->Code (str type "-response"))))

          #_(testing "entry resource"
              (given resource
                :fhir/type := :fhir/Patient
                :id := "0"
                [:meta :versionId] := #fhir/id"1"
                [:meta :lastUpdated] := Instant/EPOCH))

          #_(testing "entry response"
              (given response
                :status := "200"
                :etag := "W/\"1\""
                :lastModified := Instant/EPOCH)))))

  (testing "On transaction bundle"
    (testing "on missing request"
      (let [{:keys [status body]}
            ((handler-with [])
             {:body
              {:fhir/type :fhir/Bundle
               :type #fhir/code"transaction"
               :entry
               [{}]}})]

        (testing "returns error"
          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"value"
            [:issue 0 :expression 0] := "Bundle.entry[0]"
            [:issue 0 :diagnostics] := "Missing request."))))

    (testing "on missing request url"
      (let [{:keys [status body]}
            ((handler-with [])
             {:body
              {:fhir/type :fhir/Bundle
               :type #fhir/code"transaction"
               :entry
               [{:fhir/type :fhir.Bundle/entry
                 :request {}}]}})]

        (testing "returns error"
          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"value"
            [:issue 0 :expression 0] := "Bundle.entry[0].request"
            [:issue 0 :diagnostics] := "Missing url."))))

    (testing "on missing request method"
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

        (testing "returns error"
          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"value"
            [:issue 0 :expression 0] := "Bundle.entry[0].request"
            [:issue 0 :diagnostics] := "Missing method."))))

    (testing "on unknown method"
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

        (testing "returns error"
          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"value"
            [:issue 0 :expression 0] := "Bundle.entry[0].request.method"
            [:issue 0 :diagnostics] := "Unknown method `FOO`."))))

    (testing "on unsupported method"
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

        (testing "returns error"
          (is (= 422 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"not-supported"
            [:issue 0 :expression 0] := "Bundle.entry[0].request.method"
            [:issue 0 :diagnostics] := "Unsupported method `PATCH`."))))

    (testing "and update interaction"
      (testing "on missing type in URL"
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

          (testing "returns error"
            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"value"
              [:issue 0 :expression 0] := "Bundle.entry[0].request.url"
              [:issue 0 :diagnostics] := "Can't parse type from `entry.request.url` ``."))))

      (testing "on unknown type"
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

          (testing "returns error"
            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"value"
              [:issue 0 :expression 0] := "Bundle.entry[0].request.url"
              [:issue 0 :diagnostics] := "Unknown type `Foo` in bundle entry URL `Foo/0`."))))

      (testing "on type mismatch"
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

          (testing "returns error "
            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"invariant"
              [:issue 0 :details :coding 0 :system] := operation-outcome
              [:issue 0 :details :coding 0 :code] := #fhir/code"MSG_RESOURCE_TYPE_MISMATCH"
              [:issue 0 :expression 0] := "Bundle.entry[0].request.url"
              [:issue 0 :expression 1] := "Bundle.entry[0].resource.resourceType"))))

      (testing "on missing ID"
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

          (testing "returns error "
            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"required"
              [:issue 0 :details :coding 0 :system] := operation-outcome
              [:issue 0 :details :coding 0 :code] := #fhir/code"MSG_RESOURCE_ID_MISSING"
              [:issue 0 :expression 0] := "Bundle.entry[0].resource.id"))))

      (testing "on missing ID in URL"
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

          (testing "returns error"
            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"value"
              [:issue 0 :expression 0] := "Bundle.entry[0].request.url"
              [:issue 0 :diagnostics] := "Can't parse id from URL `Patient`."))))

      (testing "on invalid ID"
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

          (testing "returns error"
            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"value"
              [:issue 0 :details :coding 0 :system] := operation-outcome
              [:issue 0 :details :coding 0 :code] := #fhir/code"MSG_ID_INVALID"
              [:issue 0 :expression 0] := "Bundle.entry[0].resource.id"))))

      (testing "on ID mismatch"
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

          (testing "returns error"
            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"invariant"
              [:issue 0 :details :coding 0 :system] := operation-outcome
              [:issue 0 :details :coding 0 :code] := #fhir/code"MSG_RESOURCE_ID_MISMATCH"
              [:issue 0 :expression 0] := "Bundle.entry[0].request.url"
              [:issue 0 :expression 1] := "Bundle.entry[0].resource.id"))))

      (testing "on optimistic locking failure"
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

          (testing "returns error"
            (is (= 412 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"conflict"
              [:issue 0 :diagnostics] := "Precondition `W/\"1\"` failed on `Patient/0`."))))

      (testing "on duplicate resources"
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

          (testing "returns error"
            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"invariant"
              [:issue 0 :diagnostics] := "Duplicate resource `Patient/0`."))))

      (testing "on violated referential integrity"
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
                    #fhir/Reference
                        {:reference "Patient/0"}}
                   :request
                   {:fhir/type :fhir.Bundle.entry/request
                    :method #fhir/code"POST"
                    :url #fhir/uri"Observation"}}]}})]

          (testing "returns error"
            (is (= 409 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"conflict"
              [:issue 0 :diagnostics] := "Referential integrity violated. Resource `Patient/0` doesn't exist.")))))

    (testing "and create interaction"
      (testing "creates sequential identifiers"
        (let [{:keys [body]}
              ((handler-with [])
               {:headers {"prefer" "return=representation"}
                :body
                {:fhir/type :fhir/Bundle
                 :type #fhir/code"transaction"
                 :entry
                 [{:resource
                   {:fhir/type :fhir/Patient}
                   :request
                   {:method #fhir/code"POST"
                    :url #fhir/uri"Patient"}}
                  {:resource
                   {:fhir/type :fhir/Patient}
                   :request
                   {:method #fhir/code"POST"
                    :url #fhir/uri"Patient"}}]}})]
          (given body
            [:entry 0 :resource :id] := "AAAAAAAAAAAAAAAA"
            [:entry 1 :resource :id] := "AAAAAAAAAAAAAAAB"))))

    (testing "and conditional create interaction"
      (testing "on multiple matching patients"
        (let [{:keys [status body]}
              ((handler-with
                 [[[:put {:fhir/type :fhir/Patient :id "0"
                          :birthDate #fhir/date"2020"}]
                   [:put {:fhir/type :fhir/Patient :id "1"
                          :birthDate #fhir/date"2020"}]]])
               {:body
                {:fhir/type :fhir/Bundle
                 :type #fhir/code"transaction"
                 :entry
                 [{:fhir/type :fhir.Bundle/entry
                   :resource
                   {:fhir/type :fhir/Patient}
                   :request
                   {:fhir/type :fhir.Bundle.entry/request
                    :method #fhir/code"POST"
                    :url #fhir/uri"Patient"
                    :ifNoneExist "birthdate=2020"}}]}})]

          (testing "returns error"
            (is (= 412 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"conflict"
              [:issue 0 :diagnostics] := "Conditional create of a Patient with query `birthdate=2020` failed because at least the two matches `Patient/0/_history/1` and `Patient/1/_history/1` were found."))))))

  (testing "On batch bundle"
    (testing "on missing request"
      (let [{:keys [status] {[{:keys [response]}] :entry} :body}
            ((handler-with [])
             {:body
              {:fhir/type :fhir/Bundle
               :type #fhir/code"batch"
               :entry
               [{}]}})]

        (testing "response status"
          (is (= 200 status)))

        (testing "returns error"
          (testing "with status"
            (is (= "400" (:status response))))

          (testing "with outcome"
            (given (:outcome response)
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"value"
              [:issue 0 :expression 0] := "Bundle.entry[0]"
              [:issue 0 :diagnostics] := "Missing request.")))))

    (testing "on missing request url"
      (let [{:keys [status] {[{:keys [response]}] :entry} :body}
            ((handler-with [])
             {:body
              {:fhir/type :fhir/Bundle
               :type #fhir/code"batch"
               :entry
               [{:fhir/type :fhir.Bundle/entry
                 :request {}}]}})]

        (testing "response status"
          (is (= 200 status)))

        (testing "returns error"
          (testing "with status"
            (is (= "400" (:status response))))

          (testing "with outcome"
            (given (:outcome response)
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"value"
              [:issue 0 :expression 0] := "Bundle.entry[0].request"
              [:issue 0 :diagnostics] := "Missing url.")))))

    (testing "on missing request method"
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

        (testing "response status"
          (is (= 200 status)))

        (testing "returns error"
          (testing "with status"
            (is (= "400" (:status response))))

          (testing "with outcome"
            (given (:outcome response)
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"value"
              [:issue 0 :expression 0] := "Bundle.entry[0].request"
              [:issue 0 :diagnostics] := "Missing method.")))))

    (testing "on unknown method"
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

        (testing "response status"
          (is (= 200 status)))

        (testing "returns error"
          (testing "with status"
            (is (= "400" (:status response))))

          (testing "with outcome"
            (given (:outcome response)
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"value"
              [:issue 0 :expression 0] := "Bundle.entry[0].request.method"
              [:issue 0 :diagnostics] := "Unknown method `FOO`.")))))

    (testing "on unsupported method"
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

        (testing "response status"
          (is (= 200 status)))

        (testing "returns error"
          (testing "with status"
            (is (= "422" (:status response))))

          (testing "with outcome"
            (given (:outcome response)
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"not-supported"
              [:issue 0 :expression 0] := "Bundle.entry[0].request.method"
              [:issue 0 :diagnostics] := "Unsupported method `PATCH`.")))))

    (testing "and update interaction"
      (testing "on invalid type-level URL"
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

          (testing "response status"
            (is (= 200 status)))

          (testing "returns error"
            (testing "with status"
              (is (= "400" (:status response))))

            (testing "with outcome"
              (given (:outcome response)
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code"error"
                [:issue 0 :code] := #fhir/code"value"
                [:issue 0 :expression 0] := "Bundle.entry[0].request.url"
                [:issue 0 :diagnostics] :=
                "Can't parse id from URL `Patient`.")))))

      (testing "on optimistic locking failure"
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

          (testing "response status"
            (is (= 200 status)))

          (testing "returns error"
            (testing "with status"
              (is (= "412" (:status response))))

            (testing "with outcome"
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

          (testing "response status"
            (is (= 200 status)))

          (testing "entry resource"
            (is (nil? resource)))

          (testing "entry response"
            (given response
              :status := "201"
              :location := #fhir/uri"base-url-115515/Patient/0/_history/1"
              :etag := "W/\"1\""
              :lastModified := Instant/EPOCH)))

        (testing "leading slash in URL is removed"
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
                      :url #fhir/uri"/Patient/0"}}]}})]

            (testing "response status"
              (is (= 200 status)))

            (testing "entry resource"
              (is (nil? resource)))

            (testing "entry response"
              (given response
                :status := "201"
                :location := #fhir/uri"base-url-115515/Patient/0/_history/1"
                :etag := "W/\"1\""
                :lastModified := Instant/EPOCH)))))

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

          (testing "response status"
            (is (= 200 status)))

          (testing "entry resource"
            (given resource
              :fhir/type := :fhir/Patient
              :id := "0"
              [:meta :versionId] := #fhir/id"1"
              [:meta :lastUpdated] := Instant/EPOCH))

          (testing "entry response"
            (given response
              :status := "201"
              :location := #fhir/uri"base-url-115515/Patient/0/_history/1"
              :etag := "W/\"1\""
              :lastModified := Instant/EPOCH)))))

    (testing "and create interaction"
      (testing "on invalid instance-level URL"
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

          (testing "response status"
            (is (= 200 status)))

          (testing "returns error"
            (testing "with status"
              (is (= "405" (:status response))))

            (testing "with outcome"
              (given (:outcome response)
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code"error"
                [:issue 0 :code] := #fhir/code"processing"
                [:issue 0 :expression 0] := "Bundle.entry[0]"
                [:issue 0 :diagnostics] :=
                "Method POST not allowed on `/Patient/0` endpoint.")))))

      (testing "on violated referential integrity"
        (let [{:keys [status] {[{:keys [response]}] :entry} :body}
              ((handler-with [])
               {:body
                {:fhir/type :fhir/Bundle
                 :type #fhir/code"batch"
                 :entry
                 [{:fhir/type :fhir.Bundle/entry
                   :resource
                   {:fhir/type :fhir/Observation
                    :subject #fhir/Reference{:reference "Patient/0"}}
                   :request
                   {:fhir/type :fhir.Bundle.entry/request
                    :method #fhir/code"POST"
                    :url #fhir/uri"Observation"}}]}})]

          (testing "response status"
            (is (= 200 status)))

          (testing "returns error"
            (testing "with status"
              (is (= "409" (:status response))))

            (testing "with outcome"
              (given (:outcome response)
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code"error"
                [:issue 0 :code] := #fhir/code"conflict"
                [:issue 0 :expression 0] := "Bundle.entry[0]"
                [:issue 0 :diagnostics] :=
                "Referential integrity violated. Resource `Patient/0` doesn't exist."))))))

    (testing "and conditional create interaction"
      (testing "on multiple matching patients"
        (let [{:keys [status] {[{:keys [response]}] :entry} :body}
              ((handler-with
                 [[[:put {:fhir/type :fhir/Patient :id "0"
                          :birthDate #fhir/date"2020"}]
                   [:put {:fhir/type :fhir/Patient :id "1"
                          :birthDate #fhir/date"2020"}]]])
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
                    :url #fhir/uri"Patient"
                    :ifNoneExist "birthdate=2020"}}]}})]

          (testing "response status"
            (is (= 200 status)))

          (testing "returns error"
            (testing "with status"
              (is (= "412" (:status response))))

            (testing "with outcome"
              (given (:outcome response)
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code"error"
                [:issue 0 :code] := #fhir/code"conflict"
                [:issue 0 :expression 0] := "Bundle.entry[0]"
                [:issue 0 :diagnostics] :=
                "Conditional create of a Patient with query `birthdate=2020` failed because at least the two matches `Patient/0/_history/1` and `Patient/1/_history/1` were found."))))))

    (testing "and search-type interaction"
      (let [{:keys [status] {[{:keys [resource response]}] :entry} :body}
            ((handler-with [[[:create {:fhir/type :fhir/Patient :id "0"}]
                             [:create {:fhir/type :fhir/Patient :id "1"}]]])
             {:body
              {:fhir/type :fhir/Bundle
               :type #fhir/code"batch"
               :entry
               [{:fhir/type :fhir.Bundle/entry
                 :request
                 {:fhir/type :fhir.Bundle.entry/request
                  :method #fhir/code"GET"
                  :url #fhir/uri"Patient?_id=0"}}]}})]

        (testing "response status"
          (is (= 200 status)))

        (testing "entry resource"
          (given resource
            :fhir/type := :fhir/Bundle
            :type := #fhir/code"searchset"
            [:entry count] := 1
            [:entry 0 :resource :fhir/type] := :fhir/Patient
            [:entry 0 :resource :id] := "0"
            [:entry 0 :resource :meta :versionId] := #fhir/id"1"
            [:entry 0 :resource :meta :lastUpdated] := Instant/EPOCH))

        (testing "entry response"
          (given response
            :status := "200")))

      (testing "with _summary=count"
        (let [{:keys [status] {[{:keys [resource response]}] :entry} :body}
              ((handler-with [[[:create {:fhir/type :fhir/Patient :id "0"}]
                               [:create {:fhir/type :fhir/Patient :id "1"}]]])
               {:body
                {:fhir/type :fhir/Bundle
                 :type #fhir/code"batch"
                 :entry
                 [{:fhir/type :fhir.Bundle/entry
                   :request
                   {:fhir/type :fhir.Bundle.entry/request
                    :method #fhir/code"GET"
                    :url #fhir/uri"Patient?_summary=count"}}]}})]

          (testing "response status"
            (is (= 200 status)))

          (testing "entry resource"
            (given resource
              :fhir/type := :fhir/Bundle
              :type := #fhir/code"searchset"
              :total := #fhir/unsignedInt 2
              :entry :? empty?))

          (testing "entry response"
            (given response
              :status := "200")))))))
