(ns blaze.interaction.transaction-test
  "Specifications relevant for the FHIR batch/transaction interaction:

  https://www.hl7.org/fhir/http.html#transaction
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.db.api-stub :refer [mem-node-system with-system-data]]
    [blaze.executors :as ex]
    [blaze.fhir.spec.type :as type]
    [blaze.fhir.structure-definition-repo]
    [blaze.handler.util :as handler-util]
    [blaze.interaction.create]
    [blaze.interaction.delete]
    [blaze.interaction.read]
    [blaze.interaction.search-type]
    [blaze.interaction.transaction]
    [blaze.interaction.update]
    [blaze.interaction.util-spec]
    [blaze.middleware.fhir.db :refer [wrap-db]]
    [blaze.middleware.fhir.db-spec]
    [blaze.middleware.fhir.error :refer [wrap-error]]
    [blaze.page-store-spec]
    [blaze.page-store.local]
    [blaze.test-util :as tu :refer [given-thrown]]
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
    [java.time Instant]
    [java.util.concurrent ThreadPoolExecutor]))


(st/instrument)
(tu/init-fhir-specs)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def operation-outcome
  #fhir/uri "http://terminology.hl7.org/CodeSystem/operation-outcome")


(def base-url "base-url-115515")


(defmethod ig/init-key ::router
  [_ {:keys [node create-handler search-type-handler
             read-handler delete-handler update-handler]}]
  (reitit.ring/router
    [["/Observation"
      {:name :Observation/type
       :fhir.resource/type "Observation"
       :get {:middleware [[wrap-db node]]
             :handler (wrap-params search-type-handler)}
       :post create-handler}]
     ["/Patient"
      {:name :Patient/type
       :fhir.resource/type "Patient"
       :get {:middleware [[wrap-db node]]
             :handler (wrap-params search-type-handler)}
       :post create-handler}]
     ["/Patient/{id}"
      {:name :Patient/instance
       :fhir.resource/type "Patient"
       :get {:middleware [[wrap-db node]] :handler read-handler}
       :delete delete-handler
       :put update-handler}]
     ["/Patient/{id}/_history/{vid}"
      {:name :Patient/versioned-instance}]]
    {:syntax :bracket}))


