(ns blaze.rest-api.middleware.resource-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.executors :as ex]
    [blaze.fhir.spec.type :as type]
    [blaze.middleware.fhir.error :refer [wrap-error]]
    [blaze.rest-api.middleware.resource :refer [wrap-resource]]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:import
    [java.io ByteArrayInputStream]
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
  "A handler which just returns the :body from the request."
  (-> (comp ac/completed-future :body)
      (wrap-resource executor)
      wrap-error))


(defn input-stream
  ([s]
   (ByteArrayInputStream. (.getBytes s StandardCharsets/UTF_8)))
  ([s closed?]
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
          type/type := :fhir/Patient)
        (is (true? @closed?)))))

  (testing "body with invalid JSON"
    (given @(resource-handler
              {:headers {"content-type" "application/fhir+json"}
               :body (input-stream "x")})
      :status := 400
      [:body type/type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invalid"
      [:body :issue 0 :diagnostics] := "Unrecognized token 'x': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')\n at [Source: (ByteArrayInputStream); line: 1, column: 2]"))

  (testing "body with no JSON object"
    (given @(resource-handler
              {:headers {"content-type" "application/fhir+json"}
               :body (input-stream "1")})
      :status := 400
      [:body type/type] := :fhir/OperationOutcome
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
      [:body type/type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invariant"
      [:body :issue 0 :diagnostics] := "Error on value `{}`. Expected type is `code`, regex `[^\\s]+(\\s[^\\s]+)*`."
      [:body :issue 0 :expression] := ["gender"]))

  (testing "body with bundle with invalid resource"
    (given @(resource-handler
              {:headers {"content-type" "application/fhir+json"}
               :body (input-stream "{\"resourceType\": \"Bundle\", \"entry\": [{\"resource\": {\"resourceType\": \"Patient\", \"gender\": {}}}]}")})
      :status := 400
      [:body type/type] := :fhir/OperationOutcome
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
          type/type := :fhir/Patient)
        (is (true? @closed?)))))

  (testing "body with invalid XML"
    (given @(resource-handler
              {:request-method :post
               :headers {"content-type" "application/fhir+xml"}
               :body (input-stream "<Patient xmlns=\"http://hl7.org/fhir\"><id value \"a_b\"/></Patient>")})
      :status := 400
      [:body type/type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invalid"
      [:body :issue 0 :diagnostics] := "ParseError at [row,col]:[1,48]\nMessage: Attribute name \"value\" associated with an element type \"id\" must be followed by the ' = ' character."))

  (testing "body with invalid resource"
    (given @(resource-handler
              {:headers {"content-type" "application/fhir+xml"}
               :body (input-stream "<Patient xmlns=\"http://hl7.org/fhir\"><id value=\"a_b\"/></Patient>")})
      :status := 400
      [:body type/type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invariant"
      [:body :issue 0 :diagnostics] := "Error on value `a_b`. Expected type is `id`, regex `[A-Za-z0-9\\-\\.]{1,64}`."))

  (testing "body with bundle with invalid resource"
    (given @(resource-handler
              {:headers {"content-type" "application/fhir+xml"}
               :body (input-stream "<Bundle xmlns=\"http://hl7.org/fhir\"><entry><resource><Patient xmlns=\"http://hl7.org/fhir\"><id value=\"a_b\"/></Patient></resource></entry></Bundle>")})
      :status := 400
      [:body type/type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invariant"
      [:body :issue 0 :diagnostics] := "Error on value `a_b`. Expected type is `id`, regex `[A-Za-z0-9\\-\\.]{1,64}`.")))


(deftest other-test
  (testing "other content is invalid"
    (testing "without content-type header"
      (given @(resource-handler {})
        :status := 400
        [:body type/type] := :fhir/OperationOutcome
        [:body :issue 0 :severity] := #fhir/code"error"
        [:body :issue 0 :code] := #fhir/code"invalid"
        [:body :issue 0 :diagnostics] := "Content-Type header expected, but is missing. Please specify one of application/fhir+json` or `application/fhir+xml`."))

    (testing "with unknown content-type header"
      (given @(resource-handler {:headers {"content-type" "text/plain"} :body (input-stream "")})
        :status := 400
        [:body type/type] := :fhir/OperationOutcome
        [:body :issue 0 :severity] := #fhir/code"error"
        [:body :issue 0 :code] := #fhir/code"invalid"
        [:body :issue 0 :diagnostics] := "Unknown Content-Type `text/plain` expected one of application/fhir+json` or `application/fhir+xml`."))

    (testing "missing body"
      (given @(resource-handler
                {:headers {"content-type" "application/fhir+json"}})
        [:body type/type] := :fhir/OperationOutcome
        [:body :issue 0 :severity] := #fhir/code"error"
        [:body :issue 0 :code] := #fhir/code"invalid"
        [:body :issue 0 :diagnostics] := "Missing HTTP body."))))
