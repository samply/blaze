(ns blaze.rest-api.middleware.output-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.executors :as ex]
    [blaze.fhir.spec-spec]
    [blaze.rest-api.middleware.output :refer [wrap-output]]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest testing]]
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


(def executor (ex/single-thread-executor))


(def resource-handler
  "A handler which just returns a patient."
  (wrap-output
    (fn [_]
      (ac/completed-future
        (ring/response {:fhir/type :fhir/Patient :id "0"})))))


(defn- bytes->str [^bytes bs]
  (String. bs StandardCharsets/UTF_8))


(deftest json-test
  (testing "possible accept headers"
    (are [content-type]
      (given @(resource-handler {:headers {"accept" content-type}})
        [:body bytes->str] := "{\"id\":\"0\",\"resourceType\":\"Patient\"}")
      "application/fhir+json"
      "application/json"
      "text/json"))

  (testing "_format overrides"
    (are [accept format]
      (given @(resource-handler
                {:headers {"accept" accept}
                 :query-params {"_format" format}})
        [:body bytes->str] := "{\"id\":\"0\",\"resourceType\":\"Patient\"}")
      "application/fhir+xml" "application/json+xml"
      "application/fhir+xml" "application/json"
      "application/fhir+xml" "text/json"
      "application/fhir+xml" "json"
      "*/*" "application/json+xml"
      "*/*" "application/json"
      "*/*" "text/json"
      "*/*" "json")))


(deftest xml-test
  (testing "possible accept headers"
    (are [content-type]
      (given @(resource-handler {:headers {"accept" content-type}})
        [:body bytes->str] :=
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Patient xmlns=\"http://hl7.org/fhir\"><id value=\"0\"/></Patient>")
      "application/fhir+xml"
      "application/xml"
      "text/xml"))

  (testing "_format overrides"
    (are [accept format]
      (given @(resource-handler
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
