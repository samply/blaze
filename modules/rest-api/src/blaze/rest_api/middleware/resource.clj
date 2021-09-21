(ns blaze.rest-api.middleware.resource
  "JSON/XML deserialization middleware."
  (:require
    [blaze.anomaly :as ba :refer [when-ok]]
    [blaze.anomaly-spec]
    [blaze.async.comp :as ac]
    [blaze.fhir.spec :as fhir-spec]
    [clojure.data.xml :as xml]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [prometheus.alpha :as prom]
    [ring.util.request :as request]))


(prom/defhistogram parse-duration-seconds
  "FHIR parsing latencies in seconds."
  {:namespace "fhir"}
  (take 17 (iterate #(* 2 %) 0.00001))
  "format")


(defn- json-request? [request]
  (when-let [content-type (request/content-type request)]
    (or (str/starts-with? content-type "application/fhir+json")
        (str/starts-with? content-type "application/json")
        (str/starts-with? content-type "text/json"))))


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


(defn- handle-json [{:keys [body] :as request}]
  (when-ok [x (parse-json body)
            resource (conform-json x)]
    (assoc request :body resource)))


(defn- xml-request? [request]
  (when-let [content-type (request/content-type request)]
    (or (str/starts-with? content-type "application/fhir+xml")
        (str/starts-with? content-type "application/xml"))))


(defn- parse-and-conform-xml
  "Takes a request `body` and returns the parsed and conformed XML content.

  Throws an anomaly on parse or conforming errors."
  [body]
  (with-open [_ (prom/timer parse-duration-seconds "xml")
              reader (io/reader body)]
    ;; It is important to conform inside this function, because the XML parser
    ;; is lazy streaming. Otherwise, errors will be thrown outside this function.
    (ba/try-all ::anom/incorrect (fhir-spec/conform-xml (xml/parse reader)))))


(defn- handle-xml [{:keys [body] :as request}]
  (when-ok [resource (parse-and-conform-xml body)]
    (assoc request :body resource)))


(defn- unknown-content-type-msg [request]
  (if-let [content-type (request/content-type request)]
    (if (:body request)
      (format "Unknown Content-Type `%s` expected one of application/fhir+json` or `application/fhir+xml`."
              content-type)
      "Missing HTTP body.")
    "Content-Type header expected, but is missing. Please specify one of application/fhir+json` or `application/fhir+xml`."))


(defn- handle-request [{:keys [body] :as request} executor]
  (cond
    (and (json-request? request) body)
    (ac/supply-async #(handle-json request) executor)
    (and (xml-request? request) body)
    (ac/supply-async #(handle-xml request) executor)
    :else (ac/completed-future (ba/incorrect (unknown-content-type-msg request)))))


(defn wrap-resource
  "Middleware to parse a resource from the body according the content-type
  header.

  Updates the :body key in the request map on successful parsing and conforming
  the resource to the internal format.

  Returns an OperationOutcome in the internal format, skipping the handler, with
  an appropriate error on parsing and conforming errors."
  [handler executor]
  (fn [request]
    (-> (handle-request request executor)
        (ac/then-compose handler))))
