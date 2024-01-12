(ns blaze.rest-api.middleware.output-test
  (:require
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec-spec]
   [blaze.module.test-util.ring :refer [call]]
   [blaze.rest-api.middleware.output :refer [wrap-output]]
   [blaze.test-util :as tu]
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [juxt.iota :refer [given]]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-level! :trace)

(test/use-fixtures :each tu/fixture)

(def resource-handler-200
  "A handler which just returns a patient."
  (wrap-output
   (fn [_ respond _]
     (respond (ring/response {:fhir/type :fhir/Patient :id "0"})))))

(def resource-handler-304
  "A handler which returns a 304 Not Modified response."
  (wrap-output
   (fn [_ respond _]
     (respond (ring/status 304)))))

(defn- parse-json [body]
  (fhir-spec/conform-json (fhir-spec/parse-json body)))

(deftest json-test
  (testing "JSON is the default"
    (testing "without accept header"
      (given (call resource-handler-200 {})
        [:headers "Content-Type"] := "application/fhir+json;charset=utf-8"
        [:body parse-json] := {:fhir/type :fhir/Patient :id "0"})

      (testing "not modified"
        (given (call resource-handler-304 {})
          [:headers "Content-Type"] := "application/fhir+json;charset=utf-8"
          :body := nil)))

    (testing "with accept header"
      (are [accept content-type]
           (given (call resource-handler-200 {:headers {"accept" accept}})
             [:headers "Content-Type"] := content-type
             [:body parse-json] := {:fhir/type :fhir/Patient :id "0"})
        "*/*" "application/fhir+json;charset=utf-8"
        "application/*" "application/fhir+json;charset=utf-8"
        "text/*" "text/json;charset=utf-8")))

  (testing "possible accept headers"
    (are [accept content-type]
         (given (call resource-handler-200 {:headers {"accept" accept}})
           [:headers "Content-Type"] := content-type
           [:body parse-json] := {:fhir/type :fhir/Patient :id "0"})
      "application/fhir+json" "application/fhir+json;charset=utf-8"
      "application/json" "application/json;charset=utf-8"
      "text/json" "text/json;charset=utf-8"
      "application/fhir+xml;q=0.9, application/fhir+json;q=1.0" "application/fhir+json;charset=utf-8"))

  (testing "_format overrides"
    (are [accept format content-type]
         (given (call resource-handler-200
                      {:headers {"accept" accept}
                       :query-params {"_format" format}})
           [:headers "Content-Type"] := content-type
           [:body parse-json] := {:fhir/type :fhir/Patient :id "0"})
      "application/fhir+xml"
      "application/fhir+json"
      "application/fhir+json;charset=utf-8"

      "application/fhir+xml"
      "application/json"
      "application/json;charset=utf-8"

      "application/fhir+xml"
      "text/json"
      "text/json;charset=utf-8"

      "application/fhir+xml"
      "json"
      "application/fhir+json;charset=utf-8"

      "*/*"
      "application/fhir+json"
      "application/fhir+json;charset=utf-8"

      "*/*"
      "application/json"
      "application/json;charset=utf-8"

      "*/*"
      "text/json"
      "text/json;charset=utf-8"

      "*/*"
      "json"
      "application/fhir+json;charset=utf-8")))

(defn- parse-xml [body]
  (with-open [reader (io/reader body)]
    (fhir-spec/conform-xml (xml/parse reader))))

(deftest xml-test
  (testing "possible accept headers"
    (are [accept content-type]
         (given (call resource-handler-200 {:headers {"accept" accept}})
           [:headers "Content-Type"] := content-type
           [:body parse-xml] := {:fhir/type :fhir/Patient :id "0"})
      "application/fhir+xml"
      "application/fhir+xml;charset=utf-8"

      "application/xml"
      "application/xml;charset=utf-8"

      "text/xml"
      "text/xml;charset=utf-8"

      "application/fhir+json;q=0.9, application/fhir+xml;q=1.0"
      "application/fhir+xml;charset=utf-8"

      ;; Safari
      "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
      "application/xml;charset=utf-8"

      ;; Chrome
      "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
      "application/xml;charset=utf-8"

      ;; Edge
      "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
      "application/xml;charset=utf-8"

      ;; Firefox
      "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
      "application/xml;charset=utf-8"))

  (testing "_format overrides"
    (are [accept format content-type]
         (given (call resource-handler-200
                      {:headers {"accept" accept}
                       :query-params {"_format" format}})
           [:headers "Content-Type"] := content-type
           [:body parse-xml] := {:fhir/type :fhir/Patient :id "0"})
      "application/fhir+json"
      "application/fhir+xml"
      "application/fhir+xml;charset=utf-8"

      "application/fhir+json"
      "application/xml"
      "application/xml;charset=utf-8"

      "application/fhir+json"
      "text/xml"
      "text/xml;charset=utf-8"

      "application/fhir+json"
      "xml"
      "application/fhir+xml;charset=utf-8"

      "*/*"
      "application/fhir+xml"
      "application/fhir+xml;charset=utf-8"

      "*/*"
      "application/xml"
      "application/xml;charset=utf-8"

      "*/*"
      "text/xml"
      "text/xml;charset=utf-8"

      "*/*"
      "xml"
      "application/fhir+xml;charset=utf-8"))

  (testing "not modified"
    (given (call resource-handler-304 {:headers {"accept" "application/fhir+xml"}})
      [:headers "Content-Type"] := "application/fhir+xml;charset=utf-8"
      :body := nil)))

(deftest not-acceptable-test
  (is (nil? (call resource-handler-200 {:headers {"accept" "text/plain"}}))))
