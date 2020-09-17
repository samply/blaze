(ns blaze.rest-api.middleware.resource-test
  (:require
    [blaze.async-comp :as ac]
    [blaze.executors :as ex]
    [blaze.rest-api.middleware.resource :refer [wrap-resource]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:import
    [java.io StringReader]))


(defn fixture [f]
  (st/instrument)
  (log/set-level! :trace)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def executor (ex/single-thread-executor))


(def resource-handler
  "A handler which just returns the :body from the request."
  (wrap-resource (comp ac/completed-future :body) executor))


(deftest json-test
  (testing "possible content types"
    (doseq [content-type ["application/fhir+json" "text/json" "application/json"]]
      (given @(resource-handler
                {:headers {"content-type" content-type}
                 :body (StringReader. "{\"resourceType\": \"Patient\"}")})
        :fhir/type := :fhir/Patient)))

  (testing "body with invalid JSON"
    (given @(resource-handler
              {:headers {"content-type" "application/fhir+json"}
               :body (StringReader. "x")})
      :status := 400
      [:body :fhir/type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invalid"
      [:body :issue 0 :diagnostics] := "Unrecognized token 'x': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')\n at [Source: (BufferedReader); line: 1, column: 2]"))

  (testing "body with no JSON object"
    (given @(resource-handler
              {:headers {"content-type" "application/fhir+json"}
               :body (StringReader. "")})
      :status := 400
      [:body :fhir/type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"structure"
      [:body :issue 0 :details :coding 0 :system] := #fhir/uri"http://terminology.hl7.org/CodeSystem/operation-outcome"
      [:body :issue 0 :details :coding 0 :code] := #fhir/code"MSG_JSON_OBJECT"
      [:body :issue 0 :diagnostics] := "Expect a JSON object."))

  (testing "does nothing on missing body"
    (given @(resource-handler
              {:headers {"content-type" "application/fhir+json"}})
      :body := nil))

  (testing "body with invalid resource"
    (given @(resource-handler
              {:headers {"content-type" "application/fhir+json"}
               :body (StringReader. "{\"resourceType\": \"Patient\", \"gender\":{}}")})
      :status := 400
      [:body :fhir/type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invariant"
      [:body :issue 0 :diagnostics] := "Error on value `{}`. Expected type is `code`."
      [:body :issue 0 :expression] := ["gender"])))


(deftest xml-test
  (testing "possible content types"
    (doseq [content-type ["application/fhir+xml" "application/xml"]]
      (given @(resource-handler
                {:headers {"content-type" content-type}
                 :body (StringReader. "<Patient xmlns=\"http://hl7.org/fhir\"></Patient>")})
        :fhir/type := :fhir/Patient)))

  (testing "body with invalid XML"
    (given @(resource-handler
              {:request-method :post
               :headers {"content-type" "application/fhir+xml"}
               :body (StringReader. "<Patient xmlns=\"http://hl7.org/fhir\"><id value \"a_b\"/></Patient>")})
      :status := 400
      [:body :fhir/type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invalid"
      [:body :issue 0 :diagnostics] := "ParseError at [row,col]:[1,48]\nMessage: Attribute name \"value\" associated with an element type \"id\" must be followed by the ' = ' character."))

  (testing "body with invalid resource"
    (given @(resource-handler
              {:headers {"content-type" "application/fhir+xml"}
               :body (StringReader. "<Patient xmlns=\"http://hl7.org/fhir\"><id value=\"a_b\"/></Patient>")})
      :status := 400
      [:body :fhir/type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invariant"
      [:body :issue 0 :diagnostics] := "Error on value `a_b`. Expected type is `id`, regex `[A-Za-z0-9\\-\\.]{1,64}`."))

  (testing "does nothing on missing body"
    (given @(resource-handler
              {:headers {"content-type" "application/fhir+xml"}})
      :body := nil)))
