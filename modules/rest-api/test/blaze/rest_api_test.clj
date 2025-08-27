(ns blaze.rest-api-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.api-stub :refer [mem-node-config]]
   [blaze.db.impl.search-param]
   [blaze.fhir.parsing-context]
   [blaze.fhir.structure-definition-repo.protocols :as sdrp]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.fhir.writing-context]
   [blaze.job-scheduler]
   [blaze.metrics.spec]
   [blaze.middleware.fhir.output-spec]
   [blaze.module.test-util :refer [given-failed-future given-failed-system with-system]]
   [blaze.module.test-util.ring :as tu-r]
   [blaze.rest-api :as rest-api]
   [blaze.rest-api.capabilities-handler]
   [blaze.rest-api.routes-spec]
   [blaze.terminology-service :as-alias ts]
   [blaze.terminology-service.local :as ts-local]
   [blaze.test-util :as tu]
   [buddy.auth.protocols :as ap]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [reitit.ring]
   [ring.core.protocols :as rp]
   [ring.util.response :as ring]
   [taoensso.timbre :as log])
  (:import
   [java.io ByteArrayOutputStream]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze/rest-api nil}
      :key := :blaze/rest-api
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze/rest-api {}}
      :key := :blaze/rest-api
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :base-url))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :parsing-context))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :writing-context))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :structure-definition-repo))
      [:cause-data ::s/problems 4 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:cause-data ::s/problems 5 :pred] := `(fn ~'[%] (contains? ~'% :admin-node))
      [:cause-data ::s/problems 6 :pred] := `(fn ~'[%] (contains? ~'% :job-scheduler))
      [:cause-data ::s/problems 7 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 8 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 9 :pred] := `(fn ~'[%] (contains? ~'% :async-status-handler))
      [:cause-data ::s/problems 10 :pred] := `(fn ~'[%] (contains? ~'% :async-status-cancel-handler))
      [:cause-data ::s/problems 11 :pred] := `(fn ~'[%] (contains? ~'% :capabilities-handler))
      [:cause-data ::s/problems 12 :pred] := `(fn ~'[%] (contains? ~'% :db-sync-timeout)))))

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
  (with-system [{::rest-api/keys [resource-patterns]} {::rest-api/resource-patterns {:default ::interactions}}]
    (is (= [#:blaze.rest-api.resource-pattern{:type :default :interactions ::interactions}] resource-patterns))))

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
    :parsing-context (ig/ref :blaze.fhir.parsing-context/default)
    :writing-context (ig/ref :blaze.fhir/writing-context)
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
    :page-id-cipher (ig/ref :blaze.test/page-id-cipher)
    :search-system-handler success-handler
    :transaction-handler success-handler
    :resource-patterns (ig/ref ::rest-api/resource-patterns)}
   ::rest-api/resource-patterns
   {:default
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
      {:handler success-handler}}}
   :blaze/job-scheduler
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}
   ::rest-api/capabilities-handler
   {:fhir/version "4.0.1"
    :version "version-131640"
    :release-date "2024-05-23"
    :structure-definition-repo structure-definition-repo
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
    :terminology-service (ig/ref ::ts/local)}
   :blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo}
   ::ts/local
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :graph-cache (ig/ref ::ts-local/graph-cache)}
   :blaze.test/fixed-rng-fn {}
   :blaze.test/page-id-cipher {}
   ::ts-local/graph-cache {}
   :blaze.test/json-parser
   {:parsing-context (ig/ref :blaze.fhir.parsing-context/default)}
   :blaze.test/json-writer
   {:writing-context (ig/ref :blaze.fhir/writing-context)}
   [:blaze.fhir/parsing-context :blaze.fhir.parsing-context/default]
   {:structure-definition-repo structure-definition-repo}
   :blaze.fhir/writing-context
   {:structure-definition-repo structure-definition-repo}))

(defmethod ig/init-key ::empty-structure-definition-repo
  [_ _]
  (reify sdrp/StructureDefinitionRepo
    (-primitive-types [_] [])
    (-complex-types [_] [])
    (-resources [_] [])))

(defn- read-value [body]
  (when body
    (let [out (ByteArrayOutputStream.)]
      (rp/write-body-to-stream body nil out)
      (.toByteArray out))))

