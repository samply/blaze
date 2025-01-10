(ns blaze.middleware.fhir.output-test
  (:require
   [blaze.byte-string :as bs]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util]
   [blaze.middleware.fhir.output :refer [wrap-binary-output wrap-output]]
   [blaze.module.test-util.ring :refer [call]]
   [blaze.test-util :as tu]
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [juxt.iota :refer [given]]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(defn- resource-handler-200 [resource]
  (wrap-output
   (fn [_ respond _]
     (respond (ring/response resource)))))

(def ^:private resource-handler-200-with-patient
  "A handler which just returns a patient."
  (wrap-output
   (fn [_ respond _]
     (respond (ring/response {:fhir/type :fhir/Patient :id "0"})))))

(def ^:private resource-handler-304
  "A handler which returns a 304 Not Modified response."
  (wrap-output
   (fn [_ respond _]
     (respond (ring/status 304)))))

(defn- binary-resource-handler-200
  "A handler which uses the binary middleware and
  returns a binary resource."
  [{:keys [content-type data]}]
  (wrap-binary-output
   (fn [_ respond _]
     (respond
      (ring/response
       (cond-> {:fhir/type :fhir/Binary}
         data (assoc :data (type/base64Binary data))
         content-type (assoc :contentType (type/code content-type))))))))

(def ^:private binary-resource-handler-200-no-body
  "A handler which uses the binary middleware and
  returns a response with 200 and no body."
  (wrap-binary-output
   (fn [_ respond _]
     (respond (ring/status 200)))))

(defn- parse-json [body]
  (fhir-spec/conform-json (fhir-spec/parse-json body)))

(deftest json-test
  (testing "JSON is the default"
    (testing "without accept header"
      (given (call resource-handler-200-with-patient {})
        :status := 200
        [:headers "Content-Type"] := "application/fhir+json;charset=utf-8"
        [:body parse-json] := {:fhir/type :fhir/Patient :id "0"})

      (testing "not modified"
        (given (call resource-handler-304 {})
          :status := 304
          [:headers "Content-Type"] := nil
          :body := nil)))

    (testing "with accept header"
      (doseq [[accept content-type] [["*/*" "application/fhir+json;charset=utf-8"]
                                     ["application/*" "application/fhir+json;charset=utf-8"]
                                     ["text/*" "text/json;charset=utf-8"]]]
        (given (call resource-handler-200-with-patient {:headers {"accept" accept}})
          :status := 200
          [:headers "Content-Type"] := content-type
          [:body parse-json] := {:fhir/type :fhir/Patient :id "0"}))))

  (testing "possible accept headers"
    (doseq [[accept content-type] [["application/fhir+json" "application/fhir+json;charset=utf-8"]
                                   ["application/json" "application/json;charset=utf-8"]
                                   ["text/json" "text/json;charset=utf-8"]
                                   ["application/fhir+xml;q=0.9, application/fhir+json;q=1.0" "application/fhir+json;charset=utf-8"]]]
      (given (call resource-handler-200-with-patient {:headers {"accept" accept}})
        :status := 200
        [:headers "Content-Type"] := content-type
        [:body parse-json] := {:fhir/type :fhir/Patient :id "0"})))

  (testing "_format overrides"
    (doseq [[accept format content-type] [["application/fhir+xml" "application/fhir+json" "application/fhir+json;charset=utf-8"]
                                          ["application/fhir+xml" "application/json" "application/json;charset=utf-8"]
                                          ["application/fhir+xml" "text/json" "text/json;charset=utf-8"]
                                          ["application/fhir+xml" "json" "application/fhir+json;charset=utf-8"]
                                          ["*/*" "application/fhir+json" "application/fhir+json;charset=utf-8"]
                                          ["*/*" "application/json" "application/json;charset=utf-8"]
                                          ["*/*" "text/json" "text/json;charset=utf-8"]
                                          ["*/*" "json" "application/fhir+json;charset=utf-8"]]]
      (given (call resource-handler-200-with-patient
                   {:headers {"accept" accept}
                    :query-params {"_format" format}})
        :status := 200
        [:headers "Content-Type"] := content-type
        [:body parse-json] := {:fhir/type :fhir/Patient :id "0"}))))

(defn- parse-xml [body]
  (with-open [reader (io/reader body)]
    (fhir-spec/conform-xml (xml/parse reader))))

(deftest xml-test
  (testing "possible accept headers"
    (doseq [[accept content-type]
            [["application/fhir+xml" "application/fhir+xml;charset=utf-8"]
             ["application/xml" "application/xml;charset=utf-8"]
             ["text/xml" "text/xml;charset=utf-8"]
             ["application/fhir+json;q=0.9, application/fhir+xml;q=1.0" "application/fhir+xml;charset=utf-8"]
             ;; Safari
             ["text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8" "application/xml;charset=utf-8"]
             ;; Chrome
             ["text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9" "application/xml;charset=utf-8"]
             ;; Edge
             ["text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9" "application/xml;charset=utf-8"]
             ;; Firefox
             ["text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8" "application/xml;charset=utf-8"]]]
      (given (call resource-handler-200-with-patient {:headers {"accept" accept}})
        :status := 200
        [:headers "Content-Type"] := content-type
        [:body parse-xml] := {:fhir/type :fhir/Patient :id "0"})))

  (testing "_format overrides"
    (doseq [[accept format content-type] [["application/fhir+json" "application/fhir+xml" "application/fhir+xml;charset=utf-8"]
                                          ["application/fhir+json" "application/xml" "application/xml;charset=utf-8"]
                                          ["application/fhir+json" "text/xml" "text/xml;charset=utf-8"]
                                          ["application/fhir+json" "xml" "application/fhir+xml;charset=utf-8"]
                                          ["*/*" "application/fhir+xml" "application/fhir+xml;charset=utf-8"]
                                          ["*/*" "application/xml" "application/xml;charset=utf-8"]
                                          ["*/*" "text/xml" "text/xml;charset=utf-8"]
                                          ["*/*" "xml" "application/fhir+xml;charset=utf-8"]]]
      (given (call resource-handler-200-with-patient
                   {:headers {"accept" accept}
                    :query-params {"_format" format}})
        :status := 200
        [:headers "Content-Type"] := content-type
        [:body parse-xml] := {:fhir/type :fhir/Patient :id "0"})))

  (testing "not modified"
    (given (call resource-handler-304 {:headers {"accept" "application/fhir+xml"}})
      :status := 304
      [:headers "Content-Type"] := nil
      :body := nil))

  (testing "failing XML emit"
    (given (call (resource-handler-200 {:fhir/type :fhir/Patient :id "0" :gender #fhir/code"foo\u001Ebar"}) {:headers {"accept" "application/fhir+xml"}})
      :status := 500
      [:headers "Content-Type"] := "application/fhir+xml;charset=utf-8"
      [:body parse-xml :fhir/type] := :fhir/OperationOutcome
      [:body parse-xml :issue 0 :diagnostics] := "Invalid white space character (0x1e) in text to output (in xml 1.1, could output as a character entity)")))

(deftest binary-resource-test
  (testing "returning the resource"
    (testing "JSON"
      (given (call (binary-resource-handler-200 {:content-type "text/plain" :data "MTA1NjE0Cg=="}) {:headers {"accept" "application/fhir+json"}})
        :status := 200
        [:headers "Content-Type"] := "application/fhir+json;charset=utf-8"
        [:body parse-json] := {:fhir/type :fhir/Binary
                               :contentType #fhir/code"text/plain"
                               :data #fhir/base64Binary"MTA1NjE0Cg=="}))

    (testing "XML"
      (given (call (binary-resource-handler-200 {:content-type "text/plain" :data "MTA1NjE0Cg=="}) {:headers {"accept" "application/fhir+xml"}})
        :status := 200
        [:headers "Content-Type"] := "application/fhir+xml;charset=utf-8"
        [:body parse-xml] := {:fhir/type :fhir/Binary
                              :contentType #fhir/code"text/plain"
                              :data #fhir/base64Binary"MTA1NjE0Cg=="})))

  (testing "returning the data"
    (testing "with content type"
      (given (call (binary-resource-handler-200 {:content-type "text/plain" :data "MTA1NjE0Cg=="}) {:headers {"accept" "text/plain"}})
        :status := 200
        [:headers "Content-Type"] := "text/plain"
        [:body bs/from-byte-array] := #blaze/byte-string"3130353631340A"))

    (testing "without content type"
      (given (call (binary-resource-handler-200 {:content-type nil :data "MTA1NjE0Cg=="}) {:headers {"accept" "text/plain"}})
        :status := 200
        [:headers "Content-Type"] := "application/octet-stream"
        [:body bs/from-byte-array] := #blaze/byte-string"3130353631340A"))

    (testing "without data"
      (testing "with content type"
        (given (call (binary-resource-handler-200 {:content-type "text/plain"}) {:headers {"accept" "text/plain"}})
          :status := 200
          [:headers "Content-Type"] := "text/plain"
          :body := nil))

      (testing "without content type"
        (given (call (binary-resource-handler-200 {:content-type nil}) {:headers {"accept" "text/plain"}})
          :status := 200
          [:headers "Content-Type"] := "application/octet-stream"
          :body := nil))

      (testing "without body at all"
        (given (call binary-resource-handler-200-no-body {:headers {"accept" "text/plain"}})
          :status := 200
          [:headers "Content-Type"] := nil
          :body := nil)))

    (testing "failing binary emit (with invalid data)"
      (given (call (binary-resource-handler-200 {:content-type "application/pdf" :data "MTANjECg=="}) {:headers {"accept" "text/plain"}})
        :status := 500
        [:headers "Content-Type"] := "application/fhir+json"
        [:body parse-json :fhir/type] := :fhir/OperationOutcome
        [:body parse-json :issue 0 :diagnostics] := "Input byte array has wrong 4-byte ending unit"))))

(deftest not-acceptable-test
  (is (nil? (call resource-handler-200-with-patient {:headers {"accept" "text/plain"}}))))
