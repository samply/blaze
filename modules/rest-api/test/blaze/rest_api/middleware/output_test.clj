(ns blaze.rest-api.middleware.output-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.executors :as ex]
    [blaze.rest-api.middleware.output :refer [wrap-output]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest testing]]
    [juxt.iota :refer [given]]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(defn fixture [f]
  (st/instrument)
  (log/set-level! :trace)
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


(deftest json-test
  (testing "possible accept headers"
    (are [content-type]
      (given @(resource-handler {:headers {"accept" content-type}})
        :body := "{\"id\":\"0\",\"resourceType\":\"Patient\"}")
      "application/fhir+json"
      "application/json"
      "text/json"))

  (testing "_format overrides"
    (are [format]
      (given @(resource-handler
                {:headers {"accept" "application/xml+json"}
                 :query-params {"_format" format}})
        :body := "{\"id\":\"0\",\"resourceType\":\"Patient\"}")
      "application/json+xml"
      "application/json"
      "text/json"
      "json")))


(deftest xml-test
  (testing "possible accept headers"
    (are [content-type]
      (given @(resource-handler {:headers {"accept" content-type}})
        :body := "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Patient xmlns=\"http://hl7.org/fhir\"><id value=\"0\"/></Patient>")
      "application/fhir+xml"
      "application/xml"
      "text/xml"))

  (testing "_format overrides"
    (are [format]
      (given @(resource-handler
                {:headers {"accept" "application/fhir+json"}
                 :query-params {"_format" format}})
        :body := "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Patient xmlns=\"http://hl7.org/fhir\"><id value=\"0\"/></Patient>")
      "application/fhir+xml"
      "application/xml"
      "text/xml"
      "xml")))
