(ns blaze.rest-api-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api-stub :refer [mem-node-config]]
   [blaze.db.impl.search-param]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.structure-definition-repo.protocols :as sdrp]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.job-scheduler]
   [blaze.metrics.spec]
   [blaze.module.test-util :refer [with-system]]
   [blaze.module.test-util.ring :refer [call]]
   [blaze.rest-api :as rest-api]
   [blaze.rest-api.capabilities-handler]
   [blaze.rest-api.routes-spec]
   [blaze.test-util :as tu :refer [given-thrown]]
   [buddy.auth.protocols :as ap]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [reitit.core :as-alias reitit]
   [reitit.ring]
   [ring.util.response :as ring]
   [taoensso.timbre :as log])
  (:import
   [java.io ByteArrayInputStream]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

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
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze/rest-api {}})
      :key := :blaze/rest-api
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :base-url))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :structure-definition-repo))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :admin-node))
      [:cause-data ::s/problems 4 :pred] := `(fn ~'[%] (contains? ~'% :job-scheduler))
      [:cause-data ::s/problems 5 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 6 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 7 :pred] := `(fn ~'[%] (contains? ~'% :async-status-handler))
      [:cause-data ::s/problems 8 :pred] := `(fn ~'[%] (contains? ~'% :async-status-cancel-handler))
      [:cause-data ::s/problems 9 :pred] := `(fn ~'[%] (contains? ~'% :capabilities-handler))
      [:cause-data ::s/problems 10 :pred] := `(fn ~'[%] (contains? ~'% :db-sync-timeout)))))

(deftest requests-total-collector-init-test
  (with-system [{collector ::rest-api/requests-total} {::rest-api/requests-total {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(deftest request-duration-seconds-collector-init-test
  (with-system [{collector ::rest-api/request-duration-seconds} {::rest-api/request-duration-seconds {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(deftest parse-duration-seconds-collector-init-test
  (with-system [{collector ::rest-api/parse-duration-seconds} {::rest-api/parse-duration-seconds {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(deftest generate-duration-seconds-collector-init-test
  (with-system [{collector ::rest-api/generate-duration-seconds} {::rest-api/generate-duration-seconds {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(deftest resource-patterns-init-test
  (with-system [{::rest-api/keys [resource-patterns]} {::rest-api/resource-patterns ::patterns}]
    (is (= ::patterns resource-patterns))))

(deftest operations-init-test
  (with-system [{::rest-api/keys [operations]} {::rest-api/operations ::operations}]
    (is (= ::operations operations))))

(def ^:private success-handler
  (constantly (ac/completed-future (ring/status 200))))

(def ^:private config
  (assoc
   mem-node-config
   :blaze/rest-api
   {:base-url "http://localhost:8080"
    :structure-definition-repo structure-definition-repo
    :node (ig/ref :blaze.db/node)
    :admin-node (ig/ref :blaze.db/node)
    :job-scheduler (ig/ref :blaze/job-scheduler)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :async-status-handler success-handler
    :async-status-cancel-handler success-handler
    :capabilities-handler (ig/ref ::rest-api/capabilities-handler)
    :db-sync-timeout 10000
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
        :conditional-delete-type
        #:blaze.rest-api.interaction
         {:handler success-handler}
        :search-type
        #:blaze.rest-api.interaction
         {:handler success-handler}}}]}
   :blaze/job-scheduler
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}
   ::rest-api/capabilities-handler
   {:version "version-131640"
    :release-date "2024-05-23"
    :structure-definition-repo structure-definition-repo
    :search-param-registry (ig/ref :blaze.db/search-param-registry)}
   :blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo}
   :blaze.test/fixed-rng-fn {}))

(defmethod ig/init-key ::empty-structure-definition-repo
  [_ _]
  (reify sdrp/StructureDefinitionRepo
    (-primitive-types [_] [])
    (-complex-types [_] [])
    (-resources [_] [])))

(deftest format-override-test
  (testing "XML"
    (with-system [{:blaze/keys [rest-api]} config]
      (given (call rest-api {:request-method :get :uri "/metadata" :query-string "_format=xml"})
        :status := 200
        [:headers "Content-Type"] := "application/fhir+xml;charset=utf-8"))))

(deftest base-url-test
  (testing "metadata"
    (with-system [{:blaze/keys [rest-api]} config]
      (given (call rest-api {:request-method :get :uri "/metadata"})
        :status := 200
        [:body fhir-spec/parse-json :implementation :url] := "http://localhost:8080"))

    (testing "with X-Forwarded-Host header"
      (with-system [{:blaze/keys [rest-api]} config]
        (given (call rest-api
                     {:request-method :get
                      :uri "/metadata"
                      :headers {"x-forwarded-host" "blaze.de"}})
          :status := 200
          [:body fhir-spec/parse-json :implementation :url] := "http://blaze.de")))))

(deftest options-cors-test
  (with-system [{:blaze/keys [rest-api]} config]
    (given (call rest-api {:request-method :options :uri "/metadata"})
      :status := 204
      [:headers "Access-Control-Allow-Headers"] := "content-type"
      [:headers "Access-Control-Allow-Methods"] := "GET,OPTIONS")))

(deftest not-found-test
  (with-system [{:blaze/keys [rest-api]} config]
    (let [{:keys [status body]} (call rest-api {:request-method :get :uri "/foo"})]

      (is (= 404 status))

      (given (fhir-spec/conform-json (fhir-spec/parse-json body))
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"not-found")))

  (testing "with text/html accept header"
    (with-system [{:blaze/keys [rest-api]} config]
      (given (call rest-api {:request-method :get :uri "/foo"
                             :headers {"accept" "text/html"}})
        :status := 404
        :body := nil))))

(deftest method-not-allowed-test
  (with-system [{:blaze/keys [rest-api]} config]
    (given (call rest-api {:request-method :put :uri "/metadata"})
      :status := 405
      [:body fhir-spec/parse-json :resourceType] := "OperationOutcome"))

  (testing "with text/html accept header"
    (with-system [{:blaze/keys [rest-api]} config]
      (given (call rest-api {:request-method :put :uri "/metadata"
                             :headers {"accept" "text/html"}})
        :status := 405
        :body := nil)))

  (testing "Patient instance POST is not allowed"
    (with-system [{:blaze/keys [rest-api]} config]
      (let [{:keys [status body]} (call rest-api {:request-method :post :uri "/Patient/0"})]

        (is (= 405 status))

        (given (fhir-spec/conform-json (fhir-spec/parse-json body))
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code"error"
          [:issue 0 :code] := #fhir/code"processing"
          [:issue 0 :diagnostics] := "Method POST not allowed on `/Patient/0` endpoint."))))

  (testing "Patient instance PUT is not allowed"
    (with-system [{:blaze/keys [rest-api]} config]
      (let [{:keys [status body]} (call rest-api {:request-method :put :uri "/Patient/0"})]

        (is (= 405 status))

        (given (fhir-spec/conform-json (fhir-spec/parse-json body))
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code"error"
          [:issue 0 :code] := #fhir/code"processing"
          [:issue 0 :diagnostics] := "Method PUT not allowed on `/Patient/0` endpoint.")))))

(deftest not-acceptable-test
  (with-system [{:blaze/keys [rest-api]} config]
    (given (call rest-api {:request-method :get :uri "/metadata"
                           :headers {"accept" "text/plain"}})
      :status := 406
      :body := nil)))

(defn input-stream
  ([]
   (ByteArrayInputStream. (byte-array 0)))
  ([resource]
   (ByteArrayInputStream. (fhir-spec/unform-json resource))))

(deftest batch-test
  (with-system [{:blaze/keys [rest-api]} config]
    (given (call rest-api {:request-method :post :uri ""
                           :headers {"content-type" "application/fhir+json"}
                           :body (input-stream {:fhir/type :fhir/Bundle})})
      :status := 200)))

(deftest search-type-test
  (testing "using POST"
    (with-system [{:blaze/keys [rest-api]} config]
      (given (call rest-api {:request-method :post :uri "/Patient/_search"
                             :headers {"content-type" "application/x-www-form-urlencoded"}
                             :body (input-stream)})
        :status := 200))

    (testing "without Content-Type header"
      (with-system [{:blaze/keys [rest-api]} config]
        (let [request {:request-method :post :uri "/Patient/_search"
                       :body (input-stream)}
              {:keys [status body]} (call rest-api request)]

          (is (= 415 status))

          (given (fhir-spec/parse-json body)
            :resourceType := "OperationOutcome"
            [:issue 0 :severity] := "error"
            [:issue 0 :code] := "invalid"
            [:issue 0 :diagnostics] := "Missing Content-Type header. Please use `application/x-www-form-urlencoded`."))))

    (testing "with unsupported media-type"
      (with-system [{:blaze/keys [rest-api]} config]
        (let [request {:request-method :post :uri "/Patient/_search"
                       :headers {"content-type" "application/fhir+json"}
                       :body (input-stream)}
              {:keys [status body]} (call rest-api request)]

          (is (= 415 status))

          (given (fhir-spec/parse-json body)
            :resourceType := "OperationOutcome"
            [:issue 0 :severity] := "error"
            [:issue 0 :code] := "invalid"
            [:issue 0 :diagnostics] := "Unsupported Content-Type header `application/fhir+json`. Please use `application/x-www-form-urlencoded`."))))))

(deftest conditional-delete-type-test
  (with-system [{:blaze/keys [rest-api]} config]
    (given (call rest-api {:request-method :delete :uri "/Patient"})
      :status := 200)))

(def auth-config
  (-> (assoc-in config [:blaze/rest-api :auth-backends] [(ig/ref ::auth-backend)])
      (assoc ::auth-backend {})))

(defmethod ig/init-key ::auth-backend
  [_ _]
  (reify ap/IAuthentication
    (-parse [_ _] ::token)
    (-authenticate [_ _ data] (when (= ::token data) ::identity))))

(deftest auth-test
  (with-system [{:blaze/keys [rest-api]} auth-config]
    (testing "Patient search"
      (given (call rest-api {:request-method :get :uri "/Patient"})
        :status := 200))))
