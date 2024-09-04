(ns blaze.terminology-service.extern-test
  (:require
   [blaze.fhir.test-util]
   [blaze.http-client.spec :refer [http-client?]]
   [blaze.module.test-util :refer [with-system]]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service.extern]
   [blaze.test-util :as tu :refer [given-thrown]]
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
    :http-client (ig/ref ::http-client)}
   ::http-client {}})

(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {::ts/extern nil})
      :key := ::ts/extern
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {::ts/extern {}})
      :key := ::ts/extern
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :base-uri))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :http-client))))

  (testing "invalid base-uri"
    (given-thrown (ig/init {::ts/extern {:base-uri ::invalid}})
      :key := ::ts/extern
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :http-client))
      [:cause-data ::s/problems 1 :pred] := `string?
      [:cause-data ::s/problems 1 :val] := ::invalid))

  (testing "invalid http-client"
    (given-thrown (ig/init {::ts/extern {:http-client ::invalid}})
      :key := ::ts/extern
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :base-uri))
      [:cause-data ::s/problems 1 :pred] := `http-client?
      [:cause-data ::s/problems 1 :val] := ::invalid)))

(deftest terminology-service-test
  (with-system [{ts ::ts/extern ::keys [http-client]} config]

    (-> (.onGet ^HttpClientMock http-client "http://localhost:8080/fhir/ValueSet/$expand?url=http://hl7.org/fhir/ValueSet/administrative-gender")
        (.doReturn (j/write-value-as-string {:resourceType "ValueSet" :id "0"}))
        (.withHeader "content-type" "application/fhir+json"))

    (given @(ts/expand-value-set ts {:url "http://hl7.org/fhir/ValueSet/administrative-gender"})
      :fhir/type := :fhir/ValueSet
      :id := "0")))
