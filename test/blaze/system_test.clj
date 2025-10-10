(ns blaze.system-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api-stub :refer [mem-node-config with-system-data]]
   [blaze.fhir.parsing-context]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.fhir.writing-context]
   [blaze.interaction.conditional-delete-type]
   [blaze.interaction.delete]
   [blaze.interaction.delete-history]
   [blaze.interaction.history.type]
   [blaze.interaction.read]
   [blaze.interaction.search-compartment]
   [blaze.interaction.search-system]
   [blaze.interaction.search-type]
   [blaze.interaction.search.util :as search-util]
   [blaze.interaction.transaction]
   [blaze.interaction.vread]
   [blaze.middleware.fhir.decrypt-page-id :as decrypt-page-id]
   [blaze.middleware.fhir.decrypt-page-id-spec]
   [blaze.module.test-util :refer [with-system]]
   [blaze.module.test-util.ring :as tu-r]
   [blaze.page-store.protocols :as pp]
   [blaze.rest-api :as rest-api]
   [blaze.rest-api.async-status-cancel-handler]
   [blaze.rest-api.async-status-handler]
   [blaze.rest-api.batch-handler]
   [blaze.rest-api.capabilities-handler]
   [blaze.system :as system]
   [blaze.system-spec]
   [blaze.terminology-service :as-alias ts]
   [blaze.terminology-service.local :as ts-local]
   [blaze.test-util :as tu]
   [buddy.auth.protocols :as ap]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [are deftest testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [ring.core.protocols :as rp]
   [taoensso.timbre :as log])
  (:import
   [java.io ByteArrayInputStream ByteArrayOutputStream]))

(st/instrument)
(log/set-min-level! :trace)

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
          :default-value "default"}]}}))

  (testing "Passwords are masked"
    (are [name]
         (let [config {:a (system/->Cfg name (s/spec string?) nil)}]
           (= {:a "secret" :blaze/admin-api {:settings [{:name name :masked true}]}}
              (system/resolve-config config {name "secret"})))
      "PASSWORD"
      "FOO_PASSWORD"
      "PASSWORD_FOO"
      "FOO_PASSWORD_BAR"
      "PASS"
      "FOO_PASS"
      "PASS_FOO"
      "FOO_PASS_BAR")))

(deftest merge-features-test
  (testing "vector"
    (given (system/merge-features
            {:base-config {:foo [:a]}
             :features [{:key :bar :name "bar" :toggle "T" :config {:foo [:b]}}]}
            {"T" "true"})
      :foo := [:a :b]))

  (testing "nested map"
    (given (system/merge-features
            {:base-config {:foo {:default {:read {}}}}
             :features [{:key :bar :name "bar" :toggle "T" :config {:foo {:default {:delete-history {}}}}}]}
            {"T" "true"})
      :foo := {:default {:read {}
                         :delete-history {}}})))

