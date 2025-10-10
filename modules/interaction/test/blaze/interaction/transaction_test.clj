(ns blaze.interaction.transaction-test
  "Specifications relevant for the FHIR batch/transaction interaction:

  https://www.hl7.org/fhir/http.html#transaction
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.db.kv :as kv]
   [blaze.db.kv.protocols :as kv-p]
   [blaze.db.node :as node]
   [blaze.db.resource-store :as rs]
   [blaze.db.spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type-spec]
   [blaze.fhir.util :as fu]
   [blaze.handler.util :as handler-util]
   [blaze.interaction.conditional-delete-type]
   [blaze.interaction.create]
   [blaze.interaction.delete]
   [blaze.interaction.read]
   [blaze.interaction.search-type]
   [blaze.interaction.search.util :as search-util]
   [blaze.interaction.test-util :refer [wrap-error]]
   [blaze.interaction.transaction]
   [blaze.interaction.update]
   [blaze.interaction.util-spec]
   [blaze.middleware.fhir.db :as db]
   [blaze.middleware.fhir.db-spec]
   [blaze.module-spec]
   [blaze.module.test-util :refer [given-failed-system]]
   [blaze.page-store-spec]
   [blaze.page-store.local]
   [blaze.test-util :as tu]
   [blaze.util-spec]
   [blaze.util.clauses-spec]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [reitit.core :as reitit]
   [reitit.ring]
   [ring.middleware.params :refer [wrap-params]]
   [ring.util.response :as ring]
   [taoensso.timbre :as log])
  (:import
   [java.time Instant]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def operation-outcome
  #fhir/uri "http://terminology.hl7.org/CodeSystem/operation-outcome")

(def base-url "base-url-115515")

(def ^:private wrap-db
  {:name :db
   :wrap db/wrap-db})

(defmethod ig/init-key ::router
  [_ {:keys [node create-handler search-type-handler
             read-handler delete-handler update-handler
             conditional-delete-type-handler batch?]}]
  (reitit.ring/router
   [["/metadata"
     {:get
      (fn [_]
        (ac/completed-future
         (ring/response {:fhir/type :fhir/CapabilityStatement})))}]
    ["/Observation"
     {:name :Observation/type
      :conflicting true
      :fhir.resource/type "Observation"
      :get {:middleware [[wrap-db node 100]]
            :handler (wrap-params search-type-handler)}
      :post create-handler
      :delete conditional-delete-type-handler}]
    ["/Observation/__page"
     {:name :Observation/page
      :conflicting true
      :fhir.resource/type "Observation"
      :get {:middleware [[wrap-db node 100]]
            :handler (wrap-params search-type-handler)}}]
    ["/Patient"
     {:name :Patient/type
      :fhir.resource/type "Patient"
      :get {:middleware [[wrap-db node 100]]
            :handler (wrap-params search-type-handler)}
      :post create-handler
      :delete conditional-delete-type-handler}]
    ["/Patient/__page"
     {:name :Patient/page
      :conflicting true
      :fhir.resource/type "Patient"
      :get {:middleware [[wrap-db node 100]]
            :handler (wrap-params search-type-handler)}}]
    ["/Patient/{id}"
     {:name :Patient/instance
      :conflicting true
      :fhir.resource/type "Patient"
      :get {:middleware [[wrap-db node 100]] :handler read-handler}
      :delete delete-handler
      :put update-handler}]
    ["/Patient/{id}/_history/{vid}"
     {:name :Patient/versioned-instance}]]
   (cond-> {:syntax :bracket}
     batch?
     (assoc :reitit.middleware/transform
            (fn [middleware]
              (into [] (remove (comp #{:db} :name)) middleware))))))

(defmethod ig/init-key ::batch-handler [_ {:keys [router]}]
  (reitit.ring/ring-handler router handler-util/default-batch-handler))

(derive ::batch-router ::router)

(def config
  (assoc
   api-stub/mem-node-config
   :blaze.interaction/transaction
   {:node (ig/ref :blaze.db/node)
    :batch-handler (ig/ref ::batch-handler)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :db-sync-timeout 1000}

   :blaze.interaction/create
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}

   :blaze.interaction/search-type
   {::search-util/link (ig/ref ::search-util/link)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :page-store (ig/ref :blaze.page-store/local)
    :page-id-cipher (ig/ref :blaze.test/page-id-cipher)}

   :blaze.interaction/read
   {:node (ig/ref :blaze.db/node)}

   :blaze.interaction/delete
   {:node (ig/ref :blaze.db/node)}

   :blaze.interaction/update
   {:node (ig/ref :blaze.db/node)}

   :blaze.interaction/conditional-delete-type
   {:node (ig/ref :blaze.db/node)}

   ::batch-handler
   {:router (ig/ref ::batch-router)}

   ::router
   {:node (ig/ref :blaze.db/node)
    :create-handler (ig/ref :blaze.interaction/create)
    :search-type-handler (ig/ref :blaze.interaction/search-type)
    :read-handler (ig/ref :blaze.interaction/read)
    :delete-handler (ig/ref :blaze.interaction/delete)
    :update-handler (ig/ref :blaze.interaction/update)
    :conditional-delete-type-handler (ig/ref :blaze.interaction/conditional-delete-type)}

   ::batch-router
   {:batch? true
    :node (ig/ref :blaze.db/node)
    :create-handler (ig/ref :blaze.interaction/create)
    :search-type-handler (ig/ref :blaze.interaction/search-type)
    :read-handler (ig/ref :blaze.interaction/read)
    :delete-handler (ig/ref :blaze.interaction/delete)
    :update-handler (ig/ref :blaze.interaction/update)
    :conditional-delete-type-handler (ig/ref :blaze.interaction/conditional-delete-type)}

   :blaze/job-scheduler
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}

   ::search-util/link {:fhir/version "4.0.1"}
   :blaze.test/fixed-rng-fn {}
   :blaze.page-store/local {}
   :blaze.test/fixed-rng {}
   :blaze.test/page-id-cipher {}))

(deftest init-handler-test
  (testing "nil config"
    (given-failed-system {:blaze.interaction/transaction nil}
      :key := :blaze.interaction/transaction
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.interaction/transaction {}}
      :key := :blaze.interaction/transaction
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :batch-handler))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 4 :pred] := `(fn ~'[%] (contains? ~'% :db-sync-timeout))))

  (testing "invalid node"
    (given-failed-system (assoc-in config [:blaze.interaction/transaction :node] ::invalid)
      :key := :blaze.interaction/transaction
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/node]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid batch-handler"
    (given-failed-system (assoc-in config [:blaze.interaction/transaction :batch-handler] ::invalid)
      :key := :blaze.interaction/transaction
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.rest-api/batch-handler]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid clock"
    (given-failed-system (assoc-in config [:blaze.interaction/transaction :clock] ::invalid)
      :key := :blaze.interaction/transaction
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/clock]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid rng-fn"
    (given-failed-system (assoc-in config [:blaze.interaction/transaction :rng-fn] ::invalid)
      :key := :blaze.interaction/transaction
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/rng-fn]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid db-sync-timeout"
    (given-failed-system (assoc-in config [:blaze.interaction/transaction :db-sync-timeout] ::invalid)
      :key := :blaze.interaction/transaction
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.rest-api/db-sync-timeout]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(defn wrap-defaults [handler router]
  (fn [request]
    (handler
     (assoc request
            :blaze/base-url base-url
            ::reitit/router router))))

(defmacro with-handler [[handler-binding & [node-binding]] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{handler# :blaze.interaction/transaction
                         router# ::router
                         node# :blaze.db/node} config]
       ~txs
       (let [~handler-binding (-> handler# (wrap-defaults router#)
                                  wrap-error)
             ~(or node-binding '_) node#]
         ~@body))))

(deftest handler-test
  (with-handler [handler]
    (testing "on missing body"
      (let [{:keys [status body]}
            @(handler {})]

        (testing "returns error"
          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "invalid"
            [:issue 0 :diagnostics] := #fhir/string "Missing Bundle."))))

    (testing "on wrong resource type"
      (let [{:keys [status body]}
            @(handler {:body {:fhir/type :fhir/Patient}})]

        (testing "returns error"
          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "value"
            [:issue 0 :diagnostics] := #fhir/string "Expected a Bundle resource but got a Patient resource."))))

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
            [:issue 0 :diagnostics] := #fhir/string "Expected a Bundle type of batch or transaction but was `foo`.")))))

  (doseq [type ["transaction" "batch"]]
    (testing (format "On %s bundle" type)
      (testing "empty bundle"
        (with-handler [handler]
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
                  {:fhir/type :fhir/Patient :id "0"}
                  :request
                  {:fhir/type :fhir.Bundle.entry/request
                   :method #fhir/code "PUT"
                   :url #fhir/uri "Patient/0"}}]]

            (testing "without return preference"
              (with-handler [handler]
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
                      :status := #fhir/string "201"
                      :location := (type/uri (str base-url "/Patient/0/_history/1"))
                      :etag := #fhir/string "W/\"1\""
                      :lastModified := Instant/EPOCH)))))

            (testing "with representation return preference"
              (with-handler [handler]
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
                      [:meta :versionId] := #fhir/id "1"
                      [:meta :lastUpdated] := Instant/EPOCH))

                  (testing "entry response"
                    (given response
                      :status := #fhir/string "201"
                      :location := (type/uri (str base-url "/Patient/0/_history/1"))
                      :etag := #fhir/string "W/\"1\""
                      :lastModified := Instant/EPOCH)))))))

        (testing "and updated resource"
          (let [entries
                [{:fhir/type :fhir.Bundle/entry
                  :resource
                  {:fhir/type :fhir/Patient :id "0"
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
                      :status := #fhir/string "200"
                      :etag := #fhir/string "W/\"2\""
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
                      [:meta :versionId] := #fhir/id "2"
                      [:meta :lastUpdated] := Instant/EPOCH))

                  (testing "entry response"
                    (given response
                      :status := #fhir/string "200"
                      :etag := #fhir/string "W/\"2\""
                      :lastModified := Instant/EPOCH)))))))

        (testing "with identical content"
          (let [entries
                [{:fhir/type :fhir.Bundle/entry
                  :resource
                  {:fhir/type :fhir/Patient :id "0"
                   :meta (type/meta {:versionId #fhir/id "1"
                                     :lastUpdated Instant/EPOCH})
                   :gender #fhir/code "female"}
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
                      :status := #fhir/string "200"
                      :etag := #fhir/string "W/\"1\""
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
                      :gender := #fhir/code "female"
                      [:meta :versionId] := #fhir/id "1"
                      [:meta :lastUpdated] := Instant/EPOCH))

                  (testing "entry response"
                    (given response
                      :status := #fhir/string "200"
                      :etag := #fhir/string "W/\"1\""
                      :lastModified := Instant/EPOCH)))))

            (testing "and content changing transaction in between"
              (with-redefs [kv/put!
                            (fn [store entries]
                              (Thread/sleep 20)
                              (kv-p/-put store entries))]
                (with-handler [handler node]
                  [[[:put {:fhir/type :fhir/Patient :id "0"
                           :gender #fhir/code "female"}]]]

                  ;; don't wait for the transaction to be finished because the handler
                  ;; call should see the first version of the patient
                  @(node/submit-tx node [[:put {:fhir/type :fhir/Patient :id "0"
                                                :gender #fhir/code "male"}]])

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
                        :status := #fhir/string "200"
                        :etag := #fhir/string "W/\"4\""
                        :lastModified := Instant/EPOCH)))))))))

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
                    :status := #fhir/string "201"
                    :location := (type/uri (str base-url "/Patient/AAAAAAAAAAAAAAAA/_history/1"))
                    :etag := #fhir/string "W/\"1\""
                    :lastModified := Instant/EPOCH)))))

          (testing "with representation return preference"
            (with-handler [handler]
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
                    [:meta :versionId] := #fhir/id "1"
                    [:meta :lastUpdated] := Instant/EPOCH))

                (testing "entry response"
                  (given response
                    :status := #fhir/string "201"
                    :location := (type/uri (str base-url "/Patient/AAAAAAAAAAAAAAAA/_history/1"))
                    :etag := #fhir/string "W/\"1\""
                    :lastModified := Instant/EPOCH)))))))

      (testing "and conditional create interaction"
        (testing "with empty property"
          (with-handler [handler]
            (let [{:keys [status]}
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
                         :ifNoneExist #fhir/string ""}}]}})]

              (testing "a unconditional create is executed"
                (is (= 200 status))))))

        (testing "with ignorable _sort search parameter"
          (with-handler [handler]
            (let [{:keys [status]}
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
                         :ifNoneExist #fhir/string "_sort=a"}}]}})]

              (testing "a unconditional create is executed"
                (is (= 200 status))))))

        (testing "with non-matching patient"
          (testing "without return preference"
            (with-handler [handler]
              [[[:put {:fhir/type :fhir/Patient :id "0"
                       :identifier
                       [#fhir/Identifier{:value #fhir/string "095156"}]}]]]

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
                           :ifNoneExist #fhir/string "identifier=150015"}}]}})]

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
                      :status := #fhir/string "201"
                      :location := (type/uri (str base-url "/Patient/AAAAAAAAAAAAAAAA/_history/2"))
                      :etag := #fhir/string "W/\"2\""
                      :lastModified := Instant/EPOCH))))))

          (testing "with representation return preference"
            (with-handler [handler]
              [[[:put {:fhir/type :fhir/Patient :id "0"
                       :identifier
                       [#fhir/Identifier{:value #fhir/string "095156"}]}]]]

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
                           :ifNoneExist #fhir/string "identifier=150015"}}]}})]

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
                      [:meta :versionId] := #fhir/id "2"
                      [:meta :lastUpdated] := Instant/EPOCH))

                  (testing "entry response"
                    (given response
                      :status := #fhir/string "201"
                      :location := (type/uri (str base-url "/Patient/AAAAAAAAAAAAAAAA/_history/2"))
                      :etag := #fhir/string "W/\"2\""
                      :lastModified := Instant/EPOCH)))))))

        (testing "with matching patient"
          (testing "without return preference"
            (with-handler [handler]
              [[[:put {:fhir/type :fhir/Patient :id "0"
                       :identifier
                       [#fhir/Identifier{:value #fhir/string "095156"}]}]]]

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
                           :ifNoneExist #fhir/string "identifier=095156"}}]}})]

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
                      :status := #fhir/string "200"
                      :location := nil
                      :etag := #fhir/string "W/\"1\""
                      :lastModified := Instant/EPOCH))))))

          (testing "with representation return preference"
            (with-handler [handler]
              [[[:put {:fhir/type :fhir/Patient :id "0"
                       :identifier
                       [#fhir/Identifier{:value #fhir/string "095156"}]}]]]

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
                           :ifNoneExist #fhir/string "identifier=095156"}}]}})]

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
                      [:meta :versionId] := #fhir/id "1"
                      [:meta :lastUpdated] := Instant/EPOCH))

                  (testing "entry response"
                    (given response
                      :status := #fhir/string "200"
                      :etag := #fhir/string "W/\"1\""
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
                    :status := #fhir/string "204"
                    :etag := #fhir/string "W/\"2\""
                    :lastModified := Instant/EPOCH)))))))

      (testing "and read interaction"
        (testing "returns Not-Found on non-existing resource"
          (with-handler [handler]
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
                  (is (= #fhir/string "404" (:status response))))

                (testing "with outcome"
                  (given (:outcome response)
                    :fhir/type := :fhir/OperationOutcome
                    [:issue 0 :severity] := #fhir/code "error"
                    [:issue 0 :code] := #fhir/code "not-found"
                    [:issue 0 :diagnostics] := #fhir/string "Resource `Patient/0` was not found."
                    [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0]"))))))

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
                  [:meta :versionId] := #fhir/id "1"
                  [:meta :lastUpdated] := Instant/EPOCH))

              (testing "entry response"
                (given response
                  :status := #fhir/string "200"
                  :etag := #fhir/string "W/\"1\""
                  :lastModified := Instant/EPOCH))))))))

  (testing "On transaction bundle"
    (testing "on missing request"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler
                {:body
                 {:fhir/type :fhir/Bundle
                  :type #fhir/code "transaction"
                  :entry
                  [{:fhir/type :fhir.Bundle/entry}]}})]

          (testing "returns error"
            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "value"
              [:issue 0 :diagnostics] := #fhir/string "Missing request."
              [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0]")))))

    (testing "on missing request url"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler
                {:body
                 {:fhir/type :fhir/Bundle
                  :type #fhir/code "transaction"
                  :entry
                  [{:fhir/type :fhir.Bundle/entry
                    :request
                    {:fhir/type :fhir.Bundle.entry/request}}]}})]

          (testing "returns error"
            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "value"
              [:issue 0 :diagnostics] := #fhir/string "Missing request URL."
              [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0].request")))))

    (testing "on missing request method"
      (with-handler [handler]
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
              [:issue 0 :diagnostics] := #fhir/string "Missing request method."
              [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0].request")))))

    (testing "on unknown method"
      (with-handler [handler]
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
              [:issue 0 :diagnostics] := #fhir/string "Unknown request method `FOO`."
              [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0].request.method")))))

    (testing "on unsupported method"
      (with-handler [handler]
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
              [:issue 0 :diagnostics] := #fhir/string "Unsupported request method `PATCH`."
              [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0].request.method")))))

    (testing "and update interaction"
      (testing "on missing type in URL"
        (with-handler [handler]
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
                [:issue 0 :diagnostics] := #fhir/string "Can't parse type from request URL ``."
                [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0].request.url")))))

      (testing "on unknown type"
        (with-handler [handler]
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
                [:issue 0 :diagnostics] := #fhir/string "Unknown type `Foo` in bundle entry request URL `Foo/0`."
                [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0].request.url")))))

      (testing "on missing resource type"
        (with-handler [handler]
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
                       :url #fhir/uri "Patient/0"}}]}})]

            (testing "returns error "
              (is (= 400 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "required"
                [:issue 0 :diagnostics] := #fhir/string "Missing resource type."
                [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0].resource")))))

      (testing "on type mismatch"
        (with-handler [handler]
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
                [:issue 0 :diagnostics] := #fhir/string "Type mismatch between resource type `Observation` and URL `Patient/0`."
                [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0].request.url"
                [:issue 0 :expression 1] := #fhir/string "Bundle.entry[0].resource.resourceType")))))

      (testing "on missing ID"
        (with-handler [handler]
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
                [:issue 0 :diagnostics] := #fhir/string "Resource id is missing."
                [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0].resource.id")))))

      (testing "on subsetted"
        (with-handler [handler]
          (let [{:keys [status body]}
                @(handler
                  {:body
                   {:fhir/type :fhir/Bundle
                    :type #fhir/code "transaction"
                    :entry
                    [{:fhir/type :fhir.Bundle/entry
                      :resource
                      {:fhir/type :fhir/Patient :id "0"
                       :meta (type/meta {:tag [fu/subsetted]})}
                      :request
                      {:fhir/type :fhir.Bundle.entry/request
                       :method #fhir/code "PUT"
                       :url #fhir/uri "Patient/0"}}]}})]

            (testing "returns error "
              (is (= 400 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "processing"
                [:issue 0 :diagnostics] := #fhir/string "Resources with tag SUBSETTED may be incomplete and so can't be used in updates."
                [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0].resource")))))

      (testing "on missing ID in URL"
        (with-handler [handler]
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
                [:issue 0 :diagnostics] := #fhir/string "Can't parse id from URL `Patient`."
                [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0].request.url")))))

      (testing "on ID mismatch"
        (with-handler [handler]
          (let [{:keys [status body]}
                @(handler
                  {:body
                   {:fhir/type :fhir/Bundle
                    :type #fhir/code "transaction"
                    :entry
                    [{:fhir/type :fhir.Bundle/entry
                      :resource
                      {:fhir/type :fhir/Patient :id "1"}
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
                [:issue 0 :diagnostics] := #fhir/string "Id mismatch between resource id `1` and URL `Patient/0`."
                [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0].request.url"
                [:issue 0 :expression 1] := #fhir/string "Bundle.entry[0].resource.id")))))

      (testing "on optimistic locking failure"
        (testing "with different content"
          (with-handler [handler]
            [[[:create {:fhir/type :fhir/Patient :id "0"
                        :gender #fhir/code "female"}]]
             [[:put {:fhir/type :fhir/Patient :id "0"
                     :gender #fhir/code "male"}]]]

            (let [{:keys [status body]}
                  @(handler
                    {:body
                     {:fhir/type :fhir/Bundle
                      :type #fhir/code "transaction"
                      :entry
                      [{:fhir/type :fhir.Bundle/entry
                        :resource
                        {:fhir/type :fhir/Patient :id "0"
                         :gender #fhir/code "female"}
                        :request
                        {:fhir/type :fhir.Bundle.entry/request
                         :method #fhir/code "PUT"
                         :url #fhir/uri "Patient/0"
                         :ifMatch #fhir/string "W/\"1\""}}]}})]

              (testing "returns error"
                (is (= 412 status))

                (given body
                  :fhir/type := :fhir/OperationOutcome
                  [:issue 0 :severity] := #fhir/code "error"
                  [:issue 0 :code] := #fhir/code "conflict"
                  [:issue 0 :diagnostics] := #fhir/string "Precondition `W/\"1\"` failed on `Patient/0`.")))))

        (testing "with identical content"
          (with-handler [handler]
            [[[:create {:fhir/type :fhir/Patient :id "0"
                        :gender #fhir/code "male"}]]
             [[:put {:fhir/type :fhir/Patient :id "0"
                     :gender #fhir/code "female"}]]]

            (let [{:keys [status body]}
                  @(handler
                    {:body
                     {:fhir/type :fhir/Bundle
                      :type #fhir/code "transaction"
                      :entry
                      [{:fhir/type :fhir.Bundle/entry
                        :resource
                        {:fhir/type :fhir/Patient :id "0"
                         :gender #fhir/code "female"}
                        :request
                        {:fhir/type :fhir.Bundle.entry/request
                         :method #fhir/code "PUT"
                         :url #fhir/uri "Patient/0"
                         :ifMatch #fhir/string "W/\"1\""}}]}})]

              (testing "returns error"
                (is (= 412 status))

                (given body
                  :fhir/type := :fhir/OperationOutcome
                  [:issue 0 :severity] := #fhir/code "error"
                  [:issue 0 :code] := #fhir/code "conflict"
                  [:issue 0 :diagnostics] := #fhir/string "Precondition `W/\"1\"` failed on `Patient/0`."))))

          (testing "and content changing transaction in between"
            (with-redefs [kv/put!
                          (fn [store entries]
                            (Thread/sleep 20)
                            (kv-p/-put store entries))]
              (with-handler [handler node]
                [[[:create {:fhir/type :fhir/Patient :id "0"
                            :gender #fhir/code "female"}]]]

                ;; don't wait for the transaction to be finished because the handler
                ;; call should see the first version of the patient
                @(node/submit-tx node [[:put {:fhir/type :fhir/Patient :id "0"
                                              :gender #fhir/code "male"}]])

                (let [{:keys [status body]}
                      @(handler
                        {:body
                         {:fhir/type :fhir/Bundle
                          :type #fhir/code "transaction"
                          :entry
                          [{:fhir/type :fhir.Bundle/entry
                            :resource
                            {:fhir/type :fhir/Patient :id "0"
                             :gender #fhir/code "female"}
                            :request
                            {:fhir/type :fhir.Bundle.entry/request
                             :method #fhir/code "PUT"
                             :url #fhir/uri "Patient/0"
                             :ifMatch #fhir/string "W/\"1\""}}]}})]

                  (testing "returns error"
                    (is (= 412 status))

                    (given body
                      :fhir/type := :fhir/OperationOutcome
                      [:issue 0 :severity] := #fhir/code "error"
                      [:issue 0 :code] := #fhir/code "conflict"
                      [:issue 0 :diagnostics] := #fhir/string "Precondition `W/\"1\"` failed on `Patient/0`.")))

                (testing "we did not retry to the error transaction is 3"
                  (is (= 3 (:error-t @(:state node))))))))))

      (testing "on duplicate resources"
        (with-handler [handler]
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
                       :url #fhir/uri "Patient/0"}}
                     {:fhir/type :fhir.Bundle/entry
                      :resource
                      {:fhir/type :fhir/Patient :id "0"}
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
                [:issue 0 :diagnostics] := #fhir/string "Duplicate resource `Patient/0`.")))))

      (testing "on violated referential integrity"
        (with-handler [handler]
          (let [{:keys [status body]}
                @(handler
                  {:body
                   {:fhir/type :fhir/Bundle
                    :type #fhir/code "transaction"
                    :entry
                    [{:fhir/type :fhir.Bundle/entry
                      :resource
                      {:fhir/type :fhir/Observation :id "0"
                       :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}
                      :request
                      {:fhir/type :fhir.Bundle.entry/request
                       :method #fhir/code "PUT"
                       :url #fhir/uri "Observation/0"}}]}})]

            (testing "returns error"
              (is (= 409 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "conflict"
                [:issue 0 :diagnostics] := #fhir/string "Referential integrity violated. Resource `Patient/0` doesn't exist.")))))

      (testing "on missing resource content"
        (with-redefs [rs/get (fn [_ _] (ac/completed-future nil))]
          (with-handler [handler]
            (let [{:keys [status body]}
                  @(handler
                    {:headers {"prefer" "return=representation"}
                     :body
                     {:fhir/type :fhir/Bundle
                      :type #fhir/code "transaction"
                      :entry
                      [{:fhir/type :fhir.Bundle/entry
                        :resource
                        {:fhir/type :fhir/Patient :id "0"}
                        :request
                        {:fhir/type :fhir.Bundle.entry/request
                         :method #fhir/code "PUT"
                         :url #fhir/uri "Patient/0"}}]}})]

              (testing "returns error"
                (is (= 500 status))

                (given body
                  :fhir/type := :fhir/OperationOutcome
                  [:issue 0 :severity] := #fhir/code "error"
                  [:issue 0 :code] := #fhir/code "incomplete"
                  [:issue 0 :diagnostics] := #fhir/string "The transaction was successful but the resource content of `Patient/0` with hash `C9ADE22457D5AD750735B6B166E3CE8D6878D09B64C2C2868DCB6DE4C9EFBD4F` was not found during response creation.")))))))

    (testing "and create interaction"
      (testing "on missing resource content"
        (with-redefs [rs/get (fn [_ _] (ac/completed-future nil))]
          (with-handler [handler]
            (let [{:keys [status body]}
                  @(handler
                    {:headers {"prefer" "return=representation"}
                     :body
                     {:fhir/type :fhir/Bundle
                      :type #fhir/code "transaction"
                      :entry
                      [{:fhir/type :fhir.Bundle/entry
                        :resource
                        {:fhir/type :fhir/Patient}
                        :request
                        {:fhir/type :fhir.Bundle.entry/request
                         :method #fhir/code "POST"
                         :url #fhir/uri "Patient"}}]}})]

              (testing "returns error"
                (is (= 500 status))

                (given body
                  :fhir/type := :fhir/OperationOutcome
                  [:issue 0 :severity] := #fhir/code "error"
                  [:issue 0 :code] := #fhir/code "incomplete"
                  [:issue 0 :diagnostics] := #fhir/string "The transaction was successful but the resource content of `Patient/AAAAAAAAAAAAAAAA` with hash `C854DBB25D7D32AE87A7D1CD633145A775E139904408FF821FA7ABB77D311DFF` was not found during response creation."))))))

      (testing "creates sequential identifiers"
        (with-handler [handler]
          (let [{:keys [body]}
                @(handler
                  {:headers {"prefer" "return=representation"}
                   :body
                   {:fhir/type :fhir/Bundle
                    :type #fhir/code "transaction"
                    :entry
                    [{:fhir/type :fhir.Bundle/entry
                      :resource
                      {:fhir/type :fhir/Patient}
                      :request
                      {:fhir/type :fhir.Bundle.entry/request
                       :method #fhir/code "POST"
                       :url #fhir/uri "Patient"}}
                     {:fhir/type :fhir.Bundle/entry
                      :resource
                      {:fhir/type :fhir/Patient}
                      :request
                      {:fhir/type :fhir.Bundle.entry/request
                       :method #fhir/code "POST"
                       :url #fhir/uri "Patient"}}]}})]
            (given body
              [:entry 0 :resource :id] := "AAAAAAAAAAAAAAAA"
              [:entry 1 :resource :id] := "AAAAAAAAAAAAAAAB"))))

      (testing "resolves links"
        (with-handler [handler]
          (let [{:keys [body]}
                @(handler
                  {:headers {"prefer" "return=representation"}
                   :body
                   {:fhir/type :fhir/Bundle
                    :type #fhir/code "transaction"
                    :entry
                    [{:fhir/type :fhir.Bundle/entry
                      :fullUrl #fhir/uri "urn:uuid:44cf9905-f381-4849-8a35-79a6b29ae1b5"
                      :resource
                      {:fhir/type :fhir/DocumentReference
                       :content
                       [{:fhir/type :fhir.DocumentReference/content
                         :attachment
                         #fhir/Attachment{:url #fhir/url "urn:uuid:5b016a4d-d393-48df-8d92-7ac4d1b8e56d"}}]}
                      :request
                      {:fhir/type :fhir.Bundle.entry/request
                       :method #fhir/code "POST"
                       :url #fhir/uri "DocumentReference"}}
                     {:fhir/type :fhir.Bundle/entry
                      :fullUrl #fhir/uri "urn:uuid:5b016a4d-d393-48df-8d92-7ac4d1b8e56d"
                      :resource
                      {:fhir/type :fhir/Binary}
                      :request
                      {:fhir/type :fhir.Bundle.entry/request
                       :method #fhir/code "POST"
                       :url #fhir/uri "Binary"}}]}})]
            (given body
              [:entry 0 :resource :content 0 :attachment :url] := #fhir/url "Binary/AAAAAAAAAAAAAAAB"
              [:entry 1 :resource :id] := "AAAAAAAAAAAAAAAB")))))

    (testing "and conditional create interaction"
      (testing "on multiple matching patients"
        (with-handler [handler]
          [[[:put {:fhir/type :fhir/Patient :id "0"
                   :birthDate #fhir/date "2020"}]
            [:put {:fhir/type :fhir/Patient :id "1"
                   :birthDate #fhir/date "2020"}]]]

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
                       :ifNoneExist #fhir/string "birthdate=2020"}}]}})]

            (testing "returns error"
              (is (= 412 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "conflict"
                [:issue 0 :diagnostics] := #fhir/string "Conditional create of a Patient with query `birthdate=2020` failed because at least the two matches `Patient/0/_history/1` and `Patient/1/_history/1` were found."))))))

    (testing "and conditional update interaction"
      (testing "with if-none-match *"
        (testing "on non-existing resource"
          (with-handler [handler]
            (let [{:keys [status]
                   {[{:keys [resource response]}] :entry :as body} :body}
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
                         :url #fhir/uri "Patient/0"
                         :ifNoneMatch #fhir/string "*"}}]}})]

              (testing "response status"
                (is (= 200 status)))

              (testing "bundle"
                (given body
                  :fhir/type := :fhir/Bundle
                  :id := "AAAAAAAAAAAAAAAA"
                  :type := #fhir/code "transaction-response"))

              (testing "entry resource"
                (is (nil? resource)))

              (testing "entry response"
                (given response
                  :status := #fhir/string "201"
                  :etag := #fhir/string "W/\"1\""
                  :lastModified := Instant/EPOCH)))))

        (testing "on existing resource"
          (with-handler [handler]
            [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

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
                         :url #fhir/uri "Patient/0"
                         :ifNoneMatch #fhir/string "*"}}]}})]

              (testing "returns error"
                (is (= 412 status))

                (given body
                  :fhir/type := :fhir/OperationOutcome
                  [:issue 0 :severity] := #fhir/code "error"
                  [:issue 0 :code] := #fhir/code "conflict"
                  [:issue 0 :diagnostics] := #fhir/string "Resource `Patient/0` already exists.")))))))

    (testing "and conditional delete interaction"
      (let [entries
            [{:fhir/type :fhir.Bundle/entry
              :request
              {:fhir/type :fhir.Bundle.entry/request
               :method #fhir/code "DELETE"
               :url #fhir/uri "Patient"}}
             {:fhir/type :fhir.Bundle/entry
              :request
              {:fhir/type :fhir.Bundle.entry/request
               :method #fhir/code "DELETE"
               :url #fhir/uri "Observation"}}]]

        (testing "without return preference"
          (with-handler [handler]
            [[[:put {:fhir/type :fhir/Patient :id "0"}]
              [:put {:fhir/type :fhir/Observation :id "0"
                     :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

            (let [{:keys [status body]
                   {[{:keys [resource response]}] :entry} :body}
                  @(handler
                    {:body
                     {:fhir/type :fhir/Bundle
                      :type #fhir/code "transaction"
                      :entry entries}})]

              (testing "response status"
                (is (= 200 status)))

              (testing "bundle"
                (given body
                  :fhir/type := :fhir/Bundle
                  :id := "AAAAAAAAAAAAAAAA"
                  :type := #fhir/code "transaction-response"))

              (testing "entry resource"
                (is (nil? resource)))

              (testing "entry response"
                (given response
                  :status := #fhir/string "204"
                  :etag := #fhir/string "W/\"2\""
                  :lastModified := Instant/EPOCH)))))))

    (testing "and read interaction after update interaction"
      (let [entries
            [{:fhir/type :fhir.Bundle/entry
              :request
              {:fhir/type :fhir.Bundle.entry/request
               :method #fhir/code "GET"
               :url #fhir/uri "Patient/111718"}}
             {:fhir/type :fhir.Bundle/entry
              :resource
              {:fhir/type :fhir/Patient :id "111718"}
              :request
              {:fhir/type :fhir.Bundle.entry/request
               :method #fhir/code "PUT"
               :url #fhir/uri "Patient/111718"}}]]

        (testing "without return preference"
          (with-handler [handler]
            (let [{:keys [status body]
                   {[first-entry second-entry] :entry} :body}
                  @(handler
                    {:body
                     {:fhir/type :fhir/Bundle
                      :type #fhir/code "transaction"
                      :entry entries}})]

              (testing "response status"
                (is (= 200 status)))

              (testing "bundle"
                (given body
                  :fhir/type := :fhir/Bundle
                  :id := "AAAAAAAAAAAAAAAA"
                  :type := #fhir/code "transaction-response"))

              (testing "the first entry has the right resource"
                (given (:resource first-entry)
                  :fhir/type := :fhir/Patient
                  :id := "111718"
                  [:meta :versionId] := #fhir/id "1"
                  [:meta :lastUpdated] := Instant/EPOCH))

              (testing "the first entry has the right response"
                (given (:response first-entry)
                  :status := #fhir/string "200"
                  :etag := #fhir/string "W/\"1\""
                  :lastModified := Instant/EPOCH))

              (testing "the second entry resource is nil"
                (is (nil? (:resource second-entry))))

              (testing "the second entry has the right response"
                (given (:response second-entry)
                  :status := #fhir/string "201"
                  :etag := #fhir/string "W/\"1\""
                  :lastModified := Instant/EPOCH))))))))

  (testing "On batch bundle"
    (testing "on missing request"
      (with-handler [handler]
        (let [{:keys [status] {[{:keys [response]}] :entry} :body}
              @(handler
                {:body
                 {:fhir/type :fhir/Bundle
                  :type #fhir/code "batch"
                  :entry
                  [{:fhir/type :fhir.Bundle/entry}]}})]

          (testing "response status"
            (is (= 200 status)))

          (testing "returns error"
            (testing "with status"
              (is (= #fhir/string "400" (:status response))))

            (testing "with outcome"
              (given (:outcome response)
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "value"
                [:issue 0 :diagnostics] := #fhir/string "Missing request."
                [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0]"))))))

    (testing "on missing request url"
      (with-handler [handler]
        (let [{:keys [status] {[{:keys [response]}] :entry} :body}
              @(handler
                {:body
                 {:fhir/type :fhir/Bundle
                  :type #fhir/code "batch"
                  :entry
                  [{:fhir/type :fhir.Bundle/entry
                    :request {:fhir/type :fhir.Bundle.entry/request}}]}})]

          (testing "response status"
            (is (= 200 status)))

          (testing "returns error"
            (testing "with status"
              (is (= #fhir/string "400" (:status response))))

            (testing "with outcome"
              (given (:outcome response)
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "value"
                [:issue 0 :diagnostics] := #fhir/string "Missing request URL."
                [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0].request"))))))

    (testing "on missing request method"
      (with-handler [handler]
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
              (is (= #fhir/string "400" (:status response))))

            (testing "with outcome"
              (given (:outcome response)
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "value"
                [:issue 0 :diagnostics] := #fhir/string "Missing request method."
                [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0].request"))))))

    (testing "on unknown method"
      (with-handler [handler]
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
              (is (= #fhir/string "400" (:status response))))

            (testing "with outcome"
              (given (:outcome response)
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "value"
                [:issue 0 :diagnostics] := #fhir/string "Unknown request method `FOO`."
                [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0].request.method"))))))

    (testing "on unsupported method"
      (with-handler [handler]
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
              (is (= #fhir/string "422" (:status response))))

            (testing "with outcome"
              (given (:outcome response)
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "not-supported"
                [:issue 0 :diagnostics] := #fhir/string "Unsupported request method `PATCH`."
                [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0].request.method"))))))

    (testing "on subsetted"
      (with-handler [handler]
        (let [{:keys [status] {[{:keys [response]}] :entry} :body}
              @(handler
                {:body
                 {:fhir/type :fhir/Bundle
                  :type #fhir/code "batch"
                  :entry
                  [{:fhir/type :fhir.Bundle/entry
                    :resource
                    {:fhir/type :fhir/Patient :id "0"
                     :meta (type/meta {:tag [fu/subsetted]})}
                    :request
                    {:fhir/type :fhir.Bundle.entry/request
                     :method #fhir/code "PUT"
                     :url #fhir/uri "Patient/0"}}]}})]

          (testing "response status"
            (is (= 200 status)))

          (testing "returns error"
            (testing "with status"
              (is (= #fhir/string "400" (:status response))))

            (testing "with outcome"
              (given (:outcome response)
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "processing"
                [:issue 0 :diagnostics] := #fhir/string "Resources with tag SUBSETTED may be incomplete and so can't be used in updates."
                [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0].resource"))))))

    (testing "on metadata"
      (with-handler [handler]
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
                     :url #fhir/uri "metadata"}}]}})]

          (testing "response status"
            (is (= 200 status)))

          (testing "entry resource"
            (given resource
              :fhir/type := :fhir/CapabilityStatement))

          (testing "entry response"
            (given response
              :status := #fhir/string "200")))))

    (testing "and update interaction"
      (testing "on invalid type-level URL"
        (with-handler [handler]
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
                (is (= #fhir/string "400" (:status response))))

              (testing "with outcome"
                (given (:outcome response)
                  :fhir/type := :fhir/OperationOutcome
                  [:issue 0 :severity] := #fhir/code "error"
                  [:issue 0 :code] := #fhir/code "value"
                  [:issue 0 :diagnostics] := #fhir/string "Can't parse id from URL `Patient`."
                  [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0].request.url"))))))

      (testing "on optimistic locking failure"
        (testing "with different content"
          (with-handler [handler]
            [[[:create {:fhir/type :fhir/Patient :id "0"
                        :gender #fhir/code "female"}]]
             [[:put {:fhir/type :fhir/Patient :id "0"
                     :gender #fhir/code "male"}]]]

            (let [{:keys [status] {[{:keys [response]}] :entry} :body}
                  @(handler
                    {:body
                     {:fhir/type :fhir/Bundle
                      :type #fhir/code "batch"
                      :entry
                      [{:fhir/type :fhir.Bundle/entry
                        :resource
                        {:fhir/type :fhir/Patient :id "0"
                         :gender #fhir/code "female"}
                        :request
                        {:fhir/type :fhir.Bundle.entry/request
                         :method #fhir/code "PUT"
                         :url #fhir/uri "Patient/0"
                         :ifMatch #fhir/string "W/\"1\""}}]}})]

              (testing "response status"
                (is (= 200 status)))

              (testing "returns error"
                (testing "with status"
                  (is (= #fhir/string "412" (:status response))))

                (testing "with outcome"
                  (given (:outcome response)
                    :fhir/type := :fhir/OperationOutcome
                    [:issue 0 :severity] := #fhir/code "error"
                    [:issue 0 :code] := #fhir/code "conflict"
                    [:issue 0 :diagnostics] := #fhir/string "Precondition `W/\"1\"` failed on `Patient/0`."
                    [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0]"))))))

        (testing "with identical content"
          (with-handler [handler]
            [[[:create {:fhir/type :fhir/Patient :id "0"
                        :gender #fhir/code "male"}]]
             [[:put {:fhir/type :fhir/Patient :id "0"
                     :gender #fhir/code "female"}]]]

            (let [{:keys [status] {[{:keys [response]}] :entry} :body}
                  @(handler
                    {:body
                     {:fhir/type :fhir/Bundle
                      :type #fhir/code "batch"
                      :entry
                      [{:fhir/type :fhir.Bundle/entry
                        :resource
                        {:fhir/type :fhir/Patient :id "0"
                         :gender #fhir/code "female"}
                        :request
                        {:fhir/type :fhir.Bundle.entry/request
                         :method #fhir/code "PUT"
                         :url #fhir/uri "Patient/0"
                         :ifMatch #fhir/string "W/\"1\""}}]}})]

              (testing "response status"
                (is (= 200 status)))

              (testing "returns error"
                (testing "with status"
                  (is (= #fhir/string "412" (:status response))))

                (testing "with outcome"
                  (given (:outcome response)
                    :fhir/type := :fhir/OperationOutcome
                    [:issue 0 :severity] := #fhir/code "error"
                    [:issue 0 :code] := #fhir/code "conflict"
                    [:issue 0 :diagnostics] := #fhir/string "Precondition `W/\"1\"` failed on `Patient/0`."
                    [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0]")))))

          (testing "and content changing transaction in between"
            (with-redefs [kv/put!
                          (fn [store entries]
                            (Thread/sleep 20)
                            (kv-p/-put store entries))]
              (with-handler [handler node]
                [[[:create {:fhir/type :fhir/Patient :id "0"
                            :gender #fhir/code "female"}]]]

                ;; don't wait for the transaction to be finished because the handler
                ;; call should see the first version of the patient
                @(node/submit-tx node [[:put {:fhir/type :fhir/Patient :id "0"
                                              :gender #fhir/code "male"}]])

                (let [{:keys [status] {[{:keys [response]}] :entry} :body}
                      @(handler
                        {:body
                         {:fhir/type :fhir/Bundle
                          :type #fhir/code "batch"
                          :entry
                          [{:fhir/type :fhir.Bundle/entry
                            :resource
                            {:fhir/type :fhir/Patient :id "0"
                             :gender #fhir/code "female"}
                            :request
                            {:fhir/type :fhir.Bundle.entry/request
                             :method #fhir/code "PUT"
                             :url #fhir/uri "Patient/0"
                             :ifMatch #fhir/string "W/\"1\""}}]}})]

                  (testing "response status"
                    (is (= 200 status)))

                  (testing "returns error"
                    (testing "with status"
                      (is (= #fhir/string "412" (:status response))))

                    (testing "with outcome"
                      (given (:outcome response)
                        :fhir/type := :fhir/OperationOutcome
                        [:issue 0 :severity] := #fhir/code "error"
                        [:issue 0 :code] := #fhir/code "conflict"
                        [:issue 0 :diagnostics] := #fhir/string "Precondition `W/\"1\"` failed on `Patient/0`."
                        [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0]"))))

                (testing "we did not retry to the error transaction is 3"
                  (is (= 3 (:error-t @(:state node))))))))))

      (testing "without return preference"
        (with-handler [handler]
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
                :status := #fhir/string "201"
                :location := (type/uri (str base-url "/Patient/0/_history/1"))
                :etag := #fhir/string "W/\"1\""
                :lastModified := Instant/EPOCH))))

        (testing "with identical content"
          (with-handler [handler]
            [[[:create {:fhir/type :fhir/Patient :id "0"
                        :birthDate #fhir/date "2020"}]]]

            (let [{:keys [status] {[{:keys [resource response]}] :entry} :body}
                  @(handler
                    {:body
                     {:fhir/type :fhir/Bundle
                      :type #fhir/code "batch"
                      :entry
                      [{:fhir/type :fhir.Bundle/entry
                        :resource
                        {:fhir/type :fhir/Patient :id "0"
                         :birthDate #fhir/date "2020"}
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
                  :status := #fhir/string "200"
                  :etag := #fhir/string "W/\"1\""
                  :lastModified := Instant/EPOCH))))

          (testing "and content changing transaction in between"
            (with-redefs [kv/put!
                          (fn [store entries]
                            (Thread/sleep 20)
                            (kv-p/-put store entries))]
              (with-handler [handler node]
                [[[:create {:fhir/type :fhir/Patient :id "0"
                            :birthDate #fhir/date "2020"}]]]

                ;; don't wait for the transaction to be finished because the handler
                ;; call should see the first version of the patient
                @(node/submit-tx node [[:put {:fhir/type :fhir/Patient :id "0"
                                              :birthDate #fhir/date "2021"}]])

                (let [{:keys [status] {[{:keys [resource response]}] :entry} :body}
                      @(handler
                        {:body
                         {:fhir/type :fhir/Bundle
                          :type #fhir/code "batch"
                          :entry
                          [{:fhir/type :fhir.Bundle/entry
                            :resource
                            {:fhir/type :fhir/Patient :id "0"
                             :birthDate #fhir/date "2020"}
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
                      :status := #fhir/string "200"
                      :etag := #fhir/string "W/\"4\""
                      :lastModified := Instant/EPOCH)))))))

        (testing "leading slash in URL is removed"
          (with-handler [handler]
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
                  :status := #fhir/string "201"
                  :location := (type/uri (str base-url "/Patient/0/_history/1"))
                  :etag := #fhir/string "W/\"1\""
                  :lastModified := Instant/EPOCH))))))

      (testing "with representation return preference"
        (with-handler [handler]
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
                [:meta :versionId] := #fhir/id "1"
                [:meta :lastUpdated] := Instant/EPOCH))

            (testing "entry response"
              (given response
                :status := #fhir/string "201"
                :location := (type/uri (str base-url "/Patient/0/_history/1"))
                :etag := #fhir/string "W/\"1\""
                :lastModified := Instant/EPOCH))))))

    (testing "and create interaction"
      (testing "on not-found type-level URL"
        (with-handler [handler]
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
                (is (= #fhir/string "400" (:status response))))

              (testing "with outcome"
                (given (:outcome response)
                  :fhir/type := :fhir/OperationOutcome
                  [:issue 0 :severity] := #fhir/code "error"
                  [:issue 0 :code] := #fhir/code "value"
                  [:issue 0 :diagnostics] := #fhir/string "Unknown type `Foo` in bundle entry request URL `Foo`."
                  [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0].request.url"))))))

      (testing "on invalid instance-level URL"
        (with-handler [handler]
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
                (is (= #fhir/string "405" (:status response))))

              (testing "with outcome"
                (given (:outcome response)
                  :fhir/type := :fhir/OperationOutcome
                  [:issue 0 :severity] := #fhir/code "error"
                  [:issue 0 :code] := #fhir/code "processing"
                  [:issue 0 :diagnostics] := #fhir/string "Method POST not allowed on `/Patient/0` endpoint."
                  [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0]"))))))

      (testing "on violated referential integrity"
        (with-handler [handler]
          (let [{:keys [status] {[{:keys [response]}] :entry} :body}
                @(handler
                  {:body
                   {:fhir/type :fhir/Bundle
                    :type #fhir/code "batch"
                    :entry
                    [{:fhir/type :fhir.Bundle/entry
                      :resource
                      {:fhir/type :fhir/Observation
                       :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}
                      :request
                      {:fhir/type :fhir.Bundle.entry/request
                       :method #fhir/code "POST"
                       :url #fhir/uri "Observation"}}]}})]

            (testing "response status"
              (is (= 200 status)))

            (testing "returns error"
              (testing "with status"
                (is (= #fhir/string "409" (:status response))))

              (testing "with outcome"
                (given (:outcome response)
                  :fhir/type := :fhir/OperationOutcome
                  [:issue 0 :severity] := #fhir/code "error"
                  [:issue 0 :code] := #fhir/code "conflict"
                  [:issue 0 :diagnostics] := #fhir/string "Referential integrity violated. Resource `Patient/0` doesn't exist."
                  [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0]")))))))

    (testing "and conditional create interaction"
      (testing "on multiple matching patients"
        (with-handler [handler]
          [[[:put {:fhir/type :fhir/Patient :id "0"
                   :birthDate #fhir/date "2020"}]
            [:put {:fhir/type :fhir/Patient :id "1"
                   :birthDate #fhir/date "2020"}]]]

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
                       :ifNoneExist #fhir/string "birthdate=2020"}}]}})]

            (testing "response status"
              (is (= 200 status)))

            (testing "returns error"
              (testing "with status"
                (is (= #fhir/string "412" (:status response))))

              (testing "with outcome"
                (given (:outcome response)
                  :fhir/type := :fhir/OperationOutcome
                  [:issue 0 :severity] := #fhir/code "error"
                  [:issue 0 :code] := #fhir/code "conflict"
                  [:issue 0 :diagnostics] := #fhir/string "Conditional create of a Patient with query `birthdate=2020` failed because at least the two matches `Patient/0/_history/1` and `Patient/1/_history/1` were found."
                  [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0]")))))))

    (testing "and conditional update interaction"
      (testing "with if-none-match *"
        (testing "on non-existing resource"
          (with-handler [handler]
            (let [{:keys [status]
                   {[{:keys [resource response]}] :entry :as body} :body}
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
                         :url #fhir/uri "Patient/0"
                         :ifNoneMatch #fhir/string "*"}}]}})]

              (testing "response status"
                (is (= 200 status)))

              (testing "bundle"
                (given body
                  :fhir/type := :fhir/Bundle
                  :id := "AAAAAAAAAAAAAAAA"
                  :type := #fhir/code "batch-response"))

              (testing "entry resource"
                (is (nil? resource)))

              (testing "entry response"
                (given response
                  :status := #fhir/string "201"
                  :etag := #fhir/string "W/\"1\""
                  :lastModified := Instant/EPOCH)))))

        (testing "on existing resource"
          (with-handler [handler]
            [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

            (let [{:keys [status] {[{:keys [response]}] :entry} :body}
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
                         :url #fhir/uri "Patient/0"
                         :ifNoneMatch #fhir/string "*"}}]}})]

              (testing "response status"
                (is (= 200 status)))

              (testing "returns error"
                (testing "with status"
                  (is (= #fhir/string "412" (:status response))))

                (testing "with outcome"
                  (given (:outcome response)
                    :fhir/type := :fhir/OperationOutcome
                    [:issue 0 :severity] := #fhir/code "error"
                    [:issue 0 :code] := #fhir/code "conflict"
                    [:issue 0 :diagnostics] := #fhir/string "Resource `Patient/0` already exists."
                    [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0]"))))))))

    (testing "and conditional delete interaction"
      (let [entries
            [{:fhir/type :fhir.Bundle/entry
              :request
              {:fhir/type :fhir.Bundle.entry/request
               :method #fhir/code "DELETE"
               :url #fhir/uri "Patient"}}]]

        (testing "without return preference"
          (with-handler [handler]
            [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

            (let [{:keys [status body]
                   {[{:keys [resource response]}] :entry} :body}
                  @(handler
                    {:body
                     {:fhir/type :fhir/Bundle
                      :type #fhir/code "batch"
                      :entry entries}})]

              (testing "response status"
                (is (= 200 status)))

              (testing "bundle"
                (given body
                  :fhir/type := :fhir/Bundle
                  :id := "AAAAAAAAAAAAAAAA"
                  :type := #fhir/code "batch-response"))

              (testing "entry resource"
                (is (nil? resource)))

              (testing "entry response"
                (given response
                  :status := #fhir/string "204")))))))

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
              [:entry 0 :resource :meta :versionId] := #fhir/id "1"
              [:entry 0 :resource :meta :lastUpdated] := Instant/EPOCH))

          (testing "entry response"
            (given response
              :status := #fhir/string "200"))))

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
                :status := #fhir/string "200")))))

      (testing "with date-time search param value"
        (with-handler [handler]
          [[[:create
             {:fhir/type :fhir/Observation :id "0"
              :effective #fhir/dateTime "2021-12-08T00:00:00+01:00"}]
            [:create
             {:fhir/type :fhir/Observation :id "1"
              :effective #fhir/dateTime "2021-12-09T00:00:00+01:00"}]]]

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
                :status := #fhir/string "200")))

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
                  (is (= #fhir/string "400" (:status response))))

                (testing "with outcome"
                  (given (:outcome response)
                    :fhir/type := :fhir/OperationOutcome
                    [:issue 0 :severity] := #fhir/code "error"
                    [:issue 0 :code] := #fhir/code "invalid"
                    [:issue 0 :diagnostics] := #fhir/string "Invalid date-time value `2021-12-09T00:00:00 01:00` in search parameter `date`."
                    [:issue 0 :expression 0] := #fhir/string "Bundle.entry[0]"))))))))))
