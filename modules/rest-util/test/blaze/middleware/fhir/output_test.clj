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
   [clojure.test :as test :refer [are deftest is testing]]
   [juxt.iota :refer [given]]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private resource-handler-200
  "A handler which just returns a patient."
  (wrap-output
   (fn [_ respond _]
     (respond (ring/response {:fhir/type :fhir/Patient :id "0"})))))

(defn- binary-resource-handler-200
  "A handler which uses the binary middleware and
  returns a binary resource."
  [{:keys [content-type data] :as _body}]
  (wrap-binary-output
   (fn [_ respond _]
     (respond
      (ring/response
       (cond-> {:fhir/type :fhir/Binary}
         data (assoc :data (type/base64Binary data))
         content-type (assoc :contentType (type/code content-type))))))))

(def ^:private resource-handler-304
  "A handler which returns a 304 Not Modified response."
  (wrap-output
   (fn [_ respond _]
     (respond (ring/status 304)))))

(def ^:private binary-resource-handler-304
  "A handler which uses the binary middleware and
  returns a 304 Not Modified response."
  (wrap-binary-output
   (fn [_ respond _]
     (respond (ring/status 304)))))

(defn- special-resource-handler [resource]
  (wrap-output
   (fn [_ respond _]
     (respond (ring/response resource)))))

(defn- parse-json [body]
  (fhir-spec/conform-json (fhir-spec/parse-json body)))

(deftest json-test
  (testing "JSON is the default"
    (testing "without accept header"
      (given (call resource-handler-200 {})
        :status := 200
        [:headers "Content-Type"] := "application/fhir+json;charset=utf-8"
        [:body parse-json] := {:fhir/type :fhir/Patient :id "0"})

      (testing "not modified"
        (given (call resource-handler-304 {})
          :status := 304
          [:headers "Content-Type"] := nil
          :body := nil)))

    (testing "with accept header"
      (are [accept content-type]
           (given (call resource-handler-200 {:headers {"accept" accept}})
             :status := 200
             [:headers "Content-Type"] := content-type
             [:body parse-json] := {:fhir/type :fhir/Patient :id "0"})
        "*/*" "application/fhir+json;charset=utf-8"
        "application/*" "application/fhir+json;charset=utf-8"
        "text/*" "text/json;charset=utf-8")))

  (testing "possible accept headers"
    (are [accept content-type]
         (given (call resource-handler-200 {:headers {"accept" accept}})
           :status := 200
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
           :status := 200
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
           :status := 200
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
           :status := 200
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
      :status := 304
      [:headers "Content-Type"] := nil
      :body := nil))

  (testing "failing XML emit"
    (given (call (special-resource-handler {:fhir/type :fhir/Patient :id "0" :gender #fhir/code"foo\u001Ebar"}) {:headers {"accept" "application/fhir+xml"}})
      [:headers "Content-Type"] := "application/fhir+xml;charset=utf-8"
      [:body parse-xml :issue 0 :diagnostics] := "Invalid white space character (0x1e) in text to output (in xml 1.1, could output as a character entity)")))

(deftest binary-resource-test
  (testing "possible accept headers"
    (are [accept content-type body]
         (given (call (binary-resource-handler-200 {:content-type "text/plain" :data "MTA1NjE0Cg=="}) {:headers {"accept" accept}})
           :status := 200
           [:headers "Content-Type"] := content-type
           [:body bs/from-byte-array] := body)
      "application/fhir+json" "application/fhir+json;charset=utf-8" #blaze/byte-string"7B2264617461223A224D5441314E6A453043673D3D222C22636F6E74656E7454797065223A22746578742F706C61696E222C227265736F7572636554797065223A2242696E617279227D"
      "application/fhir+xml" "application/fhir+xml;charset=utf-8" #blaze/byte-string"3C3F786D6C2076657273696F6E3D27312E302720656E636F64696E673D275554462D38273F3E3C42696E61727920786D6C6E733D22687474703A2F2F686C372E6F72672F66686972223E3C636F6E74656E74547970652076616C75653D22746578742F706C61696E222F3E3C646174612076616C75653D224D5441314E6A453043673D3D222F3E3C2F42696E6172793E"))

  (testing "with data"
    (testing "with content type"
      (given (call (binary-resource-handler-200 {:content-type "text/plain" :data "MTA1NjE0Cg=="}) {:headers {"accept" "text/plain"}})
        :status := 200
        [:headers "Content-Type"] := "text/plain"
        [:body bs/from-byte-array] := #blaze/byte-string"3130353631340A"))

    (testing "without content type"
      (given (call (binary-resource-handler-200 {:content-type nil :data "MTA1NjE0Cg=="}) {:headers {"accept" "text/plain"}})
        :status := 200
        [:headers "Content-Type"] := "application/octet-stream"
        [:body bs/from-byte-array] := #blaze/byte-string"3130353631340A")))

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
        :body := nil)))

  (testing "without body at all"
    (given (call binary-resource-handler-304 {:headers {"accept" "text/plain"}})
      :status := 304
      [:headers "Content-Type"] := nil
      :body := nil)))

(deftest not-acceptable-test
  (is (nil? (call resource-handler-200 {:headers {"accept" "text/plain"}}))))
