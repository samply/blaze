(ns blaze.terminology-service.extern-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir-client-spec]
   [blaze.fhir.parsing-context]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.fhir.util :as fu]
   [blaze.fhir.writing-context]
   [blaze.http-client.spec]
   [blaze.metrics.spec]
   [blaze.module.test-util :refer [given-failed-future given-failed-system with-system]]
   [blaze.openid-client.token-provider.protocol :as tp-protocol]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service-spec]
   [blaze.terminology-service.extern :as extern]
   [blaze.terminology-service.extern.spec]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [jsonista.core :as j]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [com.pgssoft.httpclient HttpClientMock]
   [org.hamcrest Matchers]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(defmethod ig/init-key ::http-client [_ _]
  (HttpClientMock.))

(defmethod ig/init-key ::token-provider [_ _]
  (reify tp-protocol/TokenProvider
    (-current-token [_] "my-token")))

(defmethod ig/init-key ::token-provider-unavailable [_ _]
  (reify tp-protocol/TokenProvider
    (-current-token [_] (ba/unavailable "Token not available."))))

(def ^:private config
  {::ts/extern
   {:base-uri "http://localhost:8080/fhir"
    :http-client (ig/ref ::http-client)
    :parsing-context (ig/ref :blaze.fhir/parsing-context)
    :writing-context (ig/ref :blaze.fhir/writing-context)}
   ::http-client {}
   :blaze.fhir/parsing-context
   {:structure-definition-repo structure-definition-repo}
   :blaze.fhir/writing-context
   {:structure-definition-repo structure-definition-repo}})

(def ^:private config-with-token-provider
  {::ts/extern
   {:base-uri "http://localhost:8080/fhir"
    :http-client (ig/ref ::http-client)
    :token-provider (ig/ref ::token-provider)
    :parsing-context (ig/ref :blaze.fhir/parsing-context)
    :writing-context (ig/ref :blaze.fhir/writing-context)}
   ::http-client {}
   ::token-provider {}
   :blaze.fhir/parsing-context
   {:structure-definition-repo structure-definition-repo}
   :blaze.fhir/writing-context
   {:structure-definition-repo structure-definition-repo}})

(def ^:private config-with-token-provider-unavailable
  {::ts/extern
   {:base-uri "http://localhost:8080/fhir"
    :http-client (ig/ref ::http-client)
    :token-provider (ig/ref ::token-provider-unavailable)
    :parsing-context (ig/ref :blaze.fhir/parsing-context)
    :writing-context (ig/ref :blaze.fhir/writing-context)}
   ::http-client {}
   ::token-provider-unavailable {}
   :blaze.fhir/parsing-context
   {:structure-definition-repo structure-definition-repo}
   :blaze.fhir/writing-context
   {:structure-definition-repo structure-definition-repo}})