(def config
  (assoc
   mem-node-config
   :blaze/rest-api
   {:base-url "http://localhost:8080"
    :parsing-context (ig/ref :blaze.fhir.parsing-context/default)
    :writing-context (ig/ref :blaze.fhir/writing-context)
    :structure-definition-repo structure-definition-repo
    :node (ig/ref :blaze.db/node)
    :admin-node (ig/ref :blaze.db/node)
    :db-sync-timeout 10000
    :page-id-cipher (ig/ref :blaze.test/page-id-cipher)
    :auth-backends [(ig/ref ::auth-backend)]
    :search-system-handler (ig/ref :blaze.interaction/search-system)
    :transaction-handler (ig/ref :blaze.interaction/transaction)
    :async-status-handler (ig/ref ::rest-api/async-status-handler)
    :async-status-cancel-handler (ig/ref ::rest-api/async-status-cancel-handler)
    :capabilities-handler (ig/ref ::rest-api/capabilities-handler)
    :resource-patterns (ig/ref ::rest-api/resource-patterns)
    :compartments
    [#:blaze.rest-api.compartment
      {:code "Patient"
       :search-handler (ig/ref :blaze.interaction/search-compartment)}]
    :job-scheduler (ig/ref :blaze/job-scheduler)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}
   :blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo}
   ::auth-backend {}
   :blaze.interaction/transaction
   {:node (ig/ref :blaze.db/node)
    :batch-handler (ig/ref ::rest-api/batch-handler)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :db-sync-timeout 10000}
   :blaze.interaction/read {}
   :blaze.interaction/vread {}
   :blaze.interaction/delete
   {:node (ig/ref :blaze.db/node)}
   :blaze.interaction/delete-history
   {:node (ig/ref :blaze.db/node)}
   :blaze.interaction/conditional-delete-type
   {:node (ig/ref :blaze.db/node)}
   :blaze.interaction/search-system
   {::search-util/link (ig/ref ::search-util/link)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :page-store (ig/ref ::page-store)
    :page-id-cipher (ig/ref :blaze.test/page-id-cipher)}
   :blaze.interaction/search-type
   {::search-util/link (ig/ref ::search-util/link)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :page-store (ig/ref ::page-store)
    :page-id-cipher (ig/ref :blaze.test/page-id-cipher)}
   :blaze.interaction/search-compartment
   {::search-util/link (ig/ref ::search-util/link)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :page-store (ig/ref ::page-store)
    :page-id-cipher (ig/ref :blaze.test/page-id-cipher)}
   :blaze.interaction.history/type
   {::search-util/link (ig/ref ::search-util/link)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :page-id-cipher (ig/ref :blaze.test/page-id-cipher)}
   ::rest-api/async-status-handler
   {}
   ::rest-api/async-status-cancel-handler
   {:job-scheduler (ig/ref :blaze/job-scheduler)}
   ::rest-api/capabilities-handler
   {:version "0.1.0"
    :release-date "2024-01-07"
    :structure-definition-repo structure-definition-repo
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
    :terminology-service (ig/ref ::ts/local)
    :search-system-handler (ig/ref :blaze.interaction/search-system)
    :transaction-handler-active? true
    :resource-patterns (ig/ref ::rest-api/resource-patterns)}
   ::rest-api/batch-handler
   {:structure-definition-repo structure-definition-repo
    :capabilities-handler (ig/ref ::rest-api/capabilities-handler)
    :resource-patterns (ig/ref ::rest-api/resource-patterns)}
   :blaze/job-scheduler
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}
   ::rest-api/resource-patterns
   {:default
    {:read
     #:blaze.rest-api.interaction
      {:handler (ig/ref :blaze.interaction/read)}
     :vread
     #:blaze.rest-api.interaction
      {:handler (ig/ref :blaze.interaction/vread)}
     :delete
     #:blaze.rest-api.interaction
      {:handler (ig/ref :blaze.interaction/delete)}
     :delete-history
     #:blaze.rest-api.interaction
      {:handler (ig/ref :blaze.interaction/delete-history)}
     :conditional-delete-type
     #:blaze.rest-api.interaction
      {:handler (ig/ref :blaze.interaction/conditional-delete-type)}
     :search-type
     #:blaze.rest-api.interaction
      {:handler (ig/ref :blaze.interaction/search-type)}
     :history-type
     #:blaze.rest-api.interaction
      {:handler (ig/ref :blaze.interaction.history/type)}}}
   ::ts/local
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :graph-cache (ig/ref ::ts-local/graph-cache)}
   ::search-util/link {:fhir/version "4.0.1"}
   :blaze.test/executor {}
   :blaze.test/fixed-clock {}
   :blaze.test/fixed-rng-fn {}
   ::page-store {}
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

(defn input-stream [bs]
  (ByteArrayInputStream. bs))

