(ns blaze.system-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api-stub :refer [mem-node-config]]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.interaction.delete]
   [blaze.interaction.history.type]
   [blaze.interaction.read]
   [blaze.interaction.search-system]
   [blaze.interaction.search-type]
   [blaze.interaction.transaction]
   [blaze.module.test-util :refer [with-system]]
   [blaze.module.test-util.ring :refer [call]]
   [blaze.page-store.protocols :as pp]
   [blaze.rest-api]
   [blaze.system :as system]
   [blaze.system-spec]
   [blaze.test-util :as tu]
   [buddy.auth.protocols :as ap]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [java.io ByteArrayInputStream]))

(st/instrument)
(log/set-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest resolve-config-test
  (are [config env res] (= res (system/resolve-config config env))
    {:a (system/->Cfg "SERVER_PORT" (s/spec nat-int?) 8080)}
    {"SERVER_PORT" "80"}
    {:a 80
     :blaze/admin-api
     {:settings
      [{:name "SERVER_PORT"
        :value 80
        :default-value 8080}]}}

    {:a (system/->Cfg "SERVER_PORT" (s/spec nat-int?) 8080)}
    nil
    {:a 8080
     :blaze/admin-api
     {:settings
      [{:name "SERVER_PORT"
        :value 8080
        :default-value 8080}]}}

    {:a (system/->Cfg "SERVER_PORT" (s/spec nat-int?) 8080)}
    {"SERVER_PORT" "a"}
    {:a ::s/invalid
     :blaze/admin-api
     {:settings
      [{:name "SERVER_PORT"
        :value ::s/invalid
        :default-value 8080}]}})

  (testing "Blank env vars are handled same as missing ones"
    (are [config env res] (= res (system/resolve-config config env))
      {:a (system/->Cfg "PROXY_HOST" (s/spec string?) nil)}
      {"PROXY_HOST" ""}
      {:a nil :blaze/admin-api {:settings []}}

      {:a (system/->Cfg "PROXY_HOST" (s/spec string?) nil)}
      {}
      {:a nil :blaze/admin-api {:settings []}}

      {:a (system/->Cfg "PROXY_HOST" (s/spec string?) "default")}
      {"PROXY_HOST" ""}
      {:a "default"
       :blaze/admin-api
       {:settings
        [{:name "PROXY_HOST"
          :value "default"
          :default-value "default"}]}}

      {:a (system/->Cfg "PROXY_HOST" (s/spec string?) "default")}
      {}
      {:a "default"
       :blaze/admin-api
       {:settings
        [{:name "PROXY_HOST"
          :value "default"
          :default-value "default"}]}})))

(def config
  (assoc mem-node-config
         :blaze/rest-api
         {:base-url "http://localhost:8080"
          :version "0.1.0"
          :release-date "2024-01-07"
          :structure-definition-repo structure-definition-repo
          :node (ig/ref :blaze.db/node)
          :search-param-registry (ig/ref :blaze.db/search-param-registry)
          :db-sync-timeout 10000
          :auth-backends [(ig/ref ::auth-backend)]
          :search-system-handler (ig/ref :blaze.interaction/search-system)
          :transaction-handler (ig/ref :blaze.interaction/transaction)
          :resource-patterns
          [#:blaze.rest-api.resource-pattern
            {:type :default
             :interactions
             {:read
              #:blaze.rest-api.interaction
               {:handler (ig/ref :blaze.interaction/read)}
              :delete
              #:blaze.rest-api.interaction
               {:handler (ig/ref :blaze.interaction/delete)}
              :search-type
              #:blaze.rest-api.interaction
               {:handler (ig/ref :blaze.interaction/search-type)}
              :history-type
              #:blaze.rest-api.interaction
               {:handler (ig/ref :blaze.interaction.history/type)}}}]}
         :blaze.db/search-param-registry
         {:structure-definition-repo structure-definition-repo}
         ::auth-backend {}
         :blaze.interaction/transaction
         {:node (ig/ref :blaze.db/node)
          :executor (ig/ref :blaze.test/executor)
          :clock (ig/ref :blaze.test/fixed-clock)
          :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}
         :blaze.interaction/read {}
         :blaze.interaction/delete
         {:node (ig/ref :blaze.db/node)}
         :blaze.interaction/search-system
         {:clock (ig/ref :blaze.test/fixed-clock)
          :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
          :page-store (ig/ref ::page-store)}
         :blaze.interaction/search-type
         {:clock (ig/ref :blaze.test/fixed-clock)
          :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
          :page-store (ig/ref ::page-store)}
         :blaze.interaction.history/type
         {:clock (ig/ref :blaze.test/fixed-clock)
          :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}
         :blaze.test/executor {}
         :blaze.test/fixed-clock {}
         :blaze.test/fixed-rng-fn {}
         ::page-store {}))

(defmethod ig/init-key ::auth-backend
  [_ _]
  (reify ap/IAuthentication
    (-parse [_ _] ::token)
    (-authenticate [_ _ data] (when (= ::token data) ::identity))))

(defmethod ig/init-key ::page-store
  [_ _]
  (reify pp/PageStore
    (-get [_ _])
    (-put [_ _] (ac/completed-future "token"))))

(defn input-stream [bs]
  (ByteArrayInputStream. bs))

