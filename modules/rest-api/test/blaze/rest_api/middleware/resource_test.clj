(ns blaze.rest-api.middleware.resource-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.executors :as ex]
    [blaze.fhir.spec.type :as type]
    [blaze.middleware.fhir.error :refer [wrap-error]]
    [blaze.rest-api.middleware.resource :refer [wrap-resource]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:import
    [java.io StringReader]))


(st/instrument)
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


(deftest json-test
  (testing "possible content types"
    (doseq [content-type ["application/fhir+json" "text/json" "application/json"]]
      (given @(resource-handler
                {:headers {"content-type" content-type}
                 :body (StringReader. "{\"resourceType\": \"Patient\"}")})
        type/type := :fhir/Patient)))

  (testing "body with invalid JSON"
    (given @(resource-handler
              {:headers {"content-type" "application/fhir+json"}
               :body (StringReader. "x")})
      :status := 400
      [:body type/type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invalid"
      [:body :issue 0 :diagnostics] := "Unrecognized token 'x': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')\n at [Source: (StringReader); line: 1, column: 2]"))

  (testing "body with no JSON object"
    (given @(resource-handler
              {:headers {"content-type" "application/fhir+json"}
               :body (StringReader. "1")})
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
               :body (StringReader. "{\"resourceType\": \"Patient\", \"gender\": {}}")})
      :status := 400
      [:body type/type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invariant"
      [:body :issue 0 :diagnostics] := "Error on value `{}`. Expected type is `code`, regex `[^\\s]+(\\s[^\\s]+)*`."
      [:body :issue 0 :expression] := ["gender"]))

  (testing "body with bundle with invalid resource"
    (given @(resource-handler
              {:headers {"content-type" "application/fhir+json"}
               :body (StringReader. "{\"resourceType\": \"Bundle\", \"entry\": [{\"resource\": {\"resourceType\": \"Patient\", \"gender\": {}}}]}")})
      :status := 400
      [:body type/type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invariant"
      [:body :issue 0 :diagnostics] := "Error on value `{}`. Expected type is `code`, regex `[^\\s]+(\\s[^\\s]+)*`."
      [:body :issue 0 :expression] := ["entry[0].resource.gender"])))


(deftest xml-test
  (testing "possible content types"
    (doseq [content-type ["application/fhir+xml" "application/xml"]]
      (given @(resource-handler
                {:headers {"content-type" content-type}
                 :body (StringReader. "<Patient xmlns=\"http://hl7.org/fhir\"></Patient>")})
        type/type := :fhir/Patient)))

  (testing "body with invalid XML"
    (given @(resource-handler
              {:request-method :post
               :headers {"content-type" "application/fhir+xml"}
               :body (StringReader. "<Patient xmlns=\"http://hl7.org/fhir\"><id value \"a_b\"/></Patient>")})
      :status := 400
      [:body type/type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invalid"
      [:body :issue 0 :diagnostics] := "ParseError at [row,col]:[1,48]\nMessage: Attribute name \"value\" associated with an element type \"id\" must be followed by the ' = ' character."))

  (testing "body with invalid resource"
    (given @(resource-handler
              {:headers {"content-type" "application/fhir+xml"}
               :body (StringReader. "<Patient xmlns=\"http://hl7.org/fhir\"><id value=\"a_b\"/></Patient>")})
      :status := 400
      [:body type/type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invariant"
      [:body :issue 0 :diagnostics] := "Error on value `a_b`. Expected type is `id`, regex `[A-Za-z0-9\\-\\.]{1,64}`."))

  (testing "body with bundle with invalid resource"
    (given @(resource-handler
              {:headers {"content-type" "application/fhir+xml"}
               :body (StringReader. "<Bundle xmlns=\"http://hl7.org/fhir\"><entry><resource><Patient xmlns=\"http://hl7.org/fhir\"><id value=\"a_b\"/></Patient></resource></entry></Bundle>")})
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
      (given @(resource-handler {:headers {"content-type" "text/plain"} :body ""})
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
