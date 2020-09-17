(ns blaze.rest-api.middleware.resource
  "JSON/XML deserialization middleware."
  (:require
    [blaze.anomaly :refer [throw-anom ex-anom]]
    [blaze.async-comp :as ac]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.handler.util :as handler-util]
    [cheshire.core :as json]
    [cheshire.parse :refer [*use-bigdecimals?*]]
    [clojure.data.xml :as xml]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
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
  "Takes a request `body` and returns the parsed JSON content with keyword keys
  and BigDecimal numbers.

  Throws an anomaly on parse errors."
  [body]
  (with-open [_ (prom/timer parse-duration-seconds "json")
              reader (io/reader body)]
    (binding [*use-bigdecimals?* true]
      (try
        (json/parse-stream reader keyword)
        (catch Exception e
          (throw-anom ::anom/incorrect (ex-message e)))))))


(defn- conform-json [json]
  (if (map? json)
    (let [resource (fhir-spec/conform-json json)]
      (if (s/invalid? resource)
        (throw-anom
          ::anom/incorrect
          "Resource invalid."
          :fhir/issues (:fhir/issues (fhir-spec/explain-data-json json)))
        resource))
    (throw-anom
      ::anom/incorrect
      "Expect a JSON object."
      :fhir/issue "structure"
      :fhir/operation-outcome "MSG_JSON_OBJECT")))


(defn- handle-json [request]
  (update request :body (comp conform-json parse-json)))


(defn- xml-request? [request]
  (when-let [content-type (request/content-type request)]
    (or (str/starts-with? content-type "application/fhir+xml")
        (str/starts-with? content-type "application/xml"))))


(defn- conform-xml [element]
  (let [resource (fhir-spec/conform-xml element)]
    (if (s/invalid? resource)
      {::anom/category ::anom/incorrect
       ::anom/message "Resource invalid."
       :fhir/issues (:fhir/issues (fhir-spec/explain-data-xml element))}
      resource)))


(defn- parse-and-conform-xml
  "Takes a request `body` and returns the parsed and conformed XML content.

  Throws an anomaly on parse or conforming errors."
  [body]
  (with-open [_ (prom/timer parse-duration-seconds "xml")
              reader (io/reader body)]
    (try
      ;; It is important to conform inside this function, because the XML parser
      ;; is lazy streaming. Otherwise errors will be thrown outside this function.
      (conform-xml (xml/parse reader))
      (catch Exception e
        (throw-anom ::anom/incorrect (or (ex-message e) (str e)))))))


(defn- handle-xml [{:keys [body] :as request}]
  (let [resource (parse-and-conform-xml body)]
    (if (::anom/category resource)
      (throw (ex-anom resource))
      (assoc request :body resource))))


(defn- handle-request [{:keys [body] :as request} executor]
  (cond
    (and (json-request? request) body)
    (ac/supply-async #(handle-json request) executor)
    (and (xml-request? request) body)
    (ac/supply-async #(handle-xml request) executor)
    :else (ac/completed-future request)))


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
        (ac/then-compose handler)
        (ac/exceptionally handler-util/error-response))))
