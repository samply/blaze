(ns blaze.terminology-service.extern-test
  (:require
    [blaze.terminology-service :as ts]
    [blaze.terminology-service.extern]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest]]
    [integrant.core :as ig]
    [jsonista.core :as j]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:import
    [com.pgssoft.httpclient HttpClientMock]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (log/with-level :trace (f))
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn- terminology-service [base-uri http-client]
  (-> (ig/init
        {:blaze.terminology-service/extern
         {:base-uri base-uri
          :http-client http-client}})
      (:blaze.terminology-service/extern)))


(deftest terminology-service-test
  (let [http-client (HttpClientMock.)
        ts (terminology-service "http://localhost:8080/fhir" http-client)]

    (-> (.onGet http-client "http://localhost:8080/fhir/ValueSet/$expand?url=http://hl7.org/fhir/ValueSet/administrative-gender")
        (.doReturn (j/write-value-as-string {:resourceType "ValueSet" :id "0"}))
        (.withHeader "content-type" "application/fhir+json"))

    (given @(ts/expand-value-set ts {:url "http://hl7.org/fhir/ValueSet/administrative-gender"})
      :fhir/type := :fhir/ValueSet
      :id := "0")))