(deftest init-test
  (testing "nil config"
    (given-failed-system {::ts/extern nil}
      :key := ::ts/extern
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {::ts/extern {}}
      :key := ::ts/extern
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :base-uri))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :http-client))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :parsing-context))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :writing-context))))

  (testing "invalid base-uri"
    (given-failed-system (assoc-in config [::ts/extern :base-uri] ::invalid)
      :key := ::ts/extern
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::extern/base-uri]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid http-client"
    (given-failed-system (assoc-in config [::ts/extern :http-client] ::invalid)
      :key := ::ts/extern
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/http-client]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid parsing-context"
    (given-failed-system (assoc-in config [::ts/extern :parsing-context] ::invalid)
      :key := ::ts/extern
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.fhir/parsing-context]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid writing-context"
    (given-failed-system (assoc-in config [::ts/extern :writing-context] ::invalid)
      :key := ::ts/extern
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.fhir/writing-context]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid token-provider"
    (given-failed-system (assoc-in config [::ts/extern :token-provider] ::invalid)
      :key := ::ts/extern
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.openid-client/token-provider]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(deftest duration-seconds-collector-init-test
  (with-system [{collector ::extern/request-duration-seconds} {::extern/request-duration-seconds {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(defn- params-matcher [{:blaze.fhir/keys [writing-context]} params]
  (Matchers/is (fhir-spec/write-json-as-string writing-context params)))

(defn- stub-post
  "Stubs `http-client` to expect a POST to `url` with `params` as request body
  and to respond with `status` (default 200) and `body` written as FHIR JSON.
  With a non-nil `:token` it also expects an `Authorization` bearer header."
  [^HttpClientMock http-client system url params
   {:keys [status token body] :or {status 200}}]
  (-> (cond-> (.onPost http-client ^String url)
        token (.withHeader "Authorization" (str "Bearer " token)))
      (.withBody (params-matcher system params))
      (.doReturn (int status) (j/write-value-as-string body))
      (.withHeader "content-type" "application/fhir+json")))

(defn- stub-code-system-validate-code [http-client system params opts]
  (stub-post http-client system "http://localhost:8080/fhir/CodeSystem/$validate-code" params opts))

(defn- stub-expand-value-set [http-client system params opts]
  (stub-post http-client system "http://localhost:8080/fhir/ValueSet/$expand" params opts))

(defn- stub-value-set-validate-code [http-client system params opts]
  (stub-post http-client system "http://localhost:8080/fhir/ValueSet/$validate-code" params opts))

(deftest code-system-validate-code-test
  (testing "success"
    (with-system [{ts ::ts/extern ::keys [^HttpClientMock http-client] :as system} config]

      (let [params (fu/parameters "url" #fhir/uri "http://hl7.org/fhir/CodeSystem/administrative-gender")]
        (stub-code-system-validate-code http-client system params
                                        {:body {:resourceType "Parameters"}})

        (given @(ts/code-system-validate-code ts params)
          :fhir/type := :fhir/Parameters))))

  (testing "code-system not-found"
    (with-system [{ts ::ts/extern ::keys [^HttpClientMock http-client] :as system} config]

      (let [params (fu/parameters "url" #fhir/uri "http://hl7.org/fhir/CodeSystem/administrative-gender")]
        (stub-code-system-validate-code
         http-client system params
         {:status 400
          :body {:resourceType "OperationOutcome"
                 :issue
                 [{:severity "error"
                   :code "not-found"
                   :diagnostics "The value set `http://hl7.org/fhir/CodeSystem/administrative-gender` was not found."}]}})

        (given-failed-future (ts/code-system-validate-code ts params)
          ::anom/category := ::anom/incorrect
          ::anom/message := "Unexpected response status 400."
          [:fhir/issues count] := 1
          [:fhir/issues 0 :severity] := #fhir/code "error"
          [:fhir/issues 0 :code] := #fhir/code "not-found"
          [:fhir/issues 0 :diagnostics] := #fhir/string "The value set `http://hl7.org/fhir/CodeSystem/administrative-gender` was not found."))))

  (testing "success with token"
    (with-system [{ts ::ts/extern ::keys [^HttpClientMock http-client] :as system} config-with-token-provider]

      (let [params (fu/parameters "url" #fhir/uri "http://hl7.org/fhir/CodeSystem/administrative-gender")]
        (stub-code-system-validate-code http-client system params
                                        {:token "my-token"
                                         :body {:resourceType "Parameters"}})

        (given @(ts/code-system-validate-code ts params)
          :fhir/type := :fhir/Parameters))))

  (testing "token unavailable"
    (with-system [{ts ::ts/extern} config-with-token-provider-unavailable]

      (let [params (fu/parameters "url" #fhir/uri "http://hl7.org/fhir/CodeSystem/administrative-gender")]

        (given-failed-future (ts/code-system-validate-code ts params)
          ::anom/category := ::anom/unavailable
          ::anom/message := "Token not available.")))))

(deftest expand-value-set-test
  (testing "success"
    (with-system [{ts ::ts/extern ::keys [^HttpClientMock http-client] :as system} config]

      (let [params (fu/parameters "url" #fhir/uri "http://hl7.org/fhir/ValueSet/administrative-gender")]
        (stub-expand-value-set http-client system params
                               {:body {:resourceType "ValueSet" :id "0"}})

        (given @(ts/expand-value-set ts params)
          :fhir/type := :fhir/ValueSet
          :id := "0"))))

  (testing "success with token"
    (with-system [{ts ::ts/extern ::keys [^HttpClientMock http-client] :as system} config-with-token-provider]

      (let [params (fu/parameters "url" #fhir/uri "http://hl7.org/fhir/ValueSet/administrative-gender")]
        (stub-expand-value-set http-client system params
                               {:token "my-token"
                                :body {:resourceType "ValueSet" :id "0"}})

        (given @(ts/expand-value-set ts params)
          :fhir/type := :fhir/ValueSet
          :id := "0"))))

  (testing "token unavailable"
    (with-system [{ts ::ts/extern} config-with-token-provider-unavailable]

      (let [params (fu/parameters "url" #fhir/uri "http://hl7.org/fhir/ValueSet/administrative-gender")]

        (given-failed-future (ts/expand-value-set ts params)
          ::anom/category := ::anom/unavailable
          ::anom/message := "Token not available.")))))

(deftest value-set-validate-code-test
  (testing "success"
    (with-system [{ts ::ts/extern ::keys [^HttpClientMock http-client] :as system} config]

      (let [params (fu/parameters "url" #fhir/uri "http://hl7.org/fhir/ValueSet/administrative-gender")]
        (stub-value-set-validate-code http-client system params
                                      {:body {:resourceType "Parameters"}})

        (given @(ts/value-set-validate-code ts params)
          :fhir/type := :fhir/Parameters))))

  (testing "value-set not-found"
    (with-system [{ts ::ts/extern ::keys [^HttpClientMock http-client] :as system} config]

      (let [params (fu/parameters "url" #fhir/uri "http://hl7.org/fhir/ValueSet/administrative-gender")]
        (stub-value-set-validate-code
         http-client system params
         {:status 400
          :body {:resourceType "OperationOutcome"
                 :issue
                 [{:severity "error"
                   :code "not-found"
                   :diagnostics "The value set `http://hl7.org/fhir/ValueSet/administrative-gender` was not found."}]}})

        (given-failed-future (ts/value-set-validate-code ts params)
          ::anom/category := ::anom/incorrect
          ::anom/message := "Unexpected response status 400."
          [:fhir/issues count] := 1
          [:fhir/issues 0 :severity] := #fhir/code "error"
          [:fhir/issues 0 :code] := #fhir/code "not-found"
          [:fhir/issues 0 :diagnostics] := #fhir/string "The value set `http://hl7.org/fhir/ValueSet/administrative-gender` was not found."))))

  (testing "success with token"
    (with-system [{ts ::ts/extern ::keys [^HttpClientMock http-client] :as system} config-with-token-provider]

      (let [params (fu/parameters "url" #fhir/uri "http://hl7.org/fhir/ValueSet/administrative-gender")]
        (stub-value-set-validate-code http-client system params
                                      {:token "my-token"
                                       :body {:resourceType "Parameters"}})

        (given @(ts/value-set-validate-code ts params)
          :fhir/type := :fhir/Parameters))))

  (testing "token unavailable"
    (with-system [{ts ::ts/extern} config-with-token-provider-unavailable]

      (let [params (fu/parameters "url" #fhir/uri "http://hl7.org/fhir/ValueSet/administrative-gender")]

        (given-failed-future (ts/value-set-validate-code ts params)
          ::anom/category := ::anom/unavailable
          ::anom/message := "Token not available.")))))