(defn- wrap-read-value [handler]
  (fn [request respond raise]
    (handler request #(respond (update % :body read-value)) raise)))

(defn- call [handler request]
  (tu-r/call (wrap-read-value handler) request))

(deftest structure-definition-test
  (testing "StructureDefinition resources are created"
    (with-system [{:blaze.db/keys [node]} config]
      (is (< 100 (d/type-total (d/db node) "StructureDefinition")))))

  (testing "can't delete the Patient StructureDefinition"
    (with-system [{:blaze.db/keys [node]} config]
      (let [[{:keys [id]}] (vec (d/type-query (d/db node) "StructureDefinition" [["url" "http://hl7.org/fhir/StructureDefinition/Patient"]]))]
        (given-failed-future (d/transact node [[:delete "StructureDefinition" id]])
          ::anom/category := ::anom/conflict
          ::anom/message := (format "Can't delete the read-only resource `StructureDefinition/%s`." id))))))

(deftest format-override-test
  (testing "XML"
    (with-system [{:blaze/keys [rest-api]} config]
      (given (call rest-api {:request-method :get :uri "/metadata" :query-string "_format=xml"})
        :status := 200
        [:headers "Content-Type"] := "application/fhir+xml;charset=utf-8"))))

(deftest base-url-test
  (testing "metadata"
    (with-system [{:blaze/keys [rest-api] :blaze.test/keys [json-parser]} config]
      (given (call rest-api {:request-method :get :uri "/metadata"})
        :status := 200
        [:body json-parser :implementation :url] := #fhir/url"http://localhost:8080"))

    (testing "with X-Forwarded-Host header"
      (with-system [{:blaze/keys [rest-api] :blaze.test/keys [json-parser]} config]
        (given (call rest-api
                     {:request-method :get
                      :uri "/metadata"
                      :headers {"x-forwarded-host" "blaze.de"}})
          :status := 200
          [:body json-parser :implementation :url] := #fhir/url"http://blaze.de")))))

(deftest not-found-test
  (with-system [{:blaze/keys [rest-api] :blaze.test/keys [json-parser]} config]
    (let [{:keys [status body]} (call rest-api {:request-method :get :uri "/foo"})]

      (is (= 404 status))

      (given (json-parser body)
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
  (with-system [{:blaze/keys [rest-api] :blaze.test/keys [json-parser]} config]
    (given (call rest-api {:request-method :put :uri "/metadata"})
      :status := 405
      [:body json-parser :fhir/type] := :fhir/OperationOutcome))

  (testing "with text/html accept header"
    (with-system [{:blaze/keys [rest-api]} config]
      (given (call rest-api {:request-method :put :uri "/metadata"
                             :headers {"accept" "text/html"}})
        :status := 405
        :body := nil)))

  (testing "Patient instance POST is not allowed"
    (with-system [{:blaze/keys [rest-api] :blaze.test/keys [json-parser]} config]
      (let [{:keys [status body]} (call rest-api {:request-method :post :uri "/Patient/0"})]

        (is (= 405 status))

        (given (json-parser body)
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code"error"
          [:issue 0 :code] := #fhir/code"processing"
          [:issue 0 :diagnostics] := "Method POST not allowed on `/Patient/0` endpoint."))))

  (testing "Patient instance PUT is not allowed"
    (with-system [{:blaze/keys [rest-api] :blaze.test/keys [json-parser]} config]
      (let [{:keys [status body]} (call rest-api {:request-method :put :uri "/Patient/0"})]

        (is (= 405 status))

        (given (json-parser body)
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

(deftest batch-test
  (with-system [{:blaze/keys [rest-api] :blaze.test/keys [json-writer]} config]
    (given (call rest-api {:request-method :post :uri ""
                           :headers {"content-type" "application/fhir+json"}
                           :body (json-writer {:fhir/type :fhir/Bundle})})
      :status := 200)))

(deftest search-type-test
  (testing "using POST"
    (with-system [{:blaze/keys [rest-api]} config]
      (given (call rest-api {:request-method :post :uri "/Patient/_search"
                             :headers {"content-type" "application/x-www-form-urlencoded"}
                             :body (byte-array 0)})
        :status := 200))

    (testing "without Content-Type header"
      (with-system [{:blaze/keys [rest-api] :blaze.test/keys [json-parser]} config]
        (let [request {:request-method :post :uri "/Patient/_search"
                       :body (byte-array 0)}
              {:keys [status body]} (call rest-api request)]

          (is (= 415 status))

          (given (json-parser body)
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"invalid"
            [:issue 0 :diagnostics] := #fhir/string"Missing Content-Type header. Please use `application/x-www-form-urlencoded`."))))

    (testing "with unsupported media-type"
      (with-system [{:blaze/keys [rest-api] :blaze.test/keys [json-parser]} config]
        (let [request {:request-method :post :uri "/Patient/_search"
                       :headers {"content-type" "application/fhir+json"}
                       :body (byte-array 0)}
              {:keys [status body]} (call rest-api request)]

          (is (= 415 status))

          (given (json-parser body)
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"invalid"
            [:issue 0 :diagnostics] := #fhir/string"Unsupported Content-Type header `application/fhir+json`. Please use `application/x-www-form-urlencoded`."))))))

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
