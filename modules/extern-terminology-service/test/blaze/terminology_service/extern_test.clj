(ns blaze.terminology-service.extern-test
  (:require
   [blaze.fhir-client-spec]
   [blaze.fhir.parsing-context]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.fhir.util :as fu]
   [blaze.fhir.writing-context]
   [blaze.http-client.spec]
   [blaze.metrics.spec]
   [blaze.module.test-util :refer [given-failed-future given-failed-system with-system]]
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

(def config
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
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(deftest duration-seconds-collector-init-test
  (with-system [{collector ::extern/request-duration-seconds} {::extern/request-duration-seconds {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(defn- params-matcher [{:blaze.fhir/keys [writing-context]} params]
  (Matchers/is (fhir-spec/write-json-as-string writing-context params)))

(deftest code-system-validate-code-test
  (testing "success"
    (with-system [{ts ::ts/extern ::keys [^HttpClientMock http-client] :as system} config]

      (let [params (fu/parameters "url" #fhir/uri "http://hl7.org/fhir/CodeSystem/administrative-gender")]
        (-> (.onPost http-client "http://localhost:8080/fhir/CodeSystem/$validate-code")
            (.withBody (params-matcher system params))
            (.doReturn (j/write-value-as-string {:resourceType "Parameters"}))
            (.withHeader "content-type" "application/fhir+json"))

        (given @(ts/code-system-validate-code ts params)
          :fhir/type := :fhir/Parameters))))

  (testing "code-system not-found"
    (with-system [{ts ::ts/extern ::keys [^HttpClientMock http-client] :as system} config]

      (let [params (fu/parameters "url" #fhir/uri "http://hl7.org/fhir/CodeSystem/administrative-gender")]
        (-> (.onPost http-client "http://localhost:8080/fhir/CodeSystem/$validate-code")
            (.withBody (params-matcher system params))
            (.doReturn 400 (j/write-value-as-string
                            {:resourceType "OperationOutcome"
                             :issue
                             [{:severity "error"
                               :code "not-found"
                               :diagnostics "The value set `http://hl7.org/fhir/CodeSystem/administrative-gender` was not found."}]}))
            (.withHeader "content-type" "application/fhir+json"))

        (given-failed-future (ts/code-system-validate-code ts params)
          ::anom/category := ::anom/incorrect
          ::anom/message := "Unexpected response status 400."
          [:fhir/issues count] := 1
          [:fhir/issues 0 :severity] := #fhir/code "error"
          [:fhir/issues 0 :code] := #fhir/code "not-found"
          [:fhir/issues 0 :diagnostics] := #fhir/string "The value set `http://hl7.org/fhir/CodeSystem/administrative-gender` was not found.")))))

(deftest expand-value-set-test
  (with-system [{ts ::ts/extern ::keys [^HttpClientMock http-client] :as system} config]

    (let [params (fu/parameters "url" #fhir/uri "http://hl7.org/fhir/ValueSet/administrative-gender")]
      (-> (.onPost http-client "http://localhost:8080/fhir/ValueSet/$expand")
          (.withBody (params-matcher system params))
          (.doReturn (j/write-value-as-string {:resourceType "ValueSet" :id "0"}))
          (.withHeader "content-type" "application/fhir+json"))

      (given @(ts/expand-value-set ts params)
        :fhir/type := :fhir/ValueSet
        :id := "0"))))

(deftest value-set-validate-code-test
  (testing "success"
    (with-system [{ts ::ts/extern ::keys [^HttpClientMock http-client] :as system} config]

      (let [params (fu/parameters "url" #fhir/uri "http://hl7.org/fhir/ValueSet/administrative-gender")]
        (-> (.onPost http-client "http://localhost:8080/fhir/ValueSet/$validate-code")
            (.withBody (params-matcher system params))
            (.doReturn (j/write-value-as-string {:resourceType "Parameters"}))
            (.withHeader "content-type" "application/fhir+json"))

        (given @(ts/value-set-validate-code ts params)
          :fhir/type := :fhir/Parameters))))

  (testing "value-set not-found"
    (with-system [{ts ::ts/extern ::keys [^HttpClientMock http-client] :as system} config]

      (let [params (fu/parameters "url" #fhir/uri "http://hl7.org/fhir/ValueSet/administrative-gender")]
        (-> (.onPost http-client "http://localhost:8080/fhir/ValueSet/$validate-code")
            (.withBody (params-matcher system params))
            (.doReturn 400 (j/write-value-as-string
                            {:resourceType "OperationOutcome"
                             :issue
                             [{:severity "error"
                               :code "not-found"
                               :diagnostics "The value set `http://hl7.org/fhir/ValueSet/administrative-gender` was not found."}]}))
            (.withHeader "content-type" "application/fhir+json"))

        (given-failed-future (ts/value-set-validate-code ts params)
          ::anom/category := ::anom/incorrect
          ::anom/message := "Unexpected response status 400."
          [:fhir/issues count] := 1
          [:fhir/issues 0 :severity] := #fhir/code "error"
          [:fhir/issues 0 :code] := #fhir/code "not-found"
          [:fhir/issues 0 :diagnostics] := #fhir/string "The value set `http://hl7.org/fhir/ValueSet/administrative-gender` was not found.")))))
