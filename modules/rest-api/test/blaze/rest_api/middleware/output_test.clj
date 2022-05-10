(ns blaze.rest-api.middleware.output-test
  (:require
    [blaze.fhir.spec-spec]
    [blaze.rest-api.middleware.output :refer [wrap-output]]
    [blaze.test-util :as tu]
    [blaze.test-util.ring :refer [call parse-json parse-xml]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [juxt.iota :refer [given]]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


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


(deftest json-test
  (testing "JSON is the default"
    (testing "without accept header"
      (given (call resource-handler {})
        [:body parse-json] := {:resourceType "Patient" :id "0"}))

    (testing "with accept header"
      (are [accept] (given (call resource-handler {:headers {"accept" accept}})
        [:body parse-json] := {:resourceType "Patient" :id "0"})
        "*/*"
        "application/*"
        "text/*")))

  (testing "possible accept headers"
    (are [accept]
      (given (call resource-handler {:headers {"accept" accept}})
        [:body parse-json] := {:resourceType "Patient" :id "0"})
      "application/fhir+json"
      "application/json"
      "text/json"
      "application/fhir+xml;q=0.9, application/fhir+json;q=1.0"))

  (testing "_format overrides"
    (are [accept format]
      (given (call resource-handler
                   {:headers {"accept" accept}
                    :query-params {"_format" format}})
        [:body parse-json] := {:resourceType "Patient" :id "0"})
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
        [:body parse-xml] := {:fhir/type :fhir/Patient :id "0"})
      "application/fhir+xml"
      "application/xml"
      "text/xml"
      "application/fhir+json;q=0.9, application/fhir+xml;q=1.0"))

  (testing "_format overrides"
    (are [accept format]
      (given (call resource-handler
                   {:headers {"accept" accept}
                    :query-params {"_format" format}})
        [:body parse-xml] := {:fhir/type :fhir/Patient :id "0"})
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
