(ns blaze.fhir.operation.graphql.middleware.query-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir.operation.graphql.middleware.query :refer [wrap-query]]
   [blaze.handler.util :as handler-util]
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
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(defn wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))

(def handler
  "A handler which just returns the :body from the request."
  (-> (comp ac/completed-future :body)
      wrap-query
      wrap-error))

(defn input-stream
  ([^String s]
   (ByteArrayInputStream. (.getBytes s StandardCharsets/UTF_8)))
  ([^String s closed?]
   (proxy [ByteArrayInputStream] [(.getBytes s StandardCharsets/UTF_8)]
     (close []
       (reset! closed? true)))))

(deftest wrap-query-test
  (testing "application/graphql"
    (let [closed? (atom false)]
      (given @(handler
               {:headers {"content-type" "application/graphql"}
                :body (input-stream "query-160125" closed?)})
        :query := "query-160125")
      (is (true? @closed?))))

  (testing "application/json"
    (let [closed? (atom false)]
      (given @(handler
               {:headers {"content-type" "application/json"}
                :body (input-stream "{\"query\": \"query-155956\"}" closed?)})
        :query := "query-155956")
      (is (true? @closed?)))

    (testing "unknown keys are ignored"
      (given @(handler
               {:headers {"content-type" "application/json"}
                :body (input-stream "{\"query\": \"query-155956\", \"foo\": \"bar\"}")})
        :query := "query-155956"
        :foo :? nil?)))

  (testing "body with invalid JSON"
    (given @(handler
             {:headers {"content-type" "application/json"}
              :body (input-stream "x")})
      :status := 400
      [:body :fhir/type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code "error"
      [:body :issue 0 :code] := #fhir/code "invalid"
      [:body :issue 0 :diagnostics] := #fhir/string "Unrecognized token 'x': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')\n at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 2]"))

  (testing "body with no JSON object"
    (given @(handler
             {:headers {"content-type" "application/json"}
              :body (input-stream "1")})
      :status := 400
      [:body :fhir/type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code "error"
      [:body :issue 0 :code] := #fhir/code "structure"
      [:body :issue 0 :details :coding 0 :system] := #fhir/uri "http://terminology.hl7.org/CodeSystem/operation-outcome"
      [:body :issue 0 :details :coding 0 :code] := #fhir/code "MSG_JSON_OBJECT"
      [:body :issue 0 :diagnostics] := #fhir/string "Expect a JSON object."))

  (testing "other content is invalid"
    (testing "without content-type header"
      (given @(handler {})
        :status := 400
        [:body :fhir/type] := :fhir/OperationOutcome
        [:body :issue 0 :severity] := #fhir/code "error"
        [:body :issue 0 :code] := #fhir/code "invalid"
        [:body :issue 0 :diagnostics] := #fhir/string "Content-Type header expected, but is missing."))

    (testing "with unknown content-type header"
      (given @(handler {:headers {"content-type" "text/plain"} :body (input-stream "")})
        :status := 415
        [:body :fhir/type] := :fhir/OperationOutcome
        [:body :issue 0 :severity] := #fhir/code "error"
        [:body :issue 0 :code] := #fhir/code "invalid"
        [:body :issue 0 :diagnostics] := #fhir/string "Unsupported media type `text/plain` expect one of `application/graphql` or `application/json`."))

    (testing "missing body"
      (doseq [content-type ["application/graphql" "application/json"]]
        (given @(handler
                 {:headers {"content-type" content-type}})
          [:body :fhir/type] := :fhir/OperationOutcome
          [:body :issue 0 :severity] := #fhir/code "error"
          [:body :issue 0 :code] := #fhir/code "invalid"
          [:body :issue 0 :diagnostics] := #fhir/string "Missing HTTP body.")))))
