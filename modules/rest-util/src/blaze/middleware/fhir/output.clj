(ns blaze.middleware.fhir.output
  "FHIR Resource serialization middleware.

  Currently supported formats:
  * standard: JSON, XML;
  * special: binary."
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.util :as handler-util]
   [cheshire.core :as json]
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [muuntaja.parse :as parse]
   [prometheus.alpha :as prom]
   [ring.util.response :as ring]
   [taoensso.timbre :as log])
  (:import
   [java.io ByteArrayOutputStream]
   [java.util Base64]))

(set! *warn-on-reflection* true)

(prom/defhistogram generate-duration-seconds
  "FHIR generating latencies in seconds."
  {:namespace "fhir"}
  (take 17 (iterate #(* 2 %) 0.00001))
  "format")

(def ^:private parse-accept (parse/fast-memoize 1000 parse/parse-accept))

(defn- generate-error-payload [generation-fn e]
  (-> e
      ba/anomaly
      handler-util/operation-outcome
      generation-fn))

(defn- generate-json** [body]
  (let [out (ByteArrayOutputStream.)]
    (with-open [writer (io/writer out)]
      (json/generate-stream (fhir-spec/unform-json body) writer))
    (.toByteArray out)))

(defn- generate-json* [response]
  (try
    (update response :body generate-json**)
    (catch Throwable e
      (assoc response
             :body (generate-error-payload generate-json** e)
             :status 500))))

(defn- generate-json [response]
  (log/trace "generate JSON")
  (with-open [_ (prom/timer generate-duration-seconds "json")]
    (generate-json* response)))

(defn- generate-xml** [body]
  (let [out (ByteArrayOutputStream.)]
    (with-open [writer (io/writer out)]
      (xml/emit (fhir-spec/unform-xml body) writer))
    (.toByteArray out)))

(defn- generate-xml* [response]
  (try
    (update response :body generate-xml**)
    (catch Throwable e
      (assoc response
             :body (generate-error-payload generate-xml** e)
             :status 500))))

(defn- generate-xml [response]
  (log/trace "generate XML")
  (with-open [_ (prom/timer generate-duration-seconds "xml")]
    (generate-xml* response)))

(defn- generate-binary** [{:keys [data]}]
  (when data
    (.decode (Base64/getDecoder) ^String (type/value data))))

(defn- generate-binary* [response]
  (try
    (update response :body generate-binary**)
    (catch Throwable e
      (assoc response
             :body (generate-error-payload generate-json** e)
             :status 500))))

(defn- generate-binary [response]
  (log/trace "generate binary")
  (with-open [_ (prom/timer generate-duration-seconds "binary")]
    (generate-binary* response)))

(defn- encode-response-json [{:keys [body] :as response} content-type]
  (cond-> response body (-> generate-json
                            (ring/content-type content-type))))

(defn- encode-response-xml [{:keys [body] :as response} content-type]
  (cond-> response body (-> generate-xml
                            (ring/content-type content-type))))

(defn- binary-content-type [body]
  (or (-> body :contentType type/value)
      "application/octet-stream"))

(defn- encode-response-binary [{:keys [body] :as response}]
  (cond-> response body (-> generate-binary
                            (ring/content-type (binary-content-type body)))))

(defn- format-key [format]
  (condp = format
    "application/fhir+json" :fhir+json
    "application/fhir+xml" :fhir+xml
    "application/json" :json
    "application/xml" :xml
    "text/json" :text-json
    "text/xml" :text-xml
    "*/*" :fhir+json
    "application/*" :fhir+json
    "text/*" :text-json
    "json" :fhir+json
    "xml" :fhir+xml
    nil))

(defn- request-format
  [{{:strs [accept]} :headers {format "_format"} :query-params}]
  (or (some-> format format-key)
      (if-let [accept (parse-accept accept)]
        (some format-key accept)
        :fhir+json)))

(defn handle-response [opts request response]
  (case (request-format request)
    :fhir+json (encode-response-json response "application/fhir+json;charset=utf-8")
    :fhir+xml (encode-response-xml response "application/fhir+xml;charset=utf-8")
    :json (encode-response-json response "application/json;charset=utf-8")
    :xml (encode-response-xml response "application/xml;charset=utf-8")
    :text-json (encode-response-json response "text/json;charset=utf-8")
    :text-xml (encode-response-xml response "text/xml;charset=utf-8")
    (when (:accept-all? opts) (dissoc response :body))))

(defn wrap-output
  "Middleware to output resources in JSON or XML."
  ([handler]
   (wrap-output handler {}))
  ([handler opts]
   (fn [request respond raise]
     (handler request #(respond (handle-response opts request %)) raise))))

(defn handle-binary-response [request response]
  (case (request-format request)
    :fhir+json (encode-response-json response "application/fhir+json;charset=utf-8")
    :fhir+xml (encode-response-xml response "application/fhir+xml;charset=utf-8")
    (encode-response-binary response)))

(defn wrap-binary-output
  "Middleware to output binary resources."
  [handler]
  (fn [request respond raise]
    (handler request #(respond (handle-binary-response request %)) raise)))
