(ns blaze.middleware.fhir.resource
  "FHIR Resource deserialization middleware.

  Currently supported formats:
  * standard: JSON, XML."
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.type :as type]
   [clojure.data.xml.jvm.parse :as xml-jvm]
   [clojure.data.xml.tree :as xml-tree]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [cognitect.anomalies :as anom]
   [prometheus.alpha :as prom]
   [ring.util.request :as request])
  (:import
   [com.ctc.wstx.api WstxInputProperties]
   [java.io InputStream Reader]
   [java.util Base64]
   [javax.xml.stream XMLInputFactory]))

(set! *warn-on-reflection* true)

(prom/defhistogram parse-duration-seconds
  "FHIR parsing latencies in seconds."
  {:namespace "fhir"}
  (take 17 (iterate #(* 2 %) 0.00001))
  "format")

(defn- json-request? [content-type]
  (or (str/starts-with? content-type "application/fhir+json")
      (str/starts-with? content-type "application/json")
      (str/starts-with? content-type "text/json")))

(defn- parse-json
  "Takes a request `body` and returns the parsed JSON content in internal format.

  Returns an anomaly on parse errors."
  [parsing-context type body]
  (with-open [_ (prom/timer parse-duration-seconds "json")]
    (fhir-spec/parse-json parsing-context type body)))

(defn- resource-request-json
  [parsing-context type {:keys [body] :as request}]
  (if body
    (when-ok [resource (parse-json parsing-context type body)]
      (assoc request :body resource))
    (ba/incorrect "Missing HTTP body.")))

(defn- xml-request? [content-type]
  (or (str/starts-with? content-type "application/fhir+xml")
      (str/starts-with? content-type "application/xml")
      (str/starts-with? content-type "text/xml")))

(defn- create-xml-factory []
  (doto (XMLInputFactory/newInstance)
    (.setProperty XMLInputFactory/IS_COALESCING true)
    (.setProperty XMLInputFactory/IS_NAMESPACE_AWARE true)
    (.setProperty XMLInputFactory/IS_REPLACING_ENTITY_REFERENCES true)
    (.setProperty XMLInputFactory/IS_SUPPORTING_EXTERNAL_ENTITIES false)
    (.setProperty XMLInputFactory/IS_VALIDATING false)
    (.setProperty XMLInputFactory/SUPPORT_DTD false)
    (.setProperty WstxInputProperties/P_MAX_ATTRIBUTE_SIZE Integer/MAX_VALUE)))

(defn- parse-xml [reader]
  (let [factory (create-xml-factory)]
    (xml-tree/event-tree
     (xml-jvm/pull-seq
      (.createXMLStreamReader ^XMLInputFactory factory ^Reader reader)
      {:include-node? #{:element :characters}
       :location-info true}
      nil))))

(defn- parse-and-conform-xml
  "Takes a request `body` and returns the parsed and conformed XML content.

  Throws an anomaly on parse or conforming errors."
  [body]
  (with-open [_ (prom/timer parse-duration-seconds "xml")
              reader (io/reader body)]
    ;; It is important to conform inside this function, because the XML parser
    ;; is lazy streaming. Otherwise, errors will be thrown outside this function.
    (ba/try-all ::anom/incorrect (fhir-spec/conform-xml (parse-xml reader)))))

(defn- resource-request-xml [{:keys [body] :as request}]
  (if body
    (when-ok [resource (parse-and-conform-xml body)]
      (assoc request :body resource))
    (ba/incorrect "Missing HTTP body.")))

(defn- unsupported-media-type-msg [media-type]
  (format "Unsupported media type `%s` expect one of `application/fhir+json` or `application/fhir+xml`."
          media-type))

(defn- unsupported-media-type-anom [content-type]
  (ba/incorrect (unsupported-media-type-msg content-type) :http/status 415))

(defn no-content-type-resource-request [request]
  (if (str/blank? (slurp (:body request)))
    (assoc request :body nil)
    (ba/incorrect "Missing Content-Type header for FHIR resources.")))

(defn- resource-request [parsing-context type request]
  (if-let [content-type (request/content-type request)]
    (cond
      (json-request? content-type) (resource-request-json parsing-context type request)
      (xml-request? content-type) (resource-request-xml request)
      :else (unsupported-media-type-anom content-type))
    (no-content-type-resource-request request)))

(defn wrap-resource
  "Middleware to parse a resource from the body according to the content-type
  header.

  If the resource is successfully parsed and conformed to the internal format,
  updates the :body key in the request map.

  In case on errors, returns an OperationOutcome in the internal format with the
  appropriate error and skips the handler."
  [handler parsing-context type]
  (fn [request]
    (if-ok [request (resource-request parsing-context type request)]
      (handler request)
      ac/completed-future)))

(defn- read-all-bytes-and-close [^InputStream in]
  (try
    (.readAllBytes in)
    (finally
      (.close in))))

(defn- encode-binary-data [body]
  (with-open [_ (prom/timer parse-duration-seconds "binary")]
    (.encodeToString (Base64/getEncoder) (read-all-bytes-and-close body))))

(defn- resource-request-binary-data [content-type {:keys [body] :as request}]
  (if body
    (assoc request :body
           {:fhir/type :fhir/Binary
            :contentType (type/code content-type)
            :data (type/base64Binary (encode-binary-data body))})
    (ba/incorrect "Missing HTTP body.")))

(defn- binary-resource-request [parsing-context request]
  (if-let [content-type (request/content-type request)]
    (cond
      (str/starts-with? content-type "application/fhir+json")
      (resource-request-json parsing-context "Binary" request)

      (str/starts-with? content-type "application/fhir+xml")
      (resource-request-xml request)

      :else
      (resource-request-binary-data content-type request))
    (ba/incorrect "Missing Content-Type header for binary data.")))

(defn wrap-binary-data
  "Middleware to parse binary data from the body according to the content-type
  header.

  If the resource is successfully parsed and conformed to the internal format,
  updates the :body key in the request map.

  In case on errors, returns an OperationOutcome in the internal format with the
  appropriate error and skips the handler."
  [handler parsing-context]
  (fn [request]
    (if-ok [request (binary-resource-request parsing-context request)]
      (handler request)
      ac/completed-future)))
