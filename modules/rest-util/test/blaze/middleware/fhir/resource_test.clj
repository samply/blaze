(ns blaze.middleware.fhir.resource-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util]
   [blaze.handler.util :as handler-util]
   [blaze.middleware.fhir.resource :refer [wrap-binary-resource wrap-resource]]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [java.io ByteArrayInputStream]
   [java.nio.charset StandardCharsets]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(defn- wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))

(defn- parse-json [body]
  (fhir-spec/conform-json (fhir-spec/parse-json body)))

(defn- parse-xml [body]
  (with-open [reader (io/reader body)]
    (fhir-spec/conform-xml (xml/parse reader))))

(def ^:private resource-handler
  "A handler which just returns the `:body` from a non-binary resource request."
  (-> (comp ac/completed-future :body)
      wrap-resource
      wrap-error))

(def ^:private binary-resource-handler
  "A handler which just returns the `:body` from a binary resource request."
  (-> (comp ac/completed-future :body)
      wrap-binary-resource
      wrap-error))

(defn- input-stream
  ([^String s]
   (ByteArrayInputStream. (.getBytes s StandardCharsets/UTF_8)))
  ([^String s closed?]
   (proxy [ByteArrayInputStream] [(.getBytes s StandardCharsets/UTF_8)]
     (close []
       (reset! closed? true)))))

