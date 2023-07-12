(ns blaze.rest-api-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.api-stub :refer [mem-node-config]]
    [blaze.db.impl.search-param]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.structure-definition-repo.protocols :as sdrp]
    [blaze.fhir.test-util :refer [structure-definition-repo]]
    [blaze.fhir.test-util.ring :refer [call]]
    [blaze.handler.util :as handler-util]
    [blaze.metrics.spec]
    [blaze.module.test-util :refer [with-system]]
    [blaze.rest-api :as rest-api]
    [blaze.rest-api.middleware.metrics :as metrics]
    [blaze.test-util :as tu :refer [given-thrown]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [reitit.ring]
    [ring.util.response :as ring]
    [taoensso.timbre :as log])
  (:import
    [java.io ByteArrayInputStream]))


(st/instrument)
(log/set-level! :trace)


(test/use-fixtures :each tu/fixture)


(defn- handler [key]
  (fn [_] key))


(defn router [auth-backends]
  (rest-api/router
    {:base-url "base-url-111523"
     :structure-definitions
     [{:kind "resource" :name "Patient"}
      {:kind "resource" :name "Measure"}]
     :auth-backends auth-backends
     :search-system-handler (handler ::search-system)
     :transaction-handler (handler ::transaction)
     :history-system-handler (handler ::history-system)
     :resource-patterns
     [#:blaze.rest-api.resource-pattern
             {:type :default
              :interactions
              {:read
               #:blaze.rest-api.interaction
                       {:handler (handler ::read)}
               :vread
               #:blaze.rest-api.interaction
                       {:handler (handler ::vread)}
               :update
               #:blaze.rest-api.interaction
                       {:handler (handler ::update)}
               :delete
               #:blaze.rest-api.interaction
                       {:handler (handler ::delete)}
               :history-instance
               #:blaze.rest-api.interaction
                       {:handler (handler ::history-instance)}
               :history-type
               #:blaze.rest-api.interaction
                       {:handler (handler ::history-type)}
               :create
               #:blaze.rest-api.interaction
                       {:handler (handler ::create)}
               :search-type
               #:blaze.rest-api.interaction
                       {:handler (handler ::search-type)}}}]
     :compartments
     [#:blaze.rest-api.compartment
             {:code "Patient"
              :search-handler (handler ::search-patient-compartment)}]
     :operations
     [#:blaze.rest-api.operation
             {:code "compact-db"
              :system-handler (handler ::compact-db)}
      #:blaze.rest-api.operation
              {:code "evaluate-measure"
               :resource-types ["Measure"]
               :type-handler (handler ::evaluate-measure-type)
               :instance-handler (handler ::evaluate-measure-instance)}]
     :metadata-handler (handler ::metadata)
     :admin-handler (handler ::admin)}
    (fn [_])))


(def minimal-router
  (rest-api/router
    {:base-url "base-url-111523"
     :structure-definitions [{:kind "resource" :name "Patient"}]
     :resource-patterns
     [#:blaze.rest-api.resource-pattern
             {:type :default
              :interactions
              {:read
               #:blaze.rest-api.interaction
                       {:handler (handler ::read)}}}]}
    (fn [_])))


