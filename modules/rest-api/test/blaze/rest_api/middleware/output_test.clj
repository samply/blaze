(ns blaze.rest-api.middleware.output-test
  (:require
    [blaze.fhir.spec-spec]
    [blaze.rest-api.middleware.output :refer [wrap-output]]
    [blaze.test-util :as tu]
    [blaze.test-util.ring :refer [call]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [juxt.iota :refer [given]]
    [ring.util.response :as ring]
    [taoensso.timbre :as log])
  (:import
    [java.nio.charset StandardCharsets]))


(st/instrument)
(tu/init-fhir-specs)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def resource-handler
  "A handler which just returns a patient."
  (wrap-output
    (fn [_ respond _]
      (respond (ring/response {:fhir/type :fhir/Patient :id "0"})))))


(defn- bytes->str [^bytes bs]
  (String. bs StandardCharsets/UTF_8))


(deftest json-test
  (testing "JSON is the default"
    (testing "without accept header"
      (given (call resource-handler {})
        [:body bytes->str] := "{\"id\":\"0\",\"resourceType\":\"Patient\"}"))

    (testing "with accept header"
      (are [accept] (given (call resource-handler {:headers {"accept" accept}})
        [:body bytes->str] := "{\"id\":\"0\",\"resourceType\":\"Patient\"}")
        "*/*"
        "application/*"
        "text/*")))

  (testing "possible accept headers"
    (are [accept]
      (given (call resource-handler {:headers {"accept" accept}})
        [:body bytes->str] := "{\"id\":\"0\",\"resourceType\":\"Patient\"}")
      "application/fhir+json"
      "application/json"
      "text/json"
      "application/fhir+xml;q=0.9, application/fhir+json;q=1.0"))

  (testing "_format overrides"
    (are [accept format]
      (given (call resource-handler
                   {:headers {"accept" accept}
                    :query-params {"_format" format}})
        [:body bytes->str] := "{\"id\":\"0\",\"resourceType\":\"Patient\"}")
      "application/fhir+xml" "application/fhir+json"
      "application/fhir+xml" "application/json"
      "application/fhir+xml" "text/json"
      "application/fhir+xml" "json"
      "*/*" "application/fhir+json"
      "*/*" "application/json"
      "*/*" "text/json"
      "*/*" "json")))


(deftest xml-test
  (testing "possible accept headers"
    (are [accept]
      (given (call resource-handler {:headers {"accept" accept}})
        [:body bytes->str] :=
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Patient xmlns=\"http://hl7.org/fhir\"><id value=\"0\"/></Patient>")
      "application/fhir+xml"
      "application/xml"
      "text/xml"
      "application/fhir+json;q=0.9, application/fhir+xml;q=1.0"))

  (testing "_format overrides"
    (are [accept format]
      (given (call resource-handler
                   {:headers {"accept" accept}
                    :query-params {"_format" format}})
        [:body bytes->str] :=
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Patient xmlns=\"http://hl7.org/fhir\"><id value=\"0\"/></Patient>")
      "application/fhir+json" "application/fhir+xml"
      "application/fhir+json" "application/xml"
      "application/fhir+json" "text/xml"
      "application/fhir+json" "xml"
      "*/*" "application/fhir+xml"
      "*/*" "application/xml"
      "*/*" "text/xml"
      "*/*" "xml")))


(deftest not-acceptable-test
  (is (nil? (call resource-handler {:headers {"accept" "text/plain"}}))))
