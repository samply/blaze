(ns blaze.terminology-service.extern-test
  (:require
   [blaze.fhir-client-spec]
   [blaze.fhir.parsing-context]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.fhir.util :as fu]
   [blaze.fhir.writing-context]
   [blaze.http-client.spec]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service-spec]
   [blaze.terminology-service.extern]
   [blaze.terminology-service.extern.spec]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
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
      [:cause-data ::s/problems 0 :via] := [:blaze.terminology-service.extern/base-uri]
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

(defn- expand-value-set [ts & nvs]
  (ts/expand-value-set ts (apply fu/parameters nvs)))

(deftest terminology-service-test
  (with-system [{ts ::ts/extern ::keys [http-client]} config]

    (-> (.onGet ^HttpClientMock http-client "http://localhost:8080/fhir/ValueSet/$expand?url=http://hl7.org/fhir/ValueSet/administrative-gender")
        (.doReturn (j/write-value-as-string {:resourceType "ValueSet" :id "0"}))
        (.withHeader "content-type" "application/fhir+json"))

    (given @(expand-value-set ts
              "url" #fhir/uri "http://hl7.org/fhir/ValueSet/administrative-gender")
      :fhir/type := :fhir/ValueSet
      :id := "0")))
