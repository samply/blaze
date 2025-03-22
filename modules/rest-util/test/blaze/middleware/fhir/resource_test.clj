(ns blaze.middleware.fhir.resource-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util]
   [blaze.handler.util :as handler-util]
   [blaze.middleware.fhir.resource :refer [wrap-binary-data wrap-resource]]
   [blaze.middleware.fhir.resource-spec]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [jsonista.core :as j]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [java.io ByteArrayInputStream]
   [java.nio.charset StandardCharsets]
   [java.util Base64]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(defn- wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))

(def ^:private resource-body-handler
  "A handler which just returns the :body from a non-binary resource request."
  (-> (comp ac/completed-future :body)
      wrap-resource
      wrap-error))

(def ^:private binary-resource-body-handler
  "A handler which just returns the :body from a binary resource request."
  (-> (comp ac/completed-future :body)
      wrap-binary-data
      wrap-error))

(defn- string-input-stream
  ([^String s]
   (ByteArrayInputStream. (.getBytes s StandardCharsets/UTF_8)))
  ([^String s closed?]
   (proxy [ByteArrayInputStream] [(.getBytes s StandardCharsets/UTF_8)]
     (close []
       (reset! closed? true)))))

(defn- binary-input-stream
  ([^bytes data]
   (ByteArrayInputStream. data))
  ([^bytes data closed?]
   (proxy [ByteArrayInputStream] [data]
     (close []
       (reset! closed? true)))))

