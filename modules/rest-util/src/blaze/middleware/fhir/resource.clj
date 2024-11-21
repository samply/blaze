(ns blaze.middleware.fhir.resource
  "FHIR Resource deserialization middleware.

  Currently supported formats:
  * standard: JSON, XML."
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac]
   [blaze.fhir.spec :as fhir-spec]
   [clojure.data.xml.jvm.parse :as xml-jvm]
   [clojure.data.xml.tree :as xml-tree]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [cognitect.anomalies :as anom]
   [prometheus.alpha :as prom]
   [ring.util.request :as request])
  (:import
   [com.ctc.wstx.api WstxInputProperties]
   [java.io Reader]
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
  "Takes a request `body` and returns the parsed JSON content.

  Returns an anomaly on parse errors."
  [body]
  (with-open [_ (prom/timer parse-duration-seconds "json")]
    (fhir-spec/parse-json body)))

(defn- conform-json [json]
  (if (map? json)
    (fhir-spec/conform-json json)
    (ba/incorrect
     "Expect a JSON object."
     :fhir/issue "structure"
     :fhir/operation-outcome "MSG_JSON_OBJECT")))

(defn- resource-request-json [{:keys [body] :as request}]
  (if body
    (when-ok [x (parse-json body)
              resource (conform-json x)]
      (assoc request :body resource))
    (ba/incorrect "Missing HTTP body.")))

(defn- xml-request? [content-type]
  (or (str/starts-with? content-type "application/fhir+xml")
      (str/starts-with? content-type "application/xml")))

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

(defn- resource-request [request]
  (if-let [content-type (request/content-type request)]
    (cond
      (json-request? content-type) (resource-request-json request)
      (xml-request? content-type) (resource-request-xml request)
      :else
      (ba/incorrect (unsupported-media-type-msg content-type)
                    :http/status 415))
    (if (str/blank? (slurp (:body request)))
      (assoc request :body nil)
      (ba/incorrect "Content-Type header expected, but is missing."))))

(defn wrap-resource
  "Middleware to parse a resource from the body according the content-type
  header.

  Updates the :body key in the request map on successful parsing and conforming
  the resource to the internal format.

  Returns an OperationOutcome in the internal format, skipping the handler, with
  an appropriate error on parsing and conforming errors."
  [handler]
  (fn [request]
    (if-ok [request (resource-request request)]
      (handler request)
      ac/completed-future)))
