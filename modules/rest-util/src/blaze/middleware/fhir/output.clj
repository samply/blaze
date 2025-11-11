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
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [muuntaja.parse :as parse]
   [prometheus.alpha :as prom]
   [ring.core.protocols :as rp]
   [ring.util.response :as ring]
   [taoensso.timbre :as log])
  (:import
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

(defn- generate-json [writing-context body]
  (reify rp/StreamableResponseBody
    (write-body-to-stream [_body _response output-stream]
      (log/trace "generate JSON")
      (with-open [_ (prom/timer generate-duration-seconds "json")]
        (fhir-spec/write-json writing-context output-stream body)))))

(defn- generate-xml [body]
  (reify rp/StreamableResponseBody
    (write-body-to-stream [_body _response output-stream]
      (log/trace "generate XML")
      (with-open [_ (prom/timer generate-duration-seconds "xml")]
        (with-open [writer (io/writer output-stream)]
          (xml/emit (fhir-spec/unform-xml body) writer))))))

(defn- generate-binary** [{:keys [data]}]
  (when data
    (.decode (Base64/getDecoder) ^String (type/value data))))

(defn- binary-content-type [body]
  (or (-> body :contentType type/value)
      "application/octet-stream"))

(defn- generate-binary* [writing-context {:keys [body] :as response}]
  (try
    (-> (update response :body generate-binary**)
        (ring/content-type (binary-content-type body)))
    (catch Throwable e
      (-> (ring/response (generate-error-payload (partial generate-json writing-context) e))
          (ring/status 500)
          (ring/content-type "application/fhir+json")))))

(defn- generate-binary [writing-context response]
  (log/trace "generate binary")
  (with-open [_ (prom/timer generate-duration-seconds "binary")]
    (generate-binary* writing-context response)))

(defn- encode-response-json [writing-context {:keys [body] :as response} content-type]
  (cond-> response body (-> (update :body (partial generate-json writing-context))
                            (ring/content-type content-type))))

(defn- encode-response-xml [{:keys [body] :as response} content-type]
  (cond-> response body (-> (update :body generate-xml)
                            (ring/content-type content-type))))

(defn- encode-response-binary [writing-context {:keys [body] :as response}]
  (cond->> response body (generate-binary writing-context)))

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

(defn handle-response [writing-context opts request response]
  (case (request-format request)
    :fhir+json (encode-response-json writing-context response "application/fhir+json;charset=utf-8")
    :fhir+xml (encode-response-xml response "application/fhir+xml;charset=utf-8")
    :json (encode-response-json writing-context response "application/json;charset=utf-8")
    :xml (encode-response-xml response "application/xml;charset=utf-8")
    :text-json (encode-response-json writing-context response "text/json;charset=utf-8")
    :text-xml (encode-response-xml response "text/xml;charset=utf-8")
    (when (:accept-all? opts) (dissoc response :body))))

(defn wrap-output
  "Middleware to output resources in JSON or XML."
  ([handler writing-context]
   (wrap-output handler writing-context {}))
  ([handler writing-context opts]
   (fn [request respond raise]
     (handler request #(respond (handle-response writing-context opts request %)) raise))))

(defn handle-binary-response [writing-context request response]
  (case (request-format request)
    :fhir+json (encode-response-json writing-context response "application/fhir+json;charset=utf-8")
    :fhir+xml (encode-response-xml response "application/fhir+xml;charset=utf-8")
    (encode-response-binary writing-context response)))

(defn wrap-binary-output
  "Middleware to output binary resources."
  [handler writing-context]
  (fn [request respond raise]
    (handler request #(respond (handle-binary-response writing-context request %)) raise)))