(deftest json-test
  (testing "possible content types"
    (doseq [content-type ["application/fhir+json" "application/json" "text/json"]]
      (let [closed? (atom false)]
        (given @(resource-body-handler
                 {:headers {"content-type" content-type}
                  :body (string-input-stream "{\"resourceType\": \"Patient\"}" closed?)})
          fhir-spec/fhir-type := :fhir/Patient)
        (is (true? @closed?)))))

  (testing "empty body"
    (given @(resource-body-handler
             {:headers {"content-type" "application/fhir+json"}
              :body (string-input-stream "")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invalid"
      [:body :issue 0 :diagnostics] := "No content to map due to end-of-input\n at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1]"))

  (testing "body with invalid JSON"
    (given @(resource-body-handler
             {:headers {"content-type" "application/fhir+json"}
              :body (string-input-stream "x")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invalid"
      [:body :issue 0 :diagnostics] := "Unrecognized token 'x': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')\n at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 2]"))

  (testing "body with no JSON object"
    ;; There is no XML analogy to this JSON test, since XML has no objects.
    (given @(resource-body-handler
             {:headers {"content-type" "application/fhir+json"}
              :body (string-input-stream "1")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"structure"
      [:body :issue 0 :details :coding 0 :system] := #fhir/uri"http://terminology.hl7.org/CodeSystem/operation-outcome"
      [:body :issue 0 :details :coding 0 :code] := #fhir/code"MSG_JSON_OBJECT"
      [:body :issue 0 :diagnostics] := "Expect a JSON object."))

  (testing "body with invalid resource"
    (given @(resource-body-handler
             {:headers {"content-type" "application/fhir+json"}
              :body (string-input-stream "{\"resourceType\": \"Patient\", \"gender\": {}}")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invariant"
      [:body :issue 0 :diagnostics] := "Error on value `{}`. Expected type is `code`, regex `[\\u0021-\\uFFFF]+([ \\t\\n\\r][\\u0021-\\uFFFF]+)*`."
      [:body :issue 0 :expression] := ["gender"]))

  (testing "body with bundle with null resource"
    (given @(resource-body-handler
             {:headers {"content-type" "application/fhir+json"}
              :body (string-input-stream "{\"resourceType\": \"Bundle\", \"entry\": [{\"resource\": null}]}")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invariant"
      [:body :issue 0 :diagnostics] := "Error on value `null`. Expected type is `Resource`."
      [:body :issue 0 :expression] := ["entry[0].resource"]))

  (testing "body with bundle with invalid resource"
    (given @(resource-body-handler
             {:headers {"content-type" "application/fhir+json"}
              :body (string-input-stream "{\"resourceType\": \"Bundle\", \"entry\": [{\"resource\": {\"resourceType\": \"Patient\", \"gender\": {}}}]}")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invariant"
      [:body :issue 0 :diagnostics] := "Error on value `{}`. Expected type is `code`, regex `[\\u0021-\\uFFFF]+([ \\t\\n\\r][\\u0021-\\uFFFF]+)*`."
      [:body :issue 0 :expression] := ["entry[0].resource.gender"]))

  (testing "long attribute values are allowed (JSON-wrapped Binary data)"
    (given @(resource-body-handler
             {:headers {"content-type" "application/fhir+json"}
              :body (string-input-stream (str "{\"data\" : \"" (apply str (repeat (* 8 1024 1024) \a)) "\", \"resourceType\" : \"Binary\"}"))})
      fhir-spec/fhir-type := :fhir/Binary)))

(deftest xml-test
  (testing "possible content types"
    (doseq [content-type ["application/fhir+xml" "application/xml" "text/xml"]]
      (let [closed? (atom false)]
        (given @(resource-body-handler
                 {:headers {"content-type" content-type}
                  :body (string-input-stream "<Patient xmlns=\"http://hl7.org/fhir\"></Patient>" closed?)})
          fhir-spec/fhir-type := :fhir/Patient)
        (is (true? @closed?)))))

  (testing "empty body"
    (given @(resource-body-handler
             {:headers {"content-type" "application/fhir+xml"}
              :body (string-input-stream "")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invalid"
      [:body :issue 0 :diagnostics] := "Unexpected EOF in prolog\n at [row,col {unknown-source}]: [1,0]"))

  (testing "body with invalid XML"
    (doseq [[input-string diagnostics] [["1" "Unexpected character '1' (code 49) in prolog; expected '<'\n at [row,col {unknown-source}]: [1,1]"]
                                        ["<Patient xmlns=\"http://hl7.org/fhir\"><id value \"a_b\"/></Patient>" "Unexpected character '\"' (code 34) expected '='\n at [row,col {unknown-source}]: [1,48]"]]]
      (given @(resource-body-handler
               {:request-method :post
                :headers {"content-type" "application/fhir+xml"}
                :body (string-input-stream input-string)})
        :status := 400
        [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
        [:body :issue 0 :severity] := #fhir/code"error"
        [:body :issue 0 :code] := #fhir/code"invalid"
        [:body :issue 0 :diagnostics] := diagnostics)))

  (testing "body with invalid resource"
    (given @(resource-body-handler
             {:headers {"content-type" "application/fhir+xml"}
              :body (string-input-stream "<Patient xmlns=\"http://hl7.org/fhir\"><id value=\"a_b\"/></Patient>")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invariant"
      [:body :issue 0 :diagnostics] := "Error on value `a_b`. Expected type is `id`, regex `[A-Za-z0-9\\-\\.]{1,64}`."))

  (testing "body with bundle with empty resource"
    (given @(resource-body-handler
             {:headers {"content-type" "application/fhir+xml"}
              :body (string-input-stream "<Bundle xmlns=\"http://hl7.org/fhir\"><entry><resource></resource></entry></Bundle>")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invariant"
      [:body :issue 0 :diagnostics] := "Error on value `<:resource/>`. Expected type is `Resource`."))

  (testing "body with bundle with invalid resource"
    (given @(resource-body-handler
             {:headers {"content-type" "application/fhir+xml"}
              :body (string-input-stream "<Bundle xmlns=\"http://hl7.org/fhir\"><entry><resource><Patient xmlns=\"http://hl7.org/fhir\"><id value=\"a_b\"/></Patient></resource></entry></Bundle>")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invariant"
      [:body :issue 0 :diagnostics] := "Error on value `a_b`. Expected type is `id`, regex `[A-Za-z0-9\\-\\.]{1,64}`."))

  (testing "long attribute values are allowed (XML-wrapped Binary data)"
    (given @(resource-body-handler
             {:headers {"content-type" "application/fhir+xml"}
              :body (string-input-stream (str "<Binary xmlns=\"http://hl7.org/fhir\"><data value=\"" (apply str (repeat (* 8 1024 1024) \a)) "\"/></Binary>"))})
      fhir-spec/fhir-type := :fhir/Binary)))

(def ^:private whitespace
  (gen/fmap str/join (gen/vector (gen/elements [" " "\n" "\r" "\t"]))))

(deftest invalid-or-nil-test
  (testing "blank body without content type header results in a nil body"
    (satisfies-prop 10
      (prop/for-all [s whitespace]
        (nil? @(resource-body-handler {:body (string-input-stream s)})))))

  (testing "other content is invalid"
    (testing "with missing content-type header"
      (given @(resource-body-handler {:body (string-input-stream "foo")})
        :status := 400
        [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
        [:body :issue 0 :severity] := #fhir/code"error"
        [:body :issue 0 :code] := #fhir/code"invalid"
        [:body :issue 0 :diagnostics] := "Missing Content-Type header for FHIR resources."))

    (testing "with unknown content-type header"
      (given @(resource-body-handler {:headers {"content-type" "text/plain"} :body (string-input-stream "foo")})
        :status := 415
        [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
        [:body :issue 0 :severity] := #fhir/code"error"
        [:body :issue 0 :code] := #fhir/code"invalid"
        [:body :issue 0 :diagnostics] := "Unsupported media type `text/plain` expect one of `application/fhir+json` or `application/fhir+xml`."))

    (testing "missing body"
      (doseq [content-type ["application/fhir+json" "application/fhir+xml"]]
        (given @(resource-body-handler
                 {:headers {"content-type" content-type}})
          [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
          [:body :issue 0 :severity] := #fhir/code"error"
          [:body :issue 0 :code] := #fhir/code"invalid"
          [:body :issue 0 :diagnostics] := "Missing HTTP body.")))))

(defn- binary-resource-as-json [content-type data]
  (j/write-value-as-string
   {:resourceType "Binary"
    :contentType content-type
    :data data}))

(defn- binary-resource-as-xml [content-type data]
  (str "<Binary xmlns=\"http://hl7.org/fhir\"><contentType value=\"" content-type "\"/><data value=\"" data "\"/></Binary>"))

(defn- encode-binary-data [^bytes data]
  (.encodeToString (Base64/getEncoder) data))

(deftest binary-test
  (testing "FHIR-wrapped binary data"
    (doseq [[content-type resource]
            [["application/fhir+json" (binary-resource-as-json "text/plain" "MTA1NjE0Cg==")]
             ["application/fhir+xml" (binary-resource-as-xml "text/plain" "MTA1NjE0Cg==")]]]
      (let [closed? (atom false)]
        (given @(binary-resource-body-handler
                 {:headers {"content-type" content-type}
                  :body (string-input-stream resource closed?)})
          :fhir/type := :fhir/Binary
          [:contentType type/value] := "text/plain"
          [:data type/value] := "MTA1NjE0Cg==")
        (is (true? @closed?)))))

  (testing "raw binary data"
    (let [content-type "application/octet-stream"
          raw-data (byte-array [10 57 42 10 57 48 10 57 56])
          closed? (atom false)]
      (given @(binary-resource-body-handler
               {:headers {"content-type" content-type}
                :body (binary-input-stream raw-data closed?)})
        :fhir/type := :fhir/Binary
        [:contentType type/value] := content-type
        [:data type/value] := (encode-binary-data raw-data))
      (is (true? @closed?))))

  (testing "with missing content-type header"
    (given @(binary-resource-body-handler {:body (string-input-stream "foo")})
      :status := 400
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invalid"
      [:body :issue 0 :diagnostics] := "Missing Content-Type header for binary data."))

  (testing "missing body"
    (given @(binary-resource-body-handler
             {:headers {"content-type" "application/octet-stream"}})
      [:body fhir-spec/fhir-type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"invalid"
      [:body :issue 0 :diagnostics] := "Missing HTTP body.")))