(deftest router-test
  (testing "compile observe-request-duration middleware"
    (with-redefs [metrics/wrap-observe-request-duration-fn
                  (fn [interaction]
                    (fn [_handler]
                      interaction))]
      (are [path request-method interaction]
        (let [middlewares (get-in (reitit/match-by-path (router []) path) [:result request-method :middleware])
              observe-request-duration (:wrap (some #(when (= :observe-request-duration (:name %)) %) middlewares))]
          (= interaction (observe-request-duration ::handler)))

        "" :get "search-system"
        "" :post "transaction"
        "/_history" :get "history-system"
        "/__page" :get "search-system"
        "/__page" :post "search-system"
        "/Patient" :get "search-type"
        "/Patient" :post "create"
        "/Patient/_history" :get "history-type"
        "/Patient/_search" :post "search-type"
        "/Patient/0" :get "read"
        "/Patient/0" :put "update"
        "/Patient/0" :delete "delete"
        "/Patient/0/_history" :get "history-instance"
        "/Patient/0/_history/42" :get "vread"
        "/Patient/0/Condition" :get "search-compartment"
        "/Patient/0/Observation" :get "search-compartment"
        "/$compact-db" :get "operation-system-compact-db"
        "/$compact-db" :post "operation-system-compact-db"
        "/Measure/$evaluate-measure" :get "operation-type-evaluate-measure"
        "/Measure/$evaluate-measure" :post "operation-type-evaluate-measure"
        "/Measure/0/$evaluate-measure" :get "operation-instance-evaluate-measure"
        "/Measure/0/$evaluate-measure" :post "operation-instance-evaluate-measure"
        "/Measure/0" :get "read"
        "/metadata" :get "capabilities")))

  (testing "handlers"
    (are [path request-method handler]
      (= handler
         ((get-in
            (reitit/match-by-path (router []) path)
            [:result request-method :data :handler])
          {}))
      "" :get ::search-system
      "" :post ::transaction
      "/_history" :get ::history-system
      "/Patient" :get ::search-type
      "/Patient" :post ::create
      "/Patient/_history" :get ::history-type
      "/Patient/_search" :post ::search-type
      "/Patient/0" :get ::read
      "/Patient/0" :put ::update
      "/Patient/0" :delete ::delete
      "/Patient/0/_history" :get ::history-instance
      "/Patient/0/_history/42" :get ::vread
      "/Patient/0/Condition" :get ::search-patient-compartment
      "/Patient/0/Observation" :get ::search-patient-compartment
      "/$compact-db" :get ::compact-db
      "/$compact-db" :post ::compact-db
      "/Measure/$evaluate-measure" :get ::evaluate-measure-type
      "/Measure/$evaluate-measure" :post ::evaluate-measure-type
      "/Measure/0/$evaluate-measure" :get ::evaluate-measure-instance
      "/Measure/0/$evaluate-measure" :post ::evaluate-measure-instance
      "/Measure/0" :get ::read
      "/__metadata/StructureDefinition" :get ::metadata
      "/__admin" :get ::admin
      "/__admin/more" :get ::admin)

    (testing "of minimal router"
      (are [path request-method handler]
        (= handler
           ((get-in
              (reitit/match-by-path minimal-router path)
              [:result request-method :data :handler])
            {}))
        "/Patient/0" :get ::read)))

  (testing "middleware"
    (are [path request-method middleware]
      (= middleware
         (->> (get-in
                (reitit/match-by-path (router []) path)
                [:result request-method :data :middleware])
              (mapv (comp :name #(if (sequential? %) (first %) %)))))
      "" :get [:observe-request-duration :params :output :error :forwarded :sync :search-db :link-headers]
      "" :post [:observe-request-duration :params :output :error :forwarded :sync :resource :wrap-batch-handler]
      "/_history" :get [:observe-request-duration :params :output :error :forwarded :sync :db :link-headers]
      "/__page" :get [:observe-request-duration :params :output :error :forwarded :sync :snapshot-db :link-headers]
      "/__page" :post [:observe-request-duration :params :output :error :forwarded :sync :snapshot-db :link-headers]
      "/Patient" :get [:observe-request-duration :params :output :error :forwarded :sync :search-db :link-headers]
      "/Patient" :post [:observe-request-duration :params :output :error :forwarded :sync :resource]
      "/Patient/_history" :get [:observe-request-duration :params :output :error :forwarded :sync :db :link-headers]
      "/Patient/_search" :post [:observe-request-duration :params :output :error :forwarded :sync :ensure-form-body :db :link-headers]
      "/Patient/__page" :get [:observe-request-duration :params :output :error :forwarded :sync :snapshot-db :link-headers]
      "/Patient/__page" :post [:observe-request-duration :params :output :error :forwarded :sync :snapshot-db :link-headers]
      "/Patient/0" :get [:observe-request-duration :params :output :error :forwarded :sync :db]
      "/Patient/0" :put [:observe-request-duration :params :output :error :forwarded :sync :resource]
      "/Patient/0" :delete [:observe-request-duration :params :output :error :forwarded :sync]
      "/Patient/0/_history" :get [:observe-request-duration :params :output :error :forwarded :sync :db :link-headers]
      "/Patient/0/_history/42" :get [:observe-request-duration :params :output :error :forwarded :sync :versioned-instance-db]
      "/Patient/0/Condition" :get [:observe-request-duration :params :output :error :forwarded :sync :db :link-headers]
      "/Patient/0/Observation" :get [:observe-request-duration :params :output :error :forwarded :sync :db :link-headers]
      "/$compact-db" :get [:observe-request-duration :params :output :error :forwarded :sync :db]
      "/$compact-db" :post [:observe-request-duration :params :output :error :forwarded :sync :db :resource]
      "/Measure/$evaluate-measure" :get [:observe-request-duration :params :output :error :forwarded :sync :db]
      "/Measure/$evaluate-measure" :post [:observe-request-duration :params :output :error :forwarded :sync :db :resource]
      "/Measure/0/$evaluate-measure" :get [:observe-request-duration :params :output :error :forwarded :sync :db]
      "/Measure/0/$evaluate-measure" :post [:observe-request-duration :params :output :error :forwarded :sync :db :resource]
      "/Measure/0" :get [:observe-request-duration :params :output :error :forwarded :sync :db])

    (testing "with auth backends"
      (are [path request-method middleware]
        (= middleware
           (->> (get-in
                  (reitit/match-by-path (router [:auth-backend]) path)
                  [:result request-method :data :middleware])
                (mapv (comp :name #(if (sequential? %) (first %) %)))))
        "" :get [:observe-request-duration :params :output :error :forwarded :sync :auth-guard :search-db :link-headers]
        "" :post [:observe-request-duration :params :output :error :forwarded :sync :auth-guard :resource :wrap-batch-handler]
        "/$compact-db" :get [:observe-request-duration :params :output :error :forwarded :sync :auth-guard :db]
        "/$compact-db" :post [:observe-request-duration :params :output :error :forwarded :sync :auth-guard :db :resource]
        "/Measure/$evaluate-measure" :get [:observe-request-duration :params :output :error :forwarded :sync :auth-guard :db]
        "/Measure/$evaluate-measure" :post [:observe-request-duration :params :output :error :forwarded :sync :auth-guard :db :resource]
        "/Measure/0/$evaluate-measure" :get [:observe-request-duration :params :output :error :forwarded :sync :auth-guard :db]
        "/Measure/0/$evaluate-measure" :post [:observe-request-duration :params :output :error :forwarded :sync :auth-guard :db :resource])))

  (testing "Patient instance POST is not allowed"
    (given (call (reitit.ring/ring-handler (router []) handler-util/default-handler)
                 {:uri "/Patient/0" :request-method :post})
      :status := 405
      [:body :fhir/type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"processing"
      [:body :issue 0 :diagnostics] := "Method POST not allowed on `/Patient/0` endpoint."))

  (testing "Patient type PUT is not allowed"
    (given (call (reitit.ring/ring-handler (router []) handler-util/default-handler)
                 {:uri "/Patient" :request-method :put})
      :status := 405
      [:body :fhir/type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"processing"
      [:body :issue 0 :diagnostics] := "Method PUT not allowed on `/Patient` endpoint."))

  (testing "Observations are not found"
    (given (call (reitit.ring/ring-handler (router []) handler-util/default-handler)
                 {:uri "/Observation" :request-method :get})
      :status := 404
      [:body :fhir/type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"not-found")))


(deftest router-match-by-name-test
  (are [name params path]
    (= (reitit/match->path (reitit/match-by-name (router []) name params)) path)

    :Patient/type
    {}
    "/Patient"

    :Patient/instance
    {:id "23"}
    "/Patient/23"

    :Patient/versioned-instance
    {:id "23" :vid "42"}
    "/Patient/23/_history/42"))


(deftest default-options-handler-test
  (testing "without match"
    (given @(rest-api/default-options-handler {})
      :status := 204
      [:headers "Access-Control-Allow-Headers"] := "content-type"))

  (testing "with one :get match"
    (given @(rest-api/default-options-handler {::reitit/match {:result {:get {}}}})
      :status := 204
      [:headers "Access-Control-Allow-Methods"] := "GET"))

  (testing "with one :get and one :post match"
    (given @(rest-api/default-options-handler {::reitit/match {:result {:get {} :post {}}}})
      :status := 204
      [:headers "Access-Control-Allow-Methods"] := "GET,POST")))


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze/rest-api nil})
      :key := :blaze/rest-api
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze/rest-api {}})
      :key := :blaze/rest-api
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :base-url))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :version))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :structure-definition-repo))
      [:explain ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:explain ::s/problems 4 :pred] := `(fn ~'[%] (contains? ~'% :search-param-registry))
      [:explain ::s/problems 5 :pred] := `(fn ~'[%] (contains? ~'% :db-sync-timeout))))

  (testing "invalid enforce-referential-integrity"
    (given-thrown (ig/init {:blaze/rest-api {:enforce-referential-integrity ::invalid}})
      :key := :blaze/rest-api
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :base-url))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :version))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :structure-definition-repo))
      [:explain ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:explain ::s/problems 4 :pred] := `(fn ~'[%] (contains? ~'% :search-param-registry))
      [:explain ::s/problems 5 :pred] := `(fn ~'[%] (contains? ~'% :db-sync-timeout))
      [:explain ::s/problems 6 :pred] := `boolean?
      [:explain ::s/problems 6 :val] := ::invalid)))


(deftest requests-total-collector-init-test
  (with-system [{collector :blaze.rest-api/requests-total} {:blaze.rest-api/requests-total {}}]
    (is (s/valid? :blaze.metrics/collector collector))))


(deftest request-duration-seconds-collector-init-test
  (with-system [{collector :blaze.rest-api/request-duration-seconds} {:blaze.rest-api/request-duration-seconds {}}]
    (is (s/valid? :blaze.metrics/collector collector))))


(deftest parse-duration-seconds-collector-init-test
  (with-system [{collector :blaze.rest-api/parse-duration-seconds} {:blaze.rest-api/parse-duration-seconds {}}]
    (is (s/valid? :blaze.metrics/collector collector))))


(deftest generate-duration-seconds-collector-init-test
  (with-system [{collector :blaze.rest-api/generate-duration-seconds} {:blaze.rest-api/generate-duration-seconds {}}]
    (is (s/valid? :blaze.metrics/collector collector))))


(def ^:private success-handler
  (constantly (ac/completed-future (ring/status 200))))


(def ^:private system
  (assoc mem-node-config
    :blaze/rest-api
    {:base-url "http://localhost:8080"
     :version "0.1.0"
     :structure-definition-repo structure-definition-repo
     :node (ig/ref :blaze.db/node)
     :search-param-registry (ig/ref :blaze.db/search-param-registry)
     :db-sync-timeout 10000
     :auth-backends []
     :search-system-handler success-handler
     :transaction-handler success-handler
     :resource-patterns
     [#:blaze.rest-api.resource-pattern
             {:type :default
              :interactions
              {:read
               #:blaze.rest-api.interaction
                       {:handler success-handler}
               :delete
               #:blaze.rest-api.interaction
                       {:handler success-handler}
               :search-type
               #:blaze.rest-api.interaction
                       {:handler success-handler}}}]}
    :blaze.db/search-param-registry
    {:structure-definition-repo structure-definition-repo}))


(defmethod ig/init-key ::empty-structure-definition-repo
  [_ _]
  (reify sdrp/StructureDefinitionRepo
    (-primitive-types [_] [])
    (-complex-types [_] [])
    (-resources [_] [])))


(deftest format-override-test
  (testing "XML"
    (with-system [{:blaze/keys [rest-api]} system]
      (given (call rest-api {:request-method :get :uri "/metadata" :query-string "_format=xml"})
        :status := 200
        [:headers "Content-Type"] := "application/fhir+xml;charset=utf-8"))))


(deftest base-url-test
  (testing "metadata"
    (with-system [{:blaze/keys [rest-api]} system]
      (given (call rest-api {:request-method :get :uri "/metadata"})
        :status := 200
        [:body fhir-spec/parse-json :implementation :url] := "http://localhost:8080"))

    (testing "with X-Forwarded-Host header"
      (with-system [{:blaze/keys [rest-api]} system]
        (given (call rest-api
                     {:request-method :get
                      :uri "/metadata"
                      :headers {"x-forwarded-host" "blaze.de"}})
          :status := 200
          [:body fhir-spec/parse-json :implementation :url] := "http://blaze.de")))))


(deftest options-cors-test
  (with-system [{:blaze/keys [rest-api]} system]
    (given (call rest-api {:request-method :options :uri "/metadata"})
      :status := 204
      [:headers "Access-Control-Allow-Headers"] := "content-type"
      [:headers "Access-Control-Allow-Methods"] := "GET,OPTIONS")))


(deftest not-found-test
  (with-system [{:blaze/keys [rest-api]} system]
    (given (call rest-api {:request-method :get :uri "/foo"})
      :status := 404
      [:body fhir-spec/parse-json :resourceType] := "OperationOutcome"))

  (testing "with text/html accept header"
    (with-system [{:blaze/keys [rest-api]} system]
      (given (call rest-api {:request-method :get :uri "/foo"
                             :headers {"accept" "text/html"}})
        :status := 404
        :body := nil))))


(deftest method-not-allowed-test
  (with-system [{:blaze/keys [rest-api]} system]
    (given (call rest-api {:request-method :put :uri "/metadata"})
      :status := 405
      [:body fhir-spec/parse-json :resourceType] := "OperationOutcome"))

  (testing "with text/html accept header"
    (with-system [{:blaze/keys [rest-api]} system]
      (given (call rest-api {:request-method :put :uri "/metadata"
                             :headers {"accept" "text/html"}})
        :status := 405
        :body := nil))))


(deftest not-acceptable-test
  (with-system [{:blaze/keys [rest-api]} system]
    (given (call rest-api {:request-method :get :uri "/metadata"
                           :headers {"accept" "text/plain"}})
      :status := 406
      :body := nil)))


(defn empty-input-stream []
  (ByteArrayInputStream. (byte-array 0)))


(deftest search-type-test
  (testing "using POST"
    (with-system [{:blaze/keys [rest-api]} system]
      (given (call rest-api {:request-method :post :uri "/Patient/_search"
                             :headers {"content-type" "application/x-www-form-urlencoded"}
                             :body (empty-input-stream)})
        :status := 200))

    (testing "without Content-Type header"
      (with-system [{:blaze/keys [rest-api]} system]
        (let [request {:request-method :post :uri "/Patient/_search"
                       :body (empty-input-stream)}
              {:keys [status body]} (call rest-api request)]

          (is (= 415 status))

          (given (fhir-spec/parse-json body)
            :resourceType := "OperationOutcome"
            [:issue 0 :severity] := "error"
            [:issue 0 :code] := "invalid"
            [:issue 0 :diagnostics] := "Missing Content-Type header. Please use `application/x-www-form-urlencoded`."))))

    (testing "with unsupported media-type"
      (with-system [{:blaze/keys [rest-api]} system]
        (let [request {:request-method :post :uri "/Patient/_search"
                       :headers {"content-type" "application/fhir+json"}
                       :body (empty-input-stream)}
              {:keys [status body]} (call rest-api request)]

          (is (= 415 status))

          (given (fhir-spec/parse-json body)
            :resourceType := "OperationOutcome"
            [:issue 0 :severity] := "error"
            [:issue 0 :code] := "invalid"
            [:issue 0 :diagnostics] := "Unsupported Content-Type header `application/fhir+json`. Please use `application/x-www-form-urlencoded`."))))))
