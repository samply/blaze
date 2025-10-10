(ns blaze.middleware.fhir.output-test
  (:require
   [blaze.byte-string :as bs]
   [blaze.fhir.parsing-context]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.fhir.writing-context]
   [blaze.middleware.fhir.output :refer [wrap-binary-output wrap-output]]
   [blaze.middleware.fhir.output-spec]
   [blaze.module.test-util.ring :refer [call]]
   [blaze.test-util :as tu]
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [ring.core.protocols :as rp]
   [ring.util.response :as ring]
   [taoensso.timbre :as log])
  (:import
   [java.io ByteArrayOutputStream]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private parsing-context
  (ig/init-key
   :blaze.fhir/parsing-context
   {:structure-definition-repo structure-definition-repo}))

(def ^:private writing-context
  (ig/init-key
   :blaze.fhir/writing-context
   {:structure-definition-repo structure-definition-repo}))

(defn- resource-handler-200 [resource]
  (wrap-output
   (fn [_ respond _]
     (respond (ring/response resource)))
   writing-context))

(def ^:private resource-handler-200-with-patient
  "A handler which just returns a patient."
  (wrap-output
   (fn [_ respond _]
     (respond (ring/response {:fhir/type :fhir/Patient :id "0"})))
   writing-context))

(def ^:private resource-handler-304
  "A handler which returns a 304 Not Modified response."
  (wrap-output
   (fn [_ respond _]
     (respond (ring/status 304)))
   writing-context))

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
         content-type (assoc :contentType (type/code content-type))))))
   writing-context))

(def ^:private binary-resource-handler-200-no-body
  "A handler which uses the binary middleware and
  returns a response with 200 and no body."
  (wrap-binary-output
   (fn [_ respond _]
     (respond (ring/status 200)))
   writing-context))

(defn- parse-json [body]
  (let [out (ByteArrayOutputStream.)]
    (rp/write-body-to-stream body nil out)
    (fhir-spec/parse-json parsing-context (.toByteArray out))))

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
    (given (call (resource-handler-200 {:fhir/type :fhir/Patient :id "0" :gender #fhir/code "foo\u001Ebar"}) {:headers {"accept" "application/fhir+xml"}})
      :status := 500
      [:headers "Content-Type"] := "application/fhir+xml;charset=utf-8"
      [:body parse-xml :fhir/type] := :fhir/OperationOutcome
      [:body parse-xml :issue 0 :diagnostics] := #fhir/string "Invalid white space character (0x1e) in text to output (in xml 1.1, could output as a character entity)")))

(deftest binary-resource-test
  (testing "by default, the binary data gets wrapped inside a JSON FHIR-resource"
    (given (call (binary-resource-handler-200 {:content-type nil :data "MTA1NjE0Cg=="}) {:headers {}})
      :status := 200
      [:headers "Content-Type"] := "application/fhir+json;charset=utf-8"
      [:body parse-json] := {:fhir/type :fhir/Binary
                             :data #fhir/base64Binary "MTA1NjE0Cg=="}))

  (testing "explicitly requesting the binary data to be wrapped inside a FHIR-resource (both as JSON and as XML)"
    (doseq [[accept-header content-type body-parser]
            [["application/fhir+json" "application/fhir+json;charset=utf-8" parse-json]
             ["application/fhir+xml" "application/fhir+xml;charset=utf-8" parse-xml]]]
      (given (call (binary-resource-handler-200 {:content-type "text/plain" :data "MTA1NjE0Cg=="}) {:headers {"accept" accept-header}})
        :status := 200
        [:headers "Content-Type"] := content-type
        [:body body-parser] := {:fhir/type :fhir/Binary
                                :contentType #fhir/code "text/plain"
                                :data #fhir/base64Binary "MTA1NjE0Cg=="})))

  (testing "explicitly requesting raw binary data"
    (testing "with valid data"
      (testing "requesting the same non-FHIR content-type as the one provided by the binary-resource"
        (given (call (binary-resource-handler-200 {:content-type "text/binary-resource-content-type" :data "MTA1NjE0Cg=="}) {:headers {"accept" "text/binary-resource-content-type"}})
          :status := 200
          [:headers "Content-Type"] := "text/binary-resource-content-type"
          [:body bs/from-byte-array] := #blaze/byte-string"3130353631340A"))

      (testing "requesting a different non-FHIR content-type from the one provided by the binary-resource"
        (testing "when the binary-resource explicitly states its content-type"
          (given (call (binary-resource-handler-200 {:content-type "text/actual-binary-resource-content-type" :data "MTA1NjE0Cg=="}) {:headers {"accept" "text/requested-non-fhir-content-type"}})
            :status := 200
            [:headers "Content-Type"] := "text/actual-binary-resource-content-type"
            [:body bs/from-byte-array] := #blaze/byte-string"3130353631340A"))

        (testing "when the binary-resource does not explicitly states its content-type"
          (given (call (binary-resource-handler-200 {:content-type nil :data "MTA1NjE0Cg=="}) {:headers {"accept" "text/requested-non-fhir-content-type"}})
            :status := 200
            [:headers "Content-Type"] := "application/octet-stream"
            [:body bs/from-byte-array] := #blaze/byte-string"3130353631340A"))))

    (testing "without data"
      (testing "requesting the same non-FHIR content-type as the one provided by the binary-resource"
        (given (call (binary-resource-handler-200 {:content-type "text/binary-resource-content-type"}) {:headers {"accept" "text/binary-resource-content-type"}})
          :status := 200
          [:headers "Content-Type"] := "text/binary-resource-content-type"
          :body := nil))

      (testing "requesting a different non-FHIR content-type from the one provided by the binary resource"
        (testing "when the binary-resource explicitly states its content-type"
          (given (call (binary-resource-handler-200 {:content-type "text/actual-binary-resource-content-type"}) {:headers {"accept" "text/any-requested-non-fhir-content-type"}})
            :status := 200
            [:headers "Content-Type"] := "text/actual-binary-resource-content-type"
            :body := nil))

        (testing "when the binary-resource does not explicitly states its content-type"
          (given (call (binary-resource-handler-200 {:content-type nil}) {:headers {"accept" "text/requested-non-fhir-content-type"}})
            :status := 200
            [:headers "Content-Type"] := "application/octet-stream"
            :body := nil)))

      (testing "without body at all"
        (given (call binary-resource-handler-200-no-body {:headers {"accept" "text/any-requested-non-fhir-content-type"}})
          :status := 200
          [:headers "Content-Type"] := nil
          :body := nil)))

    (testing "failing binary emit / with invalid data"
      (given (call (binary-resource-handler-200 {:content-type "text/actual-binary-resource-content-type" :data "MTANjECg=="}) {:headers {"accept" "text/any-requested-non-fhir-content-type"}})
        :status := 500
        [:headers "Content-Type"] := "application/fhir+json"
        [:body parse-json :fhir/type] := :fhir/OperationOutcome
        [:body parse-json :issue 0 :diagnostics] := #fhir/string "Input byte array has wrong 4-byte ending unit"))))

(deftest not-acceptable-test
  (is (nil? (call resource-handler-200-with-patient {:headers {"accept" "text/plain"}}))))