(deftest json-test
  (testing "possible content types (JSON)"
    (doseq [content-type ["application/fhir+json" "text/json" "application/json"]]
      (let [closed? (atom false)]
        (given @(resource-handler
                 {:headers {"content-type" content-type}
                  :body (input-stream "{\"resourceType\": \"Patient\"}" closed?)})
          fhir-spec/fhir-type := :fhir/Patient)
        (is (true? @closed?)))))

  (testing "empty body (JSON)"
    (given @(resource-handler
             {:headers {"content-type" "application/fhir+json"}
              :body (input-stream "")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invalid"
      [:body :issue 0 :diagnostics] := "No content to map due to end-of-input\n at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1]"))

  (testing "body with invalid payload (JSON)"
    (given @(resource-handler
             {:headers {"content-type" "application/fhir+json"}
              :body (input-stream "x")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invalid"
      [:body :issue 0 :diagnostics] := "Unrecognized token 'x': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')\n at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 2]"))

  (testing "body with no object (JSON)"
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

  (testing "body with invalid resource (JSON)"
    (given @(resource-handler
             {:headers {"content-type" "application/fhir+json"}
              :body (input-stream "{\"resourceType\": \"Patient\", \"gender\": {}}")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invariant"
      [:body :issue 0 :diagnostics] := "Error on value `{}`. Expected type is `code`, regex `[\\u0021-\\uFFFF]+([ \\t\\n\\r][\\u0021-\\uFFFF]+)*`."
      [:body :issue 0 :expression] := ["gender"]))

  (testing "body with bundle with null resource (JSON)"
    (given @(resource-handler
             {:headers {"content-type" "application/fhir+json"}
              :body (input-stream "{\"resourceType\": \"Bundle\", \"entry\": [{\"resource\": null}]}")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invariant"
      [:body :issue 0 :diagnostics] := "Error on value `null`. Expected type is `Resource`."
      [:body :issue 0 :expression] := ["entry[0].resource"]))

  (testing "body with bundle with invalid resource (JSON)"
    (given @(resource-handler
             {:headers {"content-type" "application/fhir+json"}
              :body (input-stream "{\"resourceType\": \"Bundle\", \"entry\": [{\"resource\": {\"resourceType\": \"Patient\", \"gender\": {}}}]}")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invariant"
      [:body :issue 0 :diagnostics] := "Error on value `{}`. Expected type is `code`, regex `[\\u0021-\\uFFFF]+([ \\t\\n\\r][\\u0021-\\uFFFF]+)*`."
      [:body :issue 0 :expression] := ["entry[0].resource.gender"]))

  (testing "long attribute values are allowed (JSON-wrapped Binary data)"
    (given @(resource-handler
             {:headers {"content-type" "application/fhir+json"}
              :body (input-stream (str "{\"data\" : \"" (apply str (repeat (* 8 1024 1024) \a)) "\", \"resourceType\" : \"Binary\"}"))})
      fhir-spec/fhir-type := :fhir/Binary)))

(deftest xml-test
  (testing "possible content types (XML)"
    (doseq [content-type ["application/fhir+xml" "application/xml"]]
      (let [closed? (atom false)]
        (given @(resource-handler
                 {:headers {"content-type" content-type}
                  :body (input-stream "<Patient xmlns=\"http://hl7.org/fhir\"></Patient>" closed?)})
          fhir-spec/fhir-type := :fhir/Patient)
        (is (true? @closed?)))))

  (testing "empty body (XML)"
    (given @(resource-handler
             {:headers {"content-type" "application/fhir+xml"}
              :body (input-stream "")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invalid"
      [:body :issue 0 :diagnostics] := "Unexpected EOF in prolog\n at [row,col {unknown-source}]: [1,0]"))

  (testing "body with invalid payload (XML)"
    (given @(resource-handler
             {:request-method :post
              :headers {"content-type" "application/fhir+xml"}
              :body (input-stream "<Patient xmlns=\"http://hl7.org/fhir\"><id value \"a_b\"/></Patient>")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invalid"
      [:body :issue 0 :diagnostics] := "Unexpected character '\"' (code 34) expected '='\n at [row,col {unknown-source}]: [1,48]"))

  (testing "body with no object (XML)"
    (given @(resource-handler
             {:headers {"content-type" "application/fhir+xml"}
              :body (input-stream "1")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invalid"
      [:body :issue 0 :diagnostics] := "Unexpected character '1' (code 49) in prolog; expected '<'\n at [row,col {unknown-source}]: [1,1]"))

  (testing "body with invalid resource (XML)"
    (given @(resource-handler
             {:headers {"content-type" "application/fhir+xml"}
              :body (input-stream "<Patient xmlns=\"http://hl7.org/fhir\"><id value=\"a_b\"/></Patient>")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invariant"
      [:body :issue 0 :diagnostics] := "Error on value `a_b`. Expected type is `id`, regex `[A-Za-z0-9\\-\\.]{1,64}`."))

  (testing "body with bundle with empty resource (XML)"
    (given @(resource-handler
             {:headers {"content-type" "application/fhir+xml"}
              :body (input-stream "<Bundle xmlns=\"http://hl7.org/fhir\"><entry><resource></resource></entry></Bundle>")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invariant"
      [:body :issue 0 :diagnostics] := "Error on value `<:resource/>`. Expected type is `Resource`."))

  (testing "body with bundle with invalid resource (XML)"
    (given @(resource-handler
             {:headers {"content-type" "application/fhir+xml"}
              :body (input-stream "<Bundle xmlns=\"http://hl7.org/fhir\"><entry><resource><Patient xmlns=\"http://hl7.org/fhir\"><id value=\"a_b\"/></Patient></resource></entry></Bundle>")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invariant"
      [:body :issue 0 :diagnostics] := "Error on value `a_b`. Expected type is `id`, regex `[A-Za-z0-9\\-\\.]{1,64}`."))

  (testing "long attribute values are allowed (XML-wrapped Binary data)"
    (given @(resource-handler
             {:headers {"content-type" "application/fhir+xml"}
              :body (input-stream (str "<Binary xmlns=\"http://hl7.org/fhir\"><data value=\"" (apply str (repeat (* 8 1024 1024) \a)) "\"/></Binary>"))})
      fhir-spec/fhir-type := :fhir/Binary)))

(deftest binary-test
  (testing "returning the FHIR resource (both as JSON and as XML)"
    (let [binary-data "MTA1NjE0Cg=="]
      (doseq [[content-type body-parser resource-string-representation]
              [["application/fhir+json;charset=utf-8" parse-json (str "{\"data\" : \"" binary-data "\", \"resourceType\" : \"Binary\"}")]
               ["application/fhir+xml;charset=utf-8" parse-xml (str "<Binary xmlns=\"http://hl7.org/fhir\"><data value=\"" binary-data "\"/></Binary>")]]]
        (let [closed? (atom false)]
          (given @(binary-resource-handler
                   {:headers {"content-type" content-type}
                    :body (input-stream resource-string-representation closed?)})
            :status := 200
            identity := "this is what I get"
            fhir-spec/fhir-type := :fhir/Binary
            [:headers "Content-Type"] := content-type
            [:body body-parser] := {:fhir/type :fhir/Binary
                                    :contentType (type/code content-type)
                                    :data #fhir/base64Binary"MTA1NjE0Cg=="}))))))

(comment
  (str "{\"data\" : \"" "MTA1NjE0Cg==" "\", \"resourceType\" : \"Binary\"}")
  ;; => "{\"data\" : \"MTA1NjE0Cg==\", \"resourceType\" : \"Binary\"}"

  (str "<Binary xmlns=\"http://hl7.org/fhir\"><data value=\"" "MTA1NjE0Cg==" "\"/></Binary>")
  ;; => "<Binary xmlns=\"http://hl7.org/fhir\"><data value=\"MTA1NjE0Cg==\"/></Binary>"

  :end)

(def ^:private whitespace
  (gen/fmap str/join (gen/vector (gen/elements [" " "\n" "\r" "\t"]))))

(deftest other-test
  (testing "blank body without content type header results in a nil body"
    (satisfies-prop 10
      (prop/for-all [s whitespace]
        (nil? @(resource-handler {:body (input-stream s)})))))

  (testing "other content is invalid"
    (testing "with unknown content-type header"
      (given @(resource-handler {:headers {"content-type" "text/plain"} :body (input-stream "foo")})
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