(def search-bundle
  {:fhir/type :fhir/Bundle
   :type #fhir/code "batch"
   :entry
   [{:fhir/type :fhir.Bundle/entry
     :request
     {:fhir/type :fhir.Bundle.entry/request
      :method #fhir/code "GET"
      :url #fhir/uri "/Patient"}}]})

(deftest auth-test
  (with-system [{:blaze/keys [rest-api] :blaze.test/keys [json-parser json-writer]} config]
    (testing "Patient search"
      (given (call rest-api {:request-method :get :uri "/Patient"})
        :status := 200))

    (testing "Patient search as batch request"
      (given (call rest-api {:request-method :post :uri ""
                             :headers {"content-type" "application/fhir+json"}
                             :body (input-stream (json-writer search-bundle))})
        :status := 200
        [:body json-parser :entry 0 :response :status] := #fhir/string "200"))))

(deftest not-found-test
  (with-system [{:blaze/keys [rest-api] :blaze.test/keys [json-parser]} config]
    (given (call rest-api {:request-method :get :uri "/"})
      :status := 404
      [:body json-parser :fhir/type] := :fhir/OperationOutcome)))

(deftest not-acceptable-test
  (with-system [{:blaze/keys [rest-api]} config]
    (given (call rest-api {:request-method :get :uri "/Patient"
                           :headers {"accept" "text/plain"}})
      :status := 406
      :body := nil)))

(deftest read-test
  (with-system-data [{:blaze/keys [rest-api] :blaze.test/keys [json-parser]} config]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

    (testing "success"
      (given (call rest-api {:request-method :get :uri "/Patient/0"})
        :status := 200
        [:body json-parser :fhir/type] := :fhir/Patient))

    (testing "not found"
      (given (call rest-api {:request-method :get :uri "/Patient/1"})
        :status := 404
        [:body json-parser :fhir/type] := :fhir/OperationOutcome))))

(deftest vread-test
  (with-system-data [{:blaze/keys [rest-api] :blaze.test/keys [json-parser]} config]
    [[[:put {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean false}]]
     [[:put {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean true}]]]

    (testing "current version"
      (given (call rest-api {:request-method :get :uri "/Patient/0/_history/3"})
        :status := 200
        [:body json-parser :active] := #fhir/boolean true))

    (testing "older version"
      (given (call rest-api {:request-method :get :uri "/Patient/0/_history/2"})
        :status := 200
        [:body json-parser :active] := #fhir/boolean false))

    (doseq [t [0 4]]
      (testing (format "version %d doesn't exist" t)
        (given (call rest-api {:request-method :get :uri (format "/Patient/0/_history/%d" t)})
          :status := 404
          [:body json-parser :fhir/type] := :fhir/OperationOutcome))))

  (testing "with deleted history"
    (with-system-data [{:blaze/keys [rest-api] :blaze.test/keys [json-parser]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean false}]]
       [[:put {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean true}]]
       [[:delete-history "Patient" "0"]]]

      (testing "current version"
        (given (call rest-api {:request-method :get :uri "/Patient/0/_history/3"})
          :status := 200
          [:body json-parser :active] := #fhir/boolean true))

      (doseq [t [0 2 4]]
        (testing (format "version %d doesn't exist" t)
          (given (call rest-api {:request-method :get :uri (format "/Patient/0/_history/%d" t)})
            :status := 404
            [:body json-parser :fhir/type] := :fhir/OperationOutcome))))))

(def read-bundle
  {:fhir/type :fhir/Bundle
   :type #fhir/code "batch"
   :entry
   [{:fhir/type :fhir.Bundle/entry
     :request
     {:fhir/type :fhir.Bundle.entry/request
      :method #fhir/code "GET"
      :url #fhir/uri "/Patient/0"}}]})

(deftest batch-read-test
  (with-system [{:blaze/keys [rest-api] :blaze.test/keys [json-parser json-writer]} config]
    (given (call rest-api {:request-method :post :uri ""
                           :headers {"content-type" "application/fhir+json"}
                           :body (input-stream (json-writer read-bundle))})
      :status := 200
      [:body json-parser :entry 0 :response :status] := #fhir/string "404"
      [:body json-parser :entry 0 :response :outcome :fhir/type] := :fhir/OperationOutcome)))

(deftest batch-unsupported-media-type-test
  (with-system [{:blaze/keys [rest-api] :blaze.test/keys [json-parser]} config]
    (given (call rest-api {:request-method :post :uri ""
                           :headers {"content-type" "text/plain"}})
      :status := 415
      [:body json-parser :fhir/type] := :fhir/OperationOutcome)))

(def metadata-bundle
  {:fhir/type :fhir/Bundle
   :type #fhir/code "batch"
   :entry
   [{:fhir/type :fhir.Bundle/entry
     :request
     {:fhir/type :fhir.Bundle.entry/request
      :method #fhir/code "GET"
      :url #fhir/uri "metadata"}}]})

(deftest batch-metadata-test
  (with-system [{:blaze/keys [rest-api] :blaze.test/keys [json-parser json-writer]} config]
    (given (call rest-api {:request-method :post :uri ""
                           :headers {"content-type" "application/fhir+json"}
                           :body (input-stream (json-writer metadata-bundle))})
      :status := 200
      [:body json-parser :entry 0 :resource :fhir/type] := :fhir/CapabilityStatement
      [:body json-parser :entry 0 :response :status] := #fhir/string "200")))

(deftest delete-test
  (with-system [{:blaze/keys [rest-api] :blaze.test/keys [json-parser]} config]
    (given (call rest-api {:request-method :delete :uri "/Patient/0"})
      :status := 204
      :body := nil)

    (given (call rest-api {:request-method :get :uri "/Patient/0"})
      :status := 410
      [:body json-parser :fhir/type] := :fhir/OperationOutcome)))

(deftest delete-history-test
  (with-system-data [{:blaze/keys [rest-api] :blaze.test/keys [json-parser]} config]
    [[[:put {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean false}]]
     [[:put {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean true}]]]

    (given (call rest-api {:request-method :delete :uri "/Patient/0/_history"})
      :status := 204
      :body := nil)

    (given (call rest-api {:request-method :get :uri "/Patient/0/_history/1"})
      :status := 404
      [:body json-parser :fhir/type] := :fhir/OperationOutcome)))

(deftest conditional-delete-type-test
  (with-system [{:blaze/keys [rest-api]} config]
    (given (call rest-api {:request-method :delete :uri "/Patient"})
      :status := 204)))

(deftest search-system-test
  (with-system [{:blaze/keys [rest-api] :blaze.test/keys [json-parser]} config]
    (given (call rest-api {:request-method :get :uri ""})
      :status := 200
      [:headers "Link" #(str/split % #",") 0] := "<http://localhost:8080?_count=50>;rel=\"self\""
      [:body json-parser :fhir/type] := :fhir/Bundle)))

(deftest search-type-test
  (testing "using GET"
    (with-system [{:blaze/keys [rest-api] :blaze.test/keys [json-parser]} config]
      (given (call rest-api {:request-method :get :uri "/Patient"})
        :status := 200
        [:headers "Link"] := "<http://localhost:8080/Patient?_count=50>;rel=\"self\""
        [:body json-parser :fhir/type] := :fhir/Bundle))

    (testing "with two patients"
      (with-system-data [{:blaze/keys [rest-api] :blaze.test/keys [page-id-cipher json-parser]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Patient :id "1"}]]]

        (given (call rest-api {:request-method :get :uri "/Patient" :query-string "_count=1"})
          :status := 200
          [:headers "Link" #(str/split % #",") 0] := (format "<http://localhost:8080/Patient/__page/%s>;rel=\"first\"" (decrypt-page-id/encrypt page-id-cipher {"_count" "1" "__t" "2"}))
          [:headers "Link" #(str/split % #",") 1] := "<http://localhost:8080/Patient?_count=1>;rel=\"self\""
          [:headers "Link" #(str/split % #",") 2] := (format "<http://localhost:8080/Patient/__page/%s>;rel=\"next\"" (decrypt-page-id/encrypt page-id-cipher {"_count" "1" "__t" "2" "__page-id" "1"}))
          [:body json-parser :fhir/type] := :fhir/Bundle)

        (testing "fetch the second page"
          (given (call rest-api {:request-method :get :uri (str "/Patient/__page/" (decrypt-page-id/encrypt page-id-cipher {"__page-id" "1" "__t" "2" "_count" "1"}))})
            :status := 200

            [:headers "Link" #(str/split % #",") 0] := (format "<http://localhost:8080/Patient/__page/%s>;rel=\"first\"" (decrypt-page-id/encrypt page-id-cipher {"_count" "1" "__t" "2"}))
            [:body json-parser :fhir/type] := :fhir/Bundle))

        (testing "fetch an unknown page"
          (given (call rest-api {:request-method :get :uri "/Patient/__page/unknown"})
            :status := 404

            [:body json-parser :fhir/type] := :fhir/OperationOutcome)))))

  (testing "using POST"
    (testing "with unsupported media-type"
      (with-system [{:blaze/keys [rest-api] :blaze.test/keys [json-parser]} config]
        (given (call rest-api {:request-method :post :uri "/Patient/_search"
                               :headers {"content-type" "application/fhir+json"}
                               :body (input-stream (byte-array 0))})
          :status := 415
          [:body json-parser :fhir/type] := :fhir/OperationOutcome)))

    (with-system [{:blaze/keys [rest-api] :blaze.test/keys [json-parser]} config]
      (given (call rest-api {:request-method :post :uri "/Patient/_search"
                             :headers {"content-type" "application/x-www-form-urlencoded"}
                             :body (input-stream (byte-array 0))})
        :status := 200
        [:headers "Link"] := "<http://localhost:8080/Patient?_count=50>;rel=\"self\""
        [:body json-parser :fhir/type] := :fhir/Bundle))

    (testing "with two patients"
      (with-system-data [{:blaze/keys [rest-api] :blaze.test/keys [page-id-cipher json-parser]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Patient :id "1"}]]]

        (given (call rest-api {:request-method :post :uri "/Patient/_search"
                               :query-string "_count=1"
                               :headers {"content-type" "application/x-www-form-urlencoded"}
                               :body (input-stream (byte-array 0))})
          :status := 200
          [:headers "Link" #(str/split % #",") 0] := (format "<http://localhost:8080/Patient/__page/%s>;rel=\"first\"" (decrypt-page-id/encrypt page-id-cipher {"_count" "1" "__t" "2"}))
          [:headers "Link" #(str/split % #",") 1] := "<http://localhost:8080/Patient?_count=1>;rel=\"self\""
          [:headers "Link" #(str/split % #",") 2] := (format "<http://localhost:8080/Patient/__page/%s>;rel=\"next\"" (decrypt-page-id/encrypt page-id-cipher {"_count" "1" "__t" "2" "__page-id" "1"}))
          [:body json-parser :fhir/type] := :fhir/Bundle)

        (testing "fetch the second page"
          (given (call rest-api {:request-method :post :uri (str "/Patient/__page/" (decrypt-page-id/encrypt page-id-cipher {"__page-id" "1" "__t" "2" "_count" "1"}))})
            :status := 200

            [:headers "Link" #(str/split % #",") 0] := (format "<http://localhost:8080/Patient/__page/%s>;rel=\"first\"" (decrypt-page-id/encrypt page-id-cipher {"_count" "1" "__t" "2"}))
            [:body json-parser :fhir/type] := :fhir/Bundle))

        (testing "fetch an unknown page"
          (given (call rest-api {:request-method :post :uri "/Patient/__page/unknown"})
            :status := 404

            [:body json-parser :fhir/type] := :fhir/OperationOutcome))))))

(deftest search-compartment-test
  (with-system-data [{:blaze/keys [rest-api] :blaze.test/keys [json-parser]} config]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

    (given (call rest-api {:request-method :get :uri "/Patient/0/Observation"})
      :status := 200
      [:body json-parser :fhir/type] := :fhir/Bundle)))

(deftest history-type-test
  (with-system-data [{:blaze/keys [rest-api] :blaze.test/keys [json-parser]} config]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

    (given (call rest-api {:request-method :get :uri "/Patient/_history"})
      :status := 200
      [:headers "Link"] := "<http://localhost:8080/Patient/_history>;rel=\"self\""
      [:body json-parser :fhir/type] := :fhir/Bundle)))

(deftest redirect-slash-test
  (with-system [{:blaze/keys [rest-api]} config]
    (given (call rest-api {:request-method :get :uri "/Patient/"})
      :status := 301
      [:headers "Location"] := "/Patient")))
