(ns blaze.validator.extern-test
  (:require
   [blaze.fhir.parsing-context]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.fhir.writing-context]
   [blaze.http-client.spec]
   [blaze.metrics.spec]
   [blaze.module.test-util :refer [given-failed-future given-failed-system
                                   with-system]]
   [blaze.test-util :as tu]
   [blaze.validator :as validator]
   [blaze.validator-spec]
   [blaze.validator.extern :as extern]
   [blaze.validator.extern.spec]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [jsonista.core :as j]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [com.pgssoft.httpclient HttpClientMock]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(defmethod ig/init-key ::http-client [_ _]
  (HttpClientMock.))

(def ^:private config
  {:blaze.validator/extern
   {:base-uri "http://localhost:8080"
    :http-client (ig/ref ::http-client)
    :parsing-context (ig/ref :blaze.fhir/parsing-context)
    :writing-context (ig/ref :blaze.fhir/writing-context)}
   ::http-client {}
   :blaze.fhir/parsing-context
   {:structure-definition-repo structure-definition-repo}
   :blaze.fhir/writing-context
   {:structure-definition-repo structure-definition-repo}})

(def ^:private patient
  {:fhir/type :fhir/Patient :id "0"})

(defn- outcome-json [& issues]
  (j/write-value-as-string {:resourceType "OperationOutcome" :issue (vec issues)}))

(def ^:private valid-outcome
  (outcome-json {:severity "information" :code "informational"}))

(def ^:private invalid-outcome
  (outcome-json {:severity "error" :code "processing"
                 :diagnostics "Patient.gender: minimum required = 1"}))

(defn- stub-validator [^HttpClientMock http-client body]
  (-> (.onPost http-client "http://localhost:8080/validateResource")
      (.doReturn ^String body)
      (.withHeader "content-type" "application/fhir+json")))

(defn- stub-validator-status [^HttpClientMock http-client status body]
  (-> (.onPost http-client "http://localhost:8080/validateResource")
      (.doReturn (int status) ^String body)
      (.withHeader "content-type" "application/fhir+json")))

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.validator/extern nil}
      :key := :blaze.validator/extern
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.validator/extern {}}
      :key := :blaze.validator/extern
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :base-uri))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :http-client))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :parsing-context))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :writing-context))))

  (testing "invalid base-uri"
    (given-failed-system (assoc-in config [:blaze.validator/extern :base-uri] ::invalid)
      :key := :blaze.validator/extern
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::extern/base-uri]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid failure-mode"
    (given-failed-system (assoc-in config [:blaze.validator/extern :failure-mode] :invalid)
      :key := :blaze.validator/extern
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::extern/failure-mode]
      [:cause-data ::s/problems 0 :val] := :invalid)))

(deftest duration-seconds-collector-init-test
  (with-system [{collector ::extern/request-duration-seconds} {::extern/request-duration-seconds {}}]
    (is (s/valid? :blaze.metrics/collector collector))))

(deftest validate-test
  (testing "a valid resource is returned unchanged"
    (with-system [{validator :blaze.validator/extern ::keys [http-client]} config]
      (stub-validator http-client valid-outcome)

      (given @(validator/validate validator patient)
        :fhir/type := :fhir/Patient
        :id := "0"
        :meta := nil)))

  (testing "an invalid resource is tagged without contained outcome (tag-only)"
    (with-system [{validator :blaze.validator/extern ::keys [http-client]}
                  (assoc-in config [:blaze.validator/extern :failure-mode] :tag-only)]
      (stub-validator http-client invalid-outcome)

      (given @(validator/validate validator patient)
        :fhir/type := :fhir/Patient
        [:meta :tag 0 :code] := #fhir/code "invalid"
        :contained := nil)))

  (testing "an invalid resource is tagged with contained outcome (tag-outcome)"
    (with-system [{validator :blaze.validator/extern ::keys [http-client]} config]
      (stub-validator http-client invalid-outcome)

      (given @(validator/validate validator patient)
        :fhir/type := :fhir/Patient
        [:meta :tag 0 :code] := #fhir/code "invalid"
        [:contained 0 :fhir/type] := :fhir/OperationOutcome
        [:contained 0 :id] := "validation-outcome")))

  (testing "an invalid resource is rejected (reject)"
    (with-system [{validator :blaze.validator/extern ::keys [http-client]}
                  (assoc-in config [:blaze.validator/extern :failure-mode] :reject)]
      (stub-validator http-client invalid-outcome)

      (given-failed-future (validator/validate validator patient)
        ::anom/category := ::anom/incorrect
        [:fhir/issues count] := 1
        [:fhir/issues 0 :fhir.issues/severity] := "error")))

  (testing "an unavailable validator fails closed with busy"
    (with-system [{validator :blaze.validator/extern ::keys [http-client]} config]
      (stub-validator-status http-client 500 valid-outcome)

      (given-failed-future (validator/validate validator patient)
        ::anom/category := ::anom/busy))))