(def search-bundle
  {:fhir/type :fhir/Bundle
   :type #fhir/code"batch"
   :entry
   [{:fhir/type :fhir.Bundle/entry
     :request
     {:fhir/type :fhir.Bundle.entry/request
      :method #fhir/code"GET"
      :url #fhir/uri"/Patient"}}]})

(deftest auth-test
  (with-system [{:blaze/keys [rest-api]} config]
    (testing "Patient search"
      (given (call rest-api {:request-method :get :uri "/Patient"})
        :status := 200))

    (testing "Patient search as batch request"
      (given (call rest-api {:request-method :post :uri ""
                             :headers {"content-type" "application/fhir+json"}
                             :body (input-stream (fhir-spec/unform-json search-bundle))})
        :status := 200
        [:body fhir-spec/parse-json :entry 0 :response :status] := "200"))))

(deftest not-found-test
  (with-system [{:blaze/keys [rest-api]} config]
    (given (call rest-api {:request-method :get :uri "/"})
      :status := 404
      [:body fhir-spec/parse-json :resourceType] := "OperationOutcome")))

(deftest not-acceptable-test
  (with-system [{:blaze/keys [rest-api]} config]
    (given (call rest-api {:request-method :get :uri "/Patient"
                           :headers {"accept" "text/plain"}})
      :status := 406
      :body := nil)))

(deftest read-test
  (with-system [{:blaze/keys [rest-api]} config]
    (given (call rest-api {:request-method :get :uri "/Patient/0"})
      :status := 404
      [:body fhir-spec/parse-json :resourceType] := "OperationOutcome")))

(def read-bundle
  {:fhir/type :fhir/Bundle
   :type #fhir/code"batch"
   :entry
   [{:fhir/type :fhir.Bundle/entry
     :request
     {:fhir/type :fhir.Bundle.entry/request
      :method #fhir/code"GET"
      :url #fhir/uri"/Patient/0"}}]})

(deftest batch-read-test
  (with-system [{:blaze/keys [rest-api]} config]
    (given (call rest-api {:request-method :post :uri ""
                           :headers {"content-type" "application/fhir+json"}
                           :body (input-stream (fhir-spec/unform-json read-bundle))})
      :status := 200
      [:body fhir-spec/parse-json :entry 0 :response :status] := "404"
      [:body fhir-spec/parse-json :entry 0 :response :outcome :resourceType] := "OperationOutcome")))

(deftest batch-unsupported-media-type-test
  (with-system [{:blaze/keys [rest-api]} config]
    (given (call rest-api {:request-method :post :uri ""
                           :headers {"content-type" "text/plain"}})
      :status := 415
      [:body fhir-spec/parse-json :resourceType] := "OperationOutcome")))

(def metadata-bundle
  {:fhir/type :fhir/Bundle
   :type #fhir/code"batch"
   :entry
   [{:fhir/type :fhir.Bundle/entry
     :request
     {:fhir/type :fhir.Bundle.entry/request
      :method #fhir/code"GET"
      :url #fhir/uri"metadata"}}]})

(deftest batch-metadata-test
  (with-system [{:blaze/keys [rest-api]} config]
    (given (call rest-api {:request-method :post :uri ""
                           :headers {"content-type" "application/fhir+json"}
                           :body (input-stream (fhir-spec/unform-json metadata-bundle))})
      :status := 200
      [:body fhir-spec/parse-json :entry 0 :resource :resourceType] := "CapabilityStatement"
      [:body fhir-spec/parse-json :entry 0 :response :status] := "200")))

(deftest delete-test
  (with-system [{:blaze/keys [rest-api]} config]
    (given (call rest-api {:request-method :delete :uri "/Patient/0"})
      :status := 204
      :body := nil)

    (given (call rest-api {:request-method :get :uri "/Patient/0"})
      :status := 410
      [:body fhir-spec/parse-json :resourceType] := "OperationOutcome")))

(deftest search-system-test
  (with-system [{:blaze/keys [rest-api]} config]
    (given (call rest-api {:request-method :get :uri ""})
      :status := 200
      [:body fhir-spec/parse-json :resourceType] := "Bundle")))

(deftest search-type-test
  (testing "using GET"
    (with-system [{:blaze/keys [rest-api]} config]
      (given (call rest-api {:request-method :get :uri "/Patient"})
        :status := 200
        [:headers "Link"] := "<http://localhost:8080/Patient?_count=50>;rel=\"self\""
        [:body fhir-spec/parse-json :resourceType] := "Bundle")))

  (testing "using POST"
    (with-system [{:blaze/keys [rest-api]} config]
      (given (call rest-api {:request-method :post :uri "/Patient/_search"
                             :headers {"content-type" "application/x-www-form-urlencoded"}
                             :body (input-stream (byte-array 0))})
        :status := 200
        [:body fhir-spec/parse-json :resourceType] := "Bundle"))

    (testing "with unsupported media-type"
      (with-system [{:blaze/keys [rest-api]} config]
        (given (call rest-api {:request-method :post :uri "/Patient/_search"
                               :headers {"content-type" "application/fhir+json"}
                               :body (input-stream (byte-array 0))})
          :status := 415
          [:body fhir-spec/parse-json :resourceType] := "OperationOutcome")))))

(deftest history-type-test
  (with-system [{:blaze/keys [rest-api]} config]
    (given (call rest-api {:request-method :get :uri "/Patient/_history"})
      :status := 200
      [:headers "Link"] := "<http://localhost:8080/Patient/_history>;rel=\"self\""
      [:body fhir-spec/parse-json :resourceType] := "Bundle")))

(deftest redirect-slash-test
  (with-system [{:blaze/keys [rest-api]} config]
    (given (call rest-api {:request-method :get :uri "/Patient/"})
      :status := 301
      [:headers "Location"] := "/Patient")))