(defn batch-handler [router]
  (reitit.ring/ring-handler router handler-util/default-batch-handler))


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
    (given-thrown (ig/init {:blaze.interaction/transaction {:executor ::invalid}})
      :key := :blaze.interaction/transaction
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:explain ::s/problems 3 :pred] := `ex/executor?
      [:explain ::s/problems 3 :val] := ::invalid)))


(deftest init-executor-test
  (testing "nil config"
    (given (ig/init {:blaze.interaction.transaction/executor nil})
      :blaze.interaction.transaction/executor :instanceof ThreadPoolExecutor)))


(def system
  (assoc mem-node-system
    :blaze.interaction/transaction
    {:node (ig/ref :blaze.db/node)
     :executor (ig/ref :blaze.interaction.transaction/executor)
     :clock (ig/ref :blaze.test/clock)
     :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}

    :blaze.interaction/create
    {:node (ig/ref :blaze.db/node)
     :executor (ig/ref :blaze.test/executor)
     :clock (ig/ref :blaze.test/clock)
     :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}

    :blaze.interaction/search-type
    {:node (ig/ref :blaze.db/node)
     :clock (ig/ref :blaze.test/clock)
     :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
     :page-store (ig/ref :blaze.page-store/local)}

    :blaze.interaction/read
    {:node (ig/ref :blaze.db/node)}

    :blaze.interaction/delete
    {:node (ig/ref :blaze.db/node)
     :executor (ig/ref :blaze.test/executor)}

    :blaze.interaction/update
    {:node (ig/ref :blaze.db/node)
     :executor (ig/ref :blaze.test/executor)}

    ::router
    {:node (ig/ref :blaze.db/node)
     :create-handler (ig/ref :blaze.interaction/create)
     :search-type-handler (ig/ref :blaze.interaction/search-type)
     :read-handler (ig/ref :blaze.interaction/read)
     :delete-handler (ig/ref :blaze.interaction/delete)
     :update-handler (ig/ref :blaze.interaction/update)}

    :blaze.interaction.transaction/executor {}
    :blaze.test/fixed-rng-fn {}
    :blaze.test/executor {}
    :blaze.page-store/local {:secure-rng (ig/ref :blaze.test/fixed-rng)}
    :blaze.test/fixed-rng {}))


(defn wrap-defaults [handler router]
  (fn [request]
    (handler
      (assoc request
        :blaze/base-url base-url
        ::reitit/router router
        :batch-handler (batch-handler router)))))


(defmacro with-handler [[handler-binding] txs & body]
  `(with-system-data [{handler# :blaze.interaction/transaction
                       router# ::router} system]
     ~txs
     (let [~handler-binding (-> handler# (wrap-defaults router#)
                                wrap-error)]
       ~@body)))


(deftest handler-test
  (with-handler [handler]
    []
    (testing "on missing body"
      (let [{:keys [status body]}
            @(handler {})]

        (testing "returns error"
          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "invalid"
            [:issue 0 :diagnostics] := "Missing Bundle."))))

    (testing "on wrong resource type"
      (let [{:keys [status body]}
            @(handler {:body {:fhir/type :fhir/Patient}})]

        (testing "returns error"
          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "value"
            [:issue 0 :diagnostics] := "Expected a Bundle resource but got a Patient resource."))))

    (testing "on wrong Bundle type"
      (let [{:keys [status body]}
            @(handler
               {:body
                {:fhir/type :fhir/Bundle
                 :type #fhir/code "foo"}})]

        (testing "returns error"
          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "value"
            [:issue 0 :diagnostics] := "Expected a Bundle type of batch or transaction but was `foo`.")))))

  (doseq [type ["transaction" "batch"]]
    (testing (format "On %s bundle" type)
      (testing "empty bundle"
        (with-handler [handler]
          []
          (let [{:keys [status body]}
                @(handler
                   {:body
                    {:fhir/type :fhir/Bundle
                     :type (type/code type)}})]

            (testing "response status"
              (is (= 200 status)))

            (testing "bundle"
              (given body
                :fhir/type := :fhir/Bundle
                :id := "AAAAAAAAAAAAAAAA"
                :type := (type/code (str type "-response"))
                :entry :? empty?)))))

      (testing "and update interaction"
        (testing "and newly created resource"
          (let [entries
                [{:fhir/type :fhir.Bundle/entry
                  :resource
                  {:fhir/type :fhir/Patient
                   :id "0"}
                  :request
                  {:fhir/type :fhir.Bundle.entry/request
                   :method #fhir/code "PUT"
                   :url #fhir/uri "Patient/0"}}]]

            (testing "without return preference"
              (with-handler [handler]
                []
                (let [{:keys [status body]
                       {[{:keys [resource response]}] :entry} :body}
                      @(handler
                         {:body
                          {:fhir/type :fhir/Bundle
                           :type (type/code type)
                           :entry entries}})]

                  (testing "response status"
                    (is (= 200 status)))

                  (testing "bundle"
                    (given body
                      :fhir/type := :fhir/Bundle
                      :id := "AAAAAAAAAAAAAAAA"
                      :type := (type/code (str type "-response"))))

                  (testing "entry resource"
                    (is (nil? resource)))

                  (testing "entry response"
                    (given response
                      :status := "201"
                      :location := #fhir/uri "base-url-115515/Patient/0/_history/1"
                      :etag := "W/\"1\""
                      :lastModified := Instant/EPOCH)))))

            (testing "with representation return preference"
              (with-handler [handler]
                []
                (let [{:keys [status body]
                       {[{:keys [resource response]}] :entry} :body}
                      @(handler
                         {:headers {"prefer" "return=representation"}
                          :body
                          {:fhir/type :fhir/Bundle
                           :type (type/code type)
                           :entry entries}})]

                  (testing "response status"
                    (is (= 200 status)))

                  (testing "bundle"
                    (given body
                      :fhir/type := :fhir/Bundle
                      :id := "AAAAAAAAAAAAAAAA"
                      :type := (type/code (str type "-response"))))

                  (testing "entry resource"
                    (given resource
                      :fhir/type := :fhir/Patient
                      :id := "0"
                      [:meta :versionId] := #fhir/id"1"
                      [:meta :lastUpdated] := Instant/EPOCH))

                  (testing "entry response"
                    (given response
                      :status := "201"
                      :location := #fhir/uri "base-url-115515/Patient/0/_history/1"
                      :etag := "W/\"1\""
                      :lastModified := Instant/EPOCH)))))))

        (testing "and updated resource"
          (let [entries
                [{:resource
                  {:fhir/type :fhir/Patient
                   :id "0"
                   :gender #fhir/code "male"}
                  :request
                  {:fhir/type :fhir.Bundle.entry/request
                   :method #fhir/code "PUT"
                   :url #fhir/uri "Patient/0"}}]]

            (testing "without return preference"
              (with-handler [handler]
                [[[:put {:fhir/type :fhir/Patient :id "0"
                         :gender #fhir/code "female"}]]]

                (let [{:keys [status body]
                       {[{:keys [resource response]}] :entry} :body}
                      @(handler
                         {:body
                          {:fhir/type :fhir/Bundle
                           :type (type/code type)
                           :entry entries}})]

                  (testing "response status"
                    (is (= 200 status)))

                  (testing "bundle"
                    (given body
                      :fhir/type := :fhir/Bundle
                      :id := "AAAAAAAAAAAAAAAA"
                      :type := (type/code (str type "-response"))))

                  (testing "entry resource"
                    (is (nil? resource)))

                  (testing "entry response"
                    (given response
                      :status := "200"
                      :etag := "W/\"2\""
                      :lastModified := Instant/EPOCH)))))

            (testing "with representation return preference"
              (with-handler [handler]
                [[[:put {:fhir/type :fhir/Patient :id "0"
                         :gender #fhir/code "female"}]]]

                (let [{:keys [status body]
                       {[{:keys [resource response]}] :entry} :body}
                      @(handler
                         {:headers {"prefer" "return=representation"}
                          :body
                          {:fhir/type :fhir/Bundle
                           :type (type/code type)
                           :entry entries}})]

                  (testing "response status"
                    (is (= 200 status)))

                  (testing "bundle"
                    (given body
                      :fhir/type := :fhir/Bundle
                      :id := "AAAAAAAAAAAAAAAA"
                      :type := (type/code (str type "-response"))))

                  (testing "entry resource"
                    (given resource
                      :fhir/type := :fhir/Patient
                      :id := "0"
                      :gender := #fhir/code "male"
                      [:meta :versionId] := #fhir/id"2"
                      [:meta :lastUpdated] := Instant/EPOCH))

                  (testing "entry response"
                    (given response
                      :status := "200"
                      :etag := "W/\"2\""
                      :lastModified := Instant/EPOCH))))))))

      (testing "and create interaction"
        (let [entries
              [{:fhir/type :fhir.Bundle/entry
                :resource
                {:fhir/type :fhir/Patient}
                :request
                {:fhir/type :fhir.Bundle.entry/request
                 :method #fhir/code "POST"
                 :url #fhir/uri "Patient"}}]]

          (testing "without return preference"
            (with-handler [handler]
              []
              (let [{:keys [status body]
                     {[{:keys [resource response]}] :entry} :body}
                    @(handler
                       {:body
                        {:fhir/type :fhir/Bundle
                         :type (type/code type)
                         :entry entries}})]

                (testing "response status"
                  (is (= 200 status)))

                (testing "bundle"
                  (given body
                    :fhir/type := :fhir/Bundle
                    :id := "AAAAAAAAAAAAAAAA"
                    :type := (type/code (str type "-response"))))

                (testing "entry resource"
                  (is (nil? resource)))

                (testing "entry response"
                  (given response
                    :status := "201"
                    :location := #fhir/uri "base-url-115515/Patient/AAAAAAAAAAAAAAAA/_history/1"
                    :etag := "W/\"1\""
                    :lastModified := Instant/EPOCH)))))

          (testing "with representation return preference"
            (with-handler [handler]
              []
              (let [{:keys [status body]
                     {[{:keys [resource response]}] :entry} :body}
                    @(handler
                       {:headers {"prefer" "return=representation"}
                        :body
                        {:fhir/type :fhir/Bundle
                         :type (type/code type)
                         :entry entries}})]

                (testing "response status"
                  (is (= 200 status)))

                (testing "bundle"
                  (given body
                    :fhir/type := :fhir/Bundle
                    :id := "AAAAAAAAAAAAAAAA"
                    :type := (type/code (str type "-response"))))

                (testing "entry resource"
                  (given resource
                    :fhir/type := :fhir/Patient
                    :id := "AAAAAAAAAAAAAAAA"
                    [:meta :versionId] := #fhir/id"1"
                    [:meta :lastUpdated] := Instant/EPOCH))

                (testing "entry response"
                  (given response
                    :status := "201"
                    :location := #fhir/uri "base-url-115515/Patient/AAAAAAAAAAAAAAAA/_history/1"
                    :etag := "W/\"1\""
                    :lastModified := Instant/EPOCH)))))))

      (testing "and conditional create interaction"
        (testing "with non-matching patient"
          (testing "without return preference"
            (with-handler [handler]
              [[[:put {:fhir/type :fhir/Patient :id "0"
                       :identifier
                       [#fhir/Identifier {:value "095156"}]}]]]

              (let [{:keys [status body]
                     {[{:keys [resource response]}] :entry} :body}
                    @(handler
                       {:body
                        {:fhir/type :fhir/Bundle
                         :type (type/code type)
                         :entry
                         [{:fhir/type :fhir.Bundle/entry
                           :resource
                           {:fhir/type :fhir/Patient}
                           :request
                           {:fhir/type :fhir.Bundle.entry/request
                            :method #fhir/code "POST"
                            :url #fhir/uri "Patient"
                            :ifNoneExist "identifier=150015"}}]}})]

                (testing "the new patient is returned"
                  (testing "response status"
                    (is (= 200 status)))

                  (testing "bundle"
                    (given body
                      :fhir/type := :fhir/Bundle
                      :id := "AAAAAAAAAAAAAAAA"
                      :type := (type/code (str type "-response"))))

                  (testing "entry resource"
                    (is (nil? resource)))

                  (testing "entry response"
                    (given response
                      :status := "201"
                      :location := #fhir/uri "base-url-115515/Patient/AAAAAAAAAAAAAAAA/_history/2"
                      :etag := "W/\"2\""
                      :lastModified := Instant/EPOCH))))))

          (testing "with representation return preference"
            (with-handler [handler]
              [[[:put {:fhir/type :fhir/Patient :id "0"
                       :identifier
                       [#fhir/Identifier {:value "095156"}]}]]]

              (let [{:keys [status body]
                     {[{:keys [resource response]}] :entry} :body}
                    @(handler
                       {:headers {"prefer" "return=representation"}
                        :body
                        {:fhir/type :fhir/Bundle
                         :type (type/code type)
                         :entry
                         [{:fhir/type :fhir.Bundle/entry
                           :resource
                           {:fhir/type :fhir/Patient}
                           :request
                           {:fhir/type :fhir.Bundle.entry/request
                            :method #fhir/code "POST"
                            :url #fhir/uri "Patient"
                            :ifNoneExist "identifier=150015"}}]}})]

                (testing "the new patient is returned"
                  (testing "response status"
                    (is (= 200 status)))

                  (testing "bundle"
                    (given body
                      :fhir/type := :fhir/Bundle
                      :id := "AAAAAAAAAAAAAAAA"
                      :type := (type/code (str type "-response"))))

                  (testing "entry resource"
                    (given resource
                      :fhir/type := :fhir/Patient
                      :id := "AAAAAAAAAAAAAAAA"
                      [:meta :versionId] := #fhir/id"2"
                      [:meta :lastUpdated] := Instant/EPOCH))

                  (testing "entry response"
                    (given response
                      :status := "201"
                      :location := #fhir/uri "base-url-115515/Patient/AAAAAAAAAAAAAAAA/_history/2"
                      :etag := "W/\"2\""
                      :lastModified := Instant/EPOCH)))))))

        (testing "with matching patient"
          (testing "without return preference"
            (with-handler [handler]
              [[[:put {:fhir/type :fhir/Patient :id "0"
                       :identifier
                       [#fhir/Identifier {:value "095156"}]}]]]

              (let [{:keys [status body]
                     {[{:keys [resource response]}] :entry} :body}
                    @(handler
                       {:body
                        {:fhir/type :fhir/Bundle
                         :type (type/code type)
                         :entry
                         [{:fhir/type :fhir.Bundle/entry
                           :resource
                           {:fhir/type :fhir/Patient}
                           :request
                           {:fhir/type :fhir.Bundle.entry/request
                            :method #fhir/code "POST"
                            :url #fhir/uri "Patient"
                            :ifNoneExist "identifier=095156"}}]}})]

                (testing "the existing patient is returned"
                  (testing "response status"
                    (is (= 200 status)))

                  (testing "bundle"
                    (given body
                      :fhir/type := :fhir/Bundle
                      :id := "AAAAAAAAAAAAAAAA"
                      :type := (type/code (str type "-response"))))

                  (testing "entry resource"
                    (is (nil? resource)))

                  (testing "entry response"
                    (given response
                      :status := "200"
                      :location := nil
                      :etag := "W/\"1\""
                      :lastModified := Instant/EPOCH))))))

          (testing "with representation return preference"
            (with-handler [handler]
              [[[:put {:fhir/type :fhir/Patient :id "0"
                       :identifier
                       [#fhir/Identifier {:value "095156"}]}]]]

              (let [{:keys [status body]
                     {[{:keys [resource response]}] :entry} :body}
                    @(handler
                       {:headers {"prefer" "return=representation"}
                        :body
                        {:fhir/type :fhir/Bundle
                         :type (type/code type)
                         :entry
                         [{:fhir/type :fhir.Bundle/entry
                           :resource
                           {:fhir/type :fhir/Patient}
                           :request
                           {:fhir/type :fhir.Bundle.entry/request
                            :method #fhir/code "POST"
                            :url #fhir/uri "Patient"
                            :ifNoneExist "identifier=095156"}}]}})]

                (testing "the existing patient is returned"
                  (testing "response status"
                    (is (= 200 status)))

                  (testing "bundle"
                    (given body
                      :fhir/type := :fhir/Bundle
                      :id := "AAAAAAAAAAAAAAAA"
                      :type := (type/code (str type "-response"))))

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
                      :lastModified := Instant/EPOCH))))))))

      (testing "and delete interaction"
        (let [entries
              [{:fhir/type :fhir.Bundle/entry
                :request
                {:fhir/type :fhir.Bundle.entry/request
                 :method #fhir/code "DELETE"
                 :url #fhir/uri "Patient/0"}}]]

          (testing "without return preference"
            (with-handler [handler]
              [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

              (let [{:keys [status body]
                     {[{:keys [resource response]}] :entry} :body}
                    @(handler
                       {:body
                        {:fhir/type :fhir/Bundle
                         :type (type/code type)
                         :entry entries}})]

                (testing "response status"
                  (is (= 200 status)))

                (testing "bundle"
                  (given body
                    :fhir/type := :fhir/Bundle
                    :id := "AAAAAAAAAAAAAAAA"
                    :type := (type/code (str type "-response"))))

                (testing "entry resource"
                  (is (nil? resource)))

                (testing "entry response"
                  (given response
                    :status := "204"
                    :etag := "W/\"2\""
                    :lastModified := Instant/EPOCH)))))))

      (testing "and read interaction"
        (testing "returns Not-Found on non-existing resource"
          (with-handler [handler]
            []
            (let [{:keys [status]
                   {[{:keys [response]}] :entry :as body} :body}
                  @(handler
                     {:body
                      {:fhir/type :fhir/Bundle
                       :type (type/code type)
                       :entry
                       [{:fhir/type :fhir.Bundle/entry
                         :request
                         {:fhir/type :fhir.Bundle.entry/request
                          :method #fhir/code "GET"
                          :url #fhir/uri "Patient/0"}}]}})]

              (testing "response status"
                (is (= 200 status)))

              (testing "bundle"
                (given body
                  :fhir/type := :fhir/Bundle
                  :id := "AAAAAAAAAAAAAAAA"
                  :type := (type/code (str type "-response"))))

              (testing "returns error"
                (testing "with status"
                  (is (= "404" (:status response))))

                (testing "with outcome"
                  (given (:outcome response)
                    :fhir/type := :fhir/OperationOutcome
                    [:issue 0 :severity] := #fhir/code "error"
                    [:issue 0 :code] := #fhir/code "not-found"
                    [:issue 0 :diagnostics] := "Resource `Patient/0` was not found."
                    [:issue 0 :expression 0] := "Bundle.entry[0]"))))))

        (testing "returns existing resource"
          (with-handler [handler]
            [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

            (let [{:keys [status]
                   {[{:keys [resource response]}] :entry :as body} :body}
                  @(handler
                     {:body
                      {:fhir/type :fhir/Bundle
                       :type (type/code type)
                       :entry
                       [{:fhir/type :fhir.Bundle/entry
                         :request
                         {:fhir/type :fhir.Bundle.entry/request
                          :method #fhir/code "GET"
                          :url #fhir/uri "Patient/0"}}]}})]

              (testing "response status"
                (is (= 200 status)))

              (testing "bundle"
                (given body
                  :fhir/type := :fhir/Bundle
                  :id := "AAAAAAAAAAAAAAAA"
                  :type := (type/code (str type "-response"))))

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
                  :lastModified := Instant/EPOCH))))))))

  (testing "On transaction bundle"
    (testing "on missing request"
      (with-handler [handler]
        []
        (let [{:keys [status body]}
              @(handler
                 {:body
                  {:fhir/type :fhir/Bundle
                   :type #fhir/code "transaction"
                   :entry
                   [{}]}})]

          (testing "returns error"
            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "value"
              [:issue 0 :diagnostics] := "Missing request."
              [:issue 0 :expression 0] := "Bundle.entry[0]")))))

    (testing "on missing request url"
      (with-handler [handler]
        []
        (let [{:keys [status body]}
              @(handler
                 {:body
                  {:fhir/type :fhir/Bundle
                   :type #fhir/code "transaction"
                   :entry
                   [{:fhir/type :fhir.Bundle/entry
                     :request {}}]}})]

          (testing "returns error"
            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "value"
              [:issue 0 :diagnostics] := "Missing url."
              [:issue 0 :expression 0] := "Bundle.entry[0].request")))))

    (testing "on missing request method"
      (with-handler [handler]
        []
        (let [{:keys [status body]}
              @(handler
                 {:body
                  {:fhir/type :fhir/Bundle
                   :type #fhir/code "transaction"
                   :entry
                   [{:fhir/type :fhir.Bundle/entry
                     :request
                     {:fhir/type :fhir.Bundle.entry/request
                      :url #fhir/uri "Patient/0"}}]}})]

          (testing "returns error"
            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "value"
              [:issue 0 :diagnostics] := "Missing method."
              [:issue 0 :expression 0] := "Bundle.entry[0].request")))))

    (testing "on unknown method"
      (with-handler [handler]
        []
        (let [{:keys [status body]}
              @(handler
                 {:body
                  {:fhir/type :fhir/Bundle
                   :type #fhir/code "transaction"
                   :entry
                   [{:fhir/type :fhir.Bundle/entry
                     :request
                     {:fhir/type :fhir.Bundle.entry/request
                      :method #fhir/code "FOO"
                      :url #fhir/uri "Patient/0"}}]}})]

          (testing "returns error"
            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "value"
              [:issue 0 :diagnostics] := "Unknown method `FOO`."
              [:issue 0 :expression 0] := "Bundle.entry[0].request.method")))))

    (testing "on unsupported method"
      (with-handler [handler]
        []
        (let [{:keys [status body]}
              @(handler
                 {:body
                  {:fhir/type :fhir/Bundle
                   :type #fhir/code "transaction"
                   :entry
                   [{:fhir/type :fhir.Bundle/entry
                     :request
                     {:fhir/type :fhir.Bundle.entry/request
                      :method #fhir/code "PATCH"
                      :url #fhir/uri "Patient/0"}}]}})]

          (testing "returns error"
            (is (= 422 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "not-supported"
              [:issue 0 :diagnostics] := "Unsupported method `PATCH`."
              [:issue 0 :expression 0] := "Bundle.entry[0].request.method")))))

    (testing "and update interaction"
      (testing "on missing type in URL"
        (with-handler [handler]
          []
          (let [{:keys [status body]}
                @(handler
                   {:body
                    {:fhir/type :fhir/Bundle
                     :type #fhir/code "transaction"
                     :entry
                     [{:fhir/type :fhir.Bundle/entry
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code "PUT"
                        :url #fhir/uri ""}}]}})]

            (testing "returns error"
              (is (= 400 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "value"
                [:issue 0 :diagnostics] := "Can't parse type from `entry.request.url` ``."
                [:issue 0 :expression 0] := "Bundle.entry[0].request.url")))))

      (testing "on unknown type"
        (with-handler [handler]
          []
          (let [{:keys [status body]}
                @(handler
                   {:body
                    {:fhir/type :fhir/Bundle
                     :type #fhir/code "transaction"
                     :entry
                     [{:fhir/type :fhir.Bundle/entry
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code "PUT"
                        :url #fhir/uri "Foo/0"}}]}})]

            (testing "returns error"
              (is (= 400 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "value"
                [:issue 0 :diagnostics] := "Unknown type `Foo` in bundle entry URL `Foo/0`."
                [:issue 0 :expression 0] := "Bundle.entry[0].request.url")))))

      (testing "on missing resource type"
        (with-handler [handler]
          []
          (let [{:keys [status body]}
                @(handler
                   {:body
                    {:fhir/type :fhir/Bundle
                     :type #fhir/code "transaction"
                     :entry
                     [{:fhir/type :fhir.Bundle/entry
                       :resource
                       {}
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code "PUT"
                        :url #fhir/uri "Patient/0"}}]}})]

            (testing "returns error "
              (is (= 400 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "required"
                [:issue 0 :diagnostics] := "Resource type is missing."
                [:issue 0 :expression 0] := "Bundle.entry[0].resource.resourceType")))))

      (testing "on type mismatch"
        (with-handler [handler]
          []
          (let [{:keys [status body]}
                @(handler
                   {:body
                    {:fhir/type :fhir/Bundle
                     :type #fhir/code "transaction"
                     :entry
                     [{:fhir/type :fhir.Bundle/entry
                       :resource
                       {:fhir/type :fhir/Observation}
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code "PUT"
                        :url #fhir/uri "Patient/0"}}]}})]

            (testing "returns error "
              (is (= 400 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "invariant"
                [:issue 0 :details :coding 0 :system] := operation-outcome
                [:issue 0 :details :coding 0 :code] := #fhir/code "MSG_RESOURCE_TYPE_MISMATCH"
                [:issue 0 :diagnostics] := "Type mismatch between resource type `Observation` and URL `Patient/0`."
                [:issue 0 :expression 0] := "Bundle.entry[0].request.url"
                [:issue 0 :expression 1] := "Bundle.entry[0].resource.resourceType")))))

      (testing "on missing ID"
        (with-handler [handler]
          []
          (let [{:keys [status body]}
                @(handler
                   {:body
                    {:fhir/type :fhir/Bundle
                     :type #fhir/code "transaction"
                     :entry
                     [{:fhir/type :fhir.Bundle/entry
                       :resource
                       {:fhir/type :fhir/Patient}
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code "PUT"
                        :url #fhir/uri "Patient/0"}}]}})]

            (testing "returns error "
              (is (= 400 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "required"
                [:issue 0 :details :coding 0 :system] := operation-outcome
                [:issue 0 :details :coding 0 :code] := #fhir/code "MSG_RESOURCE_ID_MISSING"
                [:issue 0 :diagnostics] := "Resource id is missing."
                [:issue 0 :expression 0] := "Bundle.entry[0].resource.id")))))

      (testing "on missing ID in URL"
        (with-handler [handler]
          []
          (let [{:keys [status body]}
                @(handler
                   {:body
                    {:fhir/type :fhir/Bundle
                     :type #fhir/code "transaction"
                     :entry
                     [{:fhir/type :fhir.Bundle/entry
                       :resource
                       {:fhir/type :fhir/Patient :id "0"}
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code "PUT"
                        :url #fhir/uri "Patient"}}]}})]

            (testing "returns error"
              (is (= 400 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "value"
                [:issue 0 :diagnostics] := "Can't parse id from URL `Patient`."
                [:issue 0 :expression 0] := "Bundle.entry[0].request.url")))))

      (testing "on invalid ID"
        (with-handler [handler]
          []
          (let [{:keys [status body]}
                @(handler
                   {:body
                    {:fhir/type :fhir/Bundle
                     :type #fhir/code "transaction"
                     :entry
                     [{:fhir/type :fhir.Bundle/entry
                       :resource
                       {:fhir/type :fhir/Patient
                        :id "A_B"}
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code "PUT"
                        :url #fhir/uri "Patient/0"}}]}})]

            (testing "returns error"
              (is (= 400 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "value"
                [:issue 0 :details :coding 0 :system] := operation-outcome
                [:issue 0 :details :coding 0 :code] := #fhir/code "MSG_ID_INVALID"
                [:issue 0 :diagnostics] := "Resource id `A_B` is invalid."
                [:issue 0 :expression 0] := "Bundle.entry[0].resource.id")))))

      (testing "on ID mismatch"
        (with-handler [handler]
          []
          (let [{:keys [status body]}
                @(handler
                   {:body
                    {:fhir/type :fhir/Bundle
                     :type #fhir/code "transaction"
                     :entry
                     [{:fhir/type :fhir.Bundle/entry
                       :resource
                       {:fhir/type :fhir/Patient
                        :id "1"}
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code "PUT"
                        :url #fhir/uri "Patient/0"}}]}})]

            (testing "returns error"
              (is (= 400 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "invariant"
                [:issue 0 :details :coding 0 :system] := operation-outcome
                [:issue 0 :details :coding 0 :code] := #fhir/code "MSG_RESOURCE_ID_MISMATCH"
                [:issue 0 :diagnostics] := "Id mismatch between resource id `1` and URL `Patient/0`."
                [:issue 0 :expression 0] := "Bundle.entry[0].request.url"
                [:issue 0 :expression 1] := "Bundle.entry[0].resource.id")))))

      (testing "on optimistic locking failure"
        (with-handler [handler]
          [[[:create {:fhir/type :fhir/Patient :id "0"}]]
           [[:put {:fhir/type :fhir/Patient :id "0"}]]]

          (let [{:keys [status body]}
                @(handler
                   {:body
                    {:fhir/type :fhir/Bundle
                     :type #fhir/code "transaction"
                     :entry
                     [{:fhir/type :fhir.Bundle/entry
                       :resource
                       {:fhir/type :fhir/Patient
                        :id "0"}
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code "PUT"
                        :url #fhir/uri "Patient/0"
                        :ifMatch "W/\"1\""}}]}})]

            (testing "returns error"
              (is (= 412 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "conflict"
                [:issue 0 :diagnostics] := "Precondition `W/\"1\"` failed on `Patient/0`.")))))

      (testing "on duplicate resources"
        (with-handler [handler]
          []
          (let [{:keys [status body]}
                @(handler
                   {:body
                    {:fhir/type :fhir/Bundle
                     :type #fhir/code "transaction"
                     :entry
                     [{:fhir/type :fhir.Bundle/entry
                       :resource
                       {:fhir/type :fhir/Patient
                        :id "0"}
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code "PUT"
                        :url #fhir/uri "Patient/0"}}
                      {:fhir/type :fhir.Bundle/entry
                       :resource
                       {:fhir/type :fhir/Patient
                        :id "0"}
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code "PUT"
                        :url #fhir/uri "Patient/0"}}]}})]

            (testing "returns error"
              (is (= 400 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "invariant"
                [:issue 0 :diagnostics] := "Duplicate resource `Patient/0`.")))))

      (testing "on violated referential integrity"
        (with-handler [handler]
          []
          (let [{:keys [status body]}
                @(handler
                   {:body
                    {:fhir/type :fhir/Bundle
                     :type #fhir/code "transaction"
                     :entry
                     [{:fhir/type :fhir.Bundle/entry
                       :resource
                       {:fhir/type :fhir/Observation :id "0"
                        :subject
                        #fhir/Reference
                            {:reference "Patient/0"}}
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code "POST"
                        :url #fhir/uri "Observation"}}]}})]

            (testing "returns error"
              (is (= 409 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "conflict"
                [:issue 0 :diagnostics] := "Referential integrity violated. Resource `Patient/0` doesn't exist."))))))

    (testing "and create interaction"
      (testing "creates sequential identifiers"
        (with-handler [handler]
          []
          (let [{:keys [body]}
                @(handler
                   {:headers {"prefer" "return=representation"}
                    :body
                    {:fhir/type :fhir/Bundle
                     :type #fhir/code "transaction"
                     :entry
                     [{:resource
                       {:fhir/type :fhir/Patient}
                       :request
                       {:method #fhir/code "POST"
                        :url #fhir/uri "Patient"}}
                      {:resource
                       {:fhir/type :fhir/Patient}
                       :request
                       {:method #fhir/code "POST"
                        :url #fhir/uri "Patient"}}]}})]
            (given body
              [:entry 0 :resource :id] := "AAAAAAAAAAAAAAAA"
              [:entry 1 :resource :id] := "AAAAAAAAAAAAAAAB")))))

    (testing "and conditional create interaction"
      (testing "on multiple matching patients"
        (with-handler [handler]
          [[[:put {:fhir/type :fhir/Patient :id "0"
                   :birthDate #fhir/date"2020"}]
            [:put {:fhir/type :fhir/Patient :id "1"
                   :birthDate #fhir/date"2020"}]]]

          (let [{:keys [status body]}
                @(handler
                   {:body
                    {:fhir/type :fhir/Bundle
                     :type #fhir/code "transaction"
                     :entry
                     [{:fhir/type :fhir.Bundle/entry
                       :resource
                       {:fhir/type :fhir/Patient}
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code "POST"
                        :url #fhir/uri "Patient"
                        :ifNoneExist "birthdate=2020"}}]}})]

            (testing "returns error"
              (is (= 412 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "conflict"
                [:issue 0 :diagnostics] := "Conditional create of a Patient with query `birthdate=2020` failed because at least the two matches `Patient/0/_history/1` and `Patient/1/_history/1` were found.")))))))

  (testing "On batch bundle"
    (testing "on missing request"
      (with-handler [handler]
        []
        (let [{:keys [status] {[{:keys [response]}] :entry} :body}
              @(handler
                 {:body
                  {:fhir/type :fhir/Bundle
                   :type #fhir/code "batch"
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
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "value"
                [:issue 0 :diagnostics] := "Missing request."
                [:issue 0 :expression 0] := "Bundle.entry[0]"))))))

    (testing "on missing request url"
      (with-handler [handler]
        []
        (let [{:keys [status] {[{:keys [response]}] :entry} :body}
              @(handler
                 {:body
                  {:fhir/type :fhir/Bundle
                   :type #fhir/code "batch"
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
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "value"
                [:issue 0 :diagnostics] := "Missing url."
                [:issue 0 :expression 0] := "Bundle.entry[0].request"))))))

    (testing "on missing request method"
      (with-handler [handler]
        []
        (let [{:keys [status] {[{:keys [response]}] :entry} :body}
              @(handler
                 {:body
                  {:fhir/type :fhir/Bundle
                   :type #fhir/code "batch"
                   :entry
                   [{:fhir/type :fhir.Bundle/entry
                     :request
                     {:fhir/type :fhir.Bundle.entry/request
                      :url #fhir/uri "Patient/0"}}]}})]

          (testing "response status"
            (is (= 200 status)))

          (testing "returns error"
            (testing "with status"
              (is (= "400" (:status response))))

            (testing "with outcome"
              (given (:outcome response)
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "value"
                [:issue 0 :diagnostics] := "Missing method."
                [:issue 0 :expression 0] := "Bundle.entry[0].request"))))))

    (testing "on unknown method"
      (with-handler [handler]
        []
        (let [{:keys [status] {[{:keys [response]}] :entry} :body}
              @(handler
                 {:body
                  {:fhir/type :fhir/Bundle
                   :type #fhir/code "batch"
                   :entry
                   [{:fhir/type :fhir.Bundle/entry
                     :request
                     {:fhir/type :fhir.Bundle.entry/request
                      :method #fhir/code "FOO"
                      :url #fhir/uri "Patient/0"}}]}})]

          (testing "response status"
            (is (= 200 status)))

          (testing "returns error"
            (testing "with status"
              (is (= "400" (:status response))))

            (testing "with outcome"
              (given (:outcome response)
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "value"
                [:issue 0 :diagnostics] := "Unknown method `FOO`."
                [:issue 0 :expression 0] := "Bundle.entry[0].request.method"))))))

    (testing "on unsupported method"
      (with-handler [handler]
        []
        (let [{:keys [status] {[{:keys [response]}] :entry} :body}
              @(handler
                 {:body
                  {:fhir/type :fhir/Bundle
                   :type #fhir/code "batch"
                   :entry
                   [{:fhir/type :fhir.Bundle/entry
                     :request
                     {:fhir/type :fhir.Bundle.entry/request
                      :method #fhir/code "PATCH"
                      :url #fhir/uri "Patient/0"}}]}})]

          (testing "response status"
            (is (= 200 status)))

          (testing "returns error"
            (testing "with status"
              (is (= "422" (:status response))))

            (testing "with outcome"
              (given (:outcome response)
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "not-supported"
                [:issue 0 :diagnostics] := "Unsupported method `PATCH`."
                [:issue 0 :expression 0] := "Bundle.entry[0].request.method"))))))

    (testing "and update interaction"
      (testing "on invalid type-level URL"
        (with-handler [handler]
          []
          (let [{:keys [status] {[{:keys [response]}] :entry} :body}
                @(handler
                   {:body
                    {:fhir/type :fhir/Bundle
                     :type #fhir/code "batch"
                     :entry
                     [{:fhir/type :fhir.Bundle/entry
                       :resource
                       {:fhir/type :fhir/Patient}
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code "PUT"
                        :url #fhir/uri "Patient"}}]}})]

            (testing "response status"
              (is (= 200 status)))

            (testing "returns error"
              (testing "with status"
                (is (= "400" (:status response))))

              (testing "with outcome"
                (given (:outcome response)
                  :fhir/type := :fhir/OperationOutcome
                  [:issue 0 :severity] := #fhir/code "error"
                  [:issue 0 :code] := #fhir/code "value"
                  [:issue 0 :diagnostics] := "Can't parse id from URL `Patient`."
                  [:issue 0 :expression 0] := "Bundle.entry[0].request.url"))))))

      (testing "on optimistic locking failure"
        (with-handler [handler]
          [[[:create {:fhir/type :fhir/Patient :id "0"}]]
           [[:put {:fhir/type :fhir/Patient :id "0"}]]]

          (let [{:keys [status] {[{:keys [response]}] :entry} :body}
                @(handler
                   {:body
                    {:fhir/type :fhir/Bundle
                     :type #fhir/code "batch"
                     :entry
                     [{:fhir/type :fhir.Bundle/entry
                       :resource
                       {:fhir/type :fhir/Patient
                        :id "0"}
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code "PUT"
                        :url #fhir/uri "Patient/0"
                        :ifMatch "W/\"1\""}}]}})]

            (testing "response status"
              (is (= 200 status)))

            (testing "returns error"
              (testing "with status"
                (is (= "412" (:status response))))

              (testing "with outcome"
                (given (:outcome response)
                  :fhir/type := :fhir/OperationOutcome
                  [:issue 0 :severity] := #fhir/code "error"
                  [:issue 0 :code] := #fhir/code "conflict"
                  [:issue 0 :diagnostics] := "Precondition `W/\"1\"` failed on `Patient/0`."
                  [:issue 0 :expression 0] := "Bundle.entry[0]"))))))

      (testing "without return preference"
        (with-handler [handler]
          []
          (let [{:keys [status] {[{:keys [resource response]}] :entry} :body}
                @(handler
                   {:body
                    {:fhir/type :fhir/Bundle
                     :type #fhir/code "batch"
                     :entry
                     [{:fhir/type :fhir.Bundle/entry
                       :resource
                       {:fhir/type :fhir/Patient :id "0"}
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code "PUT"
                        :url #fhir/uri "Patient/0"}}]}})]

            (testing "response status"
              (is (= 200 status)))

            (testing "entry resource"
              (is (nil? resource)))

            (testing "entry response"
              (given response
                :status := "201"
                :location := #fhir/uri "base-url-115515/Patient/0/_history/1"
                :etag := "W/\"1\""
                :lastModified := Instant/EPOCH))))

        (testing "leading slash in URL is removed"
          (with-handler [handler]
            []
            (let [{:keys [status] {[{:keys [resource response]}] :entry} :body}
                  @(handler
                     {:body
                      {:fhir/type :fhir/Bundle
                       :type #fhir/code "batch"
                       :entry
                       [{:fhir/type :fhir.Bundle/entry
                         :resource
                         {:fhir/type :fhir/Patient :id "0"}
                         :request
                         {:fhir/type :fhir.Bundle.entry/request
                          :method #fhir/code "PUT"
                          :url #fhir/uri "/Patient/0"}}]}})]

              (testing "response status"
                (is (= 200 status)))

              (testing "entry resource"
                (is (nil? resource)))

              (testing "entry response"
                (given response
                  :status := "201"
                  :location := #fhir/uri "base-url-115515/Patient/0/_history/1"
                  :etag := "W/\"1\""
                  :lastModified := Instant/EPOCH))))))

      (testing "with representation return preference"
        (with-handler [handler]
          []
          (let [{:keys [status] {[{:keys [resource response]}] :entry} :body}
                @(handler
                   {:headers {"prefer" "return=representation"}
                    :body
                    {:fhir/type :fhir/Bundle
                     :type #fhir/code "batch"
                     :entry
                     [{:fhir/type :fhir.Bundle/entry
                       :resource
                       {:fhir/type :fhir/Patient :id "0"}
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code "PUT"
                        :url #fhir/uri "Patient/0"}}]}})]

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
                :location := #fhir/uri "base-url-115515/Patient/0/_history/1"
                :etag := "W/\"1\""
                :lastModified := Instant/EPOCH))))))

    (testing "and create interaction"
      (testing "on not-found type-level URL"
        (with-handler [handler]
          []
          (let [{:keys [status] {[{:keys [response]}] :entry} :body}
                @(handler
                   {:body
                    {:fhir/type :fhir/Bundle
                     :type #fhir/code "batch"
                     :entry
                     [{:fhir/type :fhir.Bundle/entry
                       :resource
                       {:fhir/type :fhir/Patient}
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code "POST"
                        :url #fhir/uri "Foo"}}]}})]

            (testing "response status"
              (is (= 200 status)))

            (testing "returns error"
              (testing "with status"
                (is (= "400" (:status response))))

              (testing "with outcome"
                (given (:outcome response)
                  :fhir/type := :fhir/OperationOutcome
                  [:issue 0 :severity] := #fhir/code "error"
                  [:issue 0 :code] := #fhir/code "value"
                  [:issue 0 :diagnostics] := "Unknown type `Foo` in bundle entry URL `Foo`."
                  [:issue 0 :expression 0] := "Bundle.entry[0].request.url"))))))

      (testing "on invalid instance-level URL"
        (with-handler [handler]
          []
          (let [{:keys [status] {[{:keys [response]}] :entry} :body}
                @(handler
                   {:body
                    {:fhir/type :fhir/Bundle
                     :type #fhir/code "batch"
                     :entry
                     [{:fhir/type :fhir.Bundle/entry
                       :resource
                       {:fhir/type :fhir/Patient}
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code "POST"
                        :url #fhir/uri "Patient/0"}}]}})]

            (testing "response status"
              (is (= 200 status)))

            (testing "returns error"
              (testing "with status"
                (is (= "405" (:status response))))

              (testing "with outcome"
                (given (:outcome response)
                  :fhir/type := :fhir/OperationOutcome
                  [:issue 0 :severity] := #fhir/code "error"
                  [:issue 0 :code] := #fhir/code "processing"
                  [:issue 0 :diagnostics] := "Method POST not allowed on `/Patient/0` endpoint."
                  [:issue 0 :expression 0] := "Bundle.entry[0]"))))))

      (testing "on violated referential integrity"
        (with-handler [handler]
          []
          (let [{:keys [status] {[{:keys [response]}] :entry} :body}
                @(handler
                   {:body
                    {:fhir/type :fhir/Bundle
                     :type #fhir/code "batch"
                     :entry
                     [{:fhir/type :fhir.Bundle/entry
                       :resource
                       {:fhir/type :fhir/Observation
                        :subject #fhir/Reference {:reference "Patient/0"}}
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code "POST"
                        :url #fhir/uri "Observation"}}]}})]

            (testing "response status"
              (is (= 200 status)))

            (testing "returns error"
              (testing "with status"
                (is (= "409" (:status response))))

              (testing "with outcome"
                (given (:outcome response)
                  :fhir/type := :fhir/OperationOutcome
                  [:issue 0 :severity] := #fhir/code "error"
                  [:issue 0 :code] := #fhir/code "conflict"
                  [:issue 0 :diagnostics] := "Referential integrity violated. Resource `Patient/0` doesn't exist."
                  [:issue 0 :expression 0] := "Bundle.entry[0]")))))))

    (testing "and conditional create interaction"
      (testing "on multiple matching patients"
        (with-handler [handler]
          [[[:put {:fhir/type :fhir/Patient :id "0"
                   :birthDate #fhir/date"2020"}]
            [:put {:fhir/type :fhir/Patient :id "1"
                   :birthDate #fhir/date"2020"}]]]

          (let [{:keys [status] {[{:keys [response]}] :entry} :body}
                @(handler
                   {:body
                    {:fhir/type :fhir/Bundle
                     :type #fhir/code "batch"
                     :entry
                     [{:fhir/type :fhir.Bundle/entry
                       :resource
                       {:fhir/type :fhir/Patient}
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code "POST"
                        :url #fhir/uri "Patient"
                        :ifNoneExist "birthdate=2020"}}]}})]

            (testing "response status"
              (is (= 200 status)))

            (testing "returns error"
              (testing "with status"
                (is (= "412" (:status response))))

              (testing "with outcome"
                (given (:outcome response)
                  :fhir/type := :fhir/OperationOutcome
                  [:issue 0 :severity] := #fhir/code "error"
                  [:issue 0 :code] := #fhir/code "conflict"
                  [:issue 0 :diagnostics] := "Conditional create of a Patient with query `birthdate=2020` failed because at least the two matches `Patient/0/_history/1` and `Patient/1/_history/1` were found."
                  [:issue 0 :expression 0] := "Bundle.entry[0]")))))))

    (testing "and search-type interaction"
      (with-handler [handler]
        [[[:create {:fhir/type :fhir/Patient :id "0"}]
          [:create {:fhir/type :fhir/Patient :id "1"}]]]

        (let [{:keys [status] {[{:keys [resource response]}] :entry} :body}
              @(handler
                 {:body
                  {:fhir/type :fhir/Bundle
                   :type #fhir/code "batch"
                   :entry
                   [{:fhir/type :fhir.Bundle/entry
                     :request
                     {:fhir/type :fhir.Bundle.entry/request
                      :method #fhir/code "GET"
                      :url #fhir/uri "Patient?_id=0"}}]}})]

          (testing "response status"
            (is (= 200 status)))

          (testing "entry resource"
            (given resource
              :fhir/type := :fhir/Bundle
              :type := #fhir/code "searchset"
              [:entry count] := 1
              [:entry 0 :resource :fhir/type] := :fhir/Patient
              [:entry 0 :resource :id] := "0"
              [:entry 0 :resource :meta :versionId] := #fhir/id"1"
              [:entry 0 :resource :meta :lastUpdated] := Instant/EPOCH))

          (testing "entry response"
            (given response
              :status := "200"))))

      (testing "with _summary=count"
        (with-handler [handler]
          [[[:create {:fhir/type :fhir/Patient :id "0"}]
            [:create {:fhir/type :fhir/Patient :id "1"}]]]

          (let [{:keys [status] {[{:keys [resource response]}] :entry} :body}
                @(handler
                   {:body
                    {:fhir/type :fhir/Bundle
                     :type #fhir/code "batch"
                     :entry
                     [{:fhir/type :fhir.Bundle/entry
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code "GET"
                        :url #fhir/uri "Patient?_summary=count"}}]}})]

            (testing "response status"
              (is (= 200 status)))

            (testing "entry resource"
              (given resource
                :fhir/type := :fhir/Bundle
                :type := #fhir/code "searchset"
                :total := #fhir/unsignedInt 2
                :entry :? empty?))

            (testing "entry response"
              (given response
                :status := "200")))))

      (testing "with date-time search param value"
        (with-handler [handler]
          [[[:create
             {:fhir/type :fhir/Observation :id "0"
              :effective #fhir/dateTime"2021-12-08T00:00:00+01:00"}]
            [:create
             {:fhir/type :fhir/Observation :id "1"
              :effective #fhir/dateTime"2021-12-09T00:00:00+01:00"}]]]

          (let [{:keys [status] {[{:keys [resource response]}] :entry} :body}
                @(handler
                   {:body
                    {:fhir/type :fhir/Bundle
                     :type #fhir/code "batch"
                     :entry
                     [{:fhir/type :fhir.Bundle/entry
                       :request
                       {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code "GET"
                        :url #fhir/uri "Observation?date=lt2021-12-08T10:00:00%2B01:00"}}]}})]

            (testing "response status"
              (is (= 200 status)))

            (testing "entry resource"
              (given resource
                :fhir/type := :fhir/Bundle
                :type := #fhir/code "searchset"
                :total := #fhir/unsignedInt 1
                [:entry count] := 1))

            (testing "entry response"
              (given response
                :status := "200")))

          (testing "without encoding of the plus in the timezone"
            (let [{:keys [status] {[{:keys [response]}] :entry} :body}
                  @(handler
                     {:body
                      {:fhir/type :fhir/Bundle
                       :type #fhir/code "batch"
                       :entry
                       [{:fhir/type :fhir.Bundle/entry
                         :request
                         {:fhir/type :fhir.Bundle.entry/request
                          :method #fhir/code "GET"
                          :url #fhir/uri "Observation?date=lt2021-12-09T00:00:00+01:00"}}]}})]

              (testing "response status"
                (is (= 200 status)))

              (testing "returns error"
                (testing "with status"
                  (is (= "400" (:status response))))

                (testing "with outcome"
                  (given (:outcome response)
                    :fhir/type := :fhir/OperationOutcome
                    [:issue 0 :severity] := #fhir/code "error"
                    [:issue 0 :code] := #fhir/code "invalid"
                    [:issue 0 :diagnostics] := "Invalid date-time value `2021-12-09T00:00:00 01:00` in search parameter `date`."
                    [:issue 0 :expression 0] := "Bundle.entry[0]"))))))))))
