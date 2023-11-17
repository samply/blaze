(ns blaze.rest-api.middleware.resource-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.handler.util :as handler-util]
   [blaze.rest-api.middleware.resource :refer [wrap-resource]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [java.io ByteArrayInputStream]
   [java.nio.charset StandardCharsets]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-level! :trace)

(test/use-fixtures :each tu/fixture)

(defn wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))

(def resource-handler
  "A handler which just returns the :body from the request."
  (-> (comp ac/completed-future :body)
      wrap-resource
      wrap-error))

(defn input-stream
  ([^String s]
   (ByteArrayInputStream. (.getBytes s StandardCharsets/UTF_8)))
  ([^String s closed?]
   (proxy [ByteArrayInputStream] [(.getBytes s StandardCharsets/UTF_8)]
     (close []
       (reset! closed? true)))))

(deftest json-test
  (testing "possible content types"
    (doseq [content-type ["application/fhir+json" "text/json" "application/json"]]
      (let [closed? (atom false)]
        (given @(resource-handler
                 {:headers {"content-type" content-type}
                  :body (input-stream "{\"resourceType\": \"Patient\"}" closed?)})
          fhir-spec/fhir-type := :fhir/Patient)
        (is (true? @closed?)))))

  (testing "body with invalid JSON"
    (given @(resource-handler
             {:headers {"content-type" "application/fhir+json"}
              :body (input-stream "x")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invalid"
      [:body :issue 0 :diagnostics] := "Unrecognized token 'x': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')\n at [Source: (ByteArrayInputStream); line: 1, column: 2]"))

  (testing "body with no JSON object"
    (given @(resource-handler
             {:headers {"content-type" "application/fhir+json"}
              :body (input-stream "1")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"structure"
      [:body :issue 0 :details :coding 0 :system] := #fhir/uri"http://terminology.hl7.org/CodeSystem/operation-outcome"
      [:body :issue 0 :details :coding 0 :code] := #fhir/code"MSG_JSON_OBJECT"
      [:body :issue 0 :diagnostics] := "Expect a JSON object."))

  (testing "body with invalid resource"
    (given @(resource-handler
             {:headers {"content-type" "application/fhir+json"}
              :body (input-stream "{\"resourceType\": \"Patient\", \"gender\": {}}")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invariant"
      [:body :issue 0 :diagnostics] := "Error on value `{}`. Expected type is `code`, regex `[^\\s]+(\\s[^\\s]+)*`."
      [:body :issue 0 :expression] := ["gender"]))

  (testing "body with bundle with null resource"
    (given @(resource-handler
             {:headers {"content-type" "application/fhir+json"}
              :body (input-stream "{\"resourceType\": \"Bundle\", \"entry\": [{\"resource\": null}]}")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invariant"
      [:body :issue 0 :diagnostics] := "Error on value `null`. Expected type is `Resource`."
      [:body :issue 0 :expression] := ["entry[0].resource"]))

  (testing "body with bundle with invalid resource"
    (given @(resource-handler
             {:headers {"content-type" "application/fhir+json"}
              :body (input-stream "{\"resourceType\": \"Bundle\", \"entry\": [{\"resource\": {\"resourceType\": \"Patient\", \"gender\": {}}}]}")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invariant"
      [:body :issue 0 :diagnostics] := "Error on value `{}`. Expected type is `code`, regex `[^\\s]+(\\s[^\\s]+)*`."
      [:body :issue 0 :expression] := ["entry[0].resource.gender"])))

(deftest xml-test
  (testing "possible content types"
    (doseq [content-type ["application/fhir+xml" "application/xml"]]
      (let [closed? (atom false)]
        (given @(resource-handler
                 {:headers {"content-type" content-type}
                  :body (input-stream "<Patient xmlns=\"http://hl7.org/fhir\"></Patient>" closed?)})
          fhir-spec/fhir-type := :fhir/Patient)
        (is (true? @closed?)))))

  (testing "long attribute values are allowed"
    (given @(resource-handler
             {:headers {"content-type" "application/fhir+xml"}
              :body (input-stream (str "<Binary xmlns=\"http://hl7.org/fhir\"><data value=\"" (apply str (repeat (* 8 1024 1024) \a)) "\"/></Binary>"))})
      fhir-spec/fhir-type := :fhir/Binary))

  (testing "body with invalid XML"
    (given @(resource-handler
             {:request-method :post
              :headers {"content-type" "application/fhir+xml"}
              :body (input-stream "<Patient xmlns=\"http://hl7.org/fhir\"><id value \"a_b\"/></Patient>")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invalid"
      [:body :issue 0 :diagnostics] := "Unexpected character '\"' (code 34) expected '='\n at [row,col {unknown-source}]: [1,48]"))

  (testing "body with invalid resource"
    (given @(resource-handler
             {:headers {"content-type" "application/fhir+xml"}
              :body (input-stream "<Patient xmlns=\"http://hl7.org/fhir\"><id value=\"a_b\"/></Patient>")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invariant"
      [:body :issue 0 :diagnostics] := "Error on value `a_b`. Expected type is `id`, regex `[A-Za-z0-9\\-\\.]{1,64}`."))

  (testing "body with bundle with empty resource"
    (given @(resource-handler
             {:headers {"content-type" "application/fhir+xml"}
              :body (input-stream "<Bundle xmlns=\"http://hl7.org/fhir\"><entry><resource></resource></entry></Bundle>")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invariant"
      [:body :issue 0 :diagnostics] := "Error on value `<:resource/>`. Expected type is `Resource`."))

  (testing "body with bundle with invalid resource"
    (given @(resource-handler
             {:headers {"content-type" "application/fhir+xml"}
              :body (input-stream "<Bundle xmlns=\"http://hl7.org/fhir\"><entry><resource><Patient xmlns=\"http://hl7.org/fhir\"><id value=\"a_b\"/></Patient></resource></entry></Bundle>")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invariant"
      [:body :issue 0 :diagnostics] := "Error on value `a_b`. Expected type is `id`, regex `[A-Za-z0-9\\-\\.]{1,64}`.")))

(deftest other-test
  (testing "other content is invalid"
    (testing "without content-type header"
      (given @(resource-handler {})
        :status := 400
        [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
        [:body :issue 0 :severity] := #fhir/code"error"
        [:body :issue 0 :code] := #fhir/code"invalid"
        [:body :issue 0 :diagnostics] := "Content-Type header expected, but is missing."))

    (testing "with unknown content-type header"
      (given @(resource-handler {:headers {"content-type" "text/plain"} :body (input-stream "")})
        :status := 415
        [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
        [:body :issue 0 :severity] := #fhir/code"error"
        [:body :issue 0 :code] := #fhir/code"invalid"
        [:body :issue 0 :diagnostics] := "Unsupported media type `text/plain` expect one of `application/fhir+json` or `application/fhir+xml`."))

    (testing "missing body"
      (doseq [content-type ["application/fhir+json" "application/fhir+xml"]]
        (given @(resource-handler
                 {:headers {"content-type" content-type}})
          [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
          [:body :issue 0 :severity] := #fhir/code"error"
          [:body :issue 0 :code] := #fhir/code"invalid"
          [:body :issue 0 :diagnostics] := "Missing HTTP body.")))))
