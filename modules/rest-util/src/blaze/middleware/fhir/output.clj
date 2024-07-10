(ns blaze.middleware.fhir.output
  "JSON/XML serialization middleware."
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.handler.util :as handler-util]
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [muuntaja.parse :as parse]
   [prometheus.alpha :as prom]
   [ring.util.response :as ring]
   [taoensso.timbre :as log])
  (:import
   [java.io ByteArrayOutputStream]))

(set! *warn-on-reflection* true)

(prom/defhistogram generate-duration-seconds
  "FHIR generating latencies in seconds."
  {:namespace "fhir"}
  (take 17 (iterate #(* 2 %) 0.00001))
  "format")

(def ^:private parse-accept (parse/fast-memoize 1000 parse/parse-accept))

(defn- generate-json [body]
  (log/trace "generate JSON")
  (with-open [_ (prom/timer generate-duration-seconds "json")]
    (fhir-spec/unform-json body)))

(defn- generate-xml** [body]
  (let [out (ByteArrayOutputStream.)]
    (with-open [writer (io/writer out)]
      (xml/emit (fhir-spec/unform-xml body) writer))
    (.toByteArray out)))

(defn- generate-xml* [body]
  (try
    (generate-xml** body)
    (catch Throwable e
      (generate-xml** (handler-util/operation-outcome (ba/anomaly e))))))

(defn- generate-xml [body]
  (log/trace "generate XML")
  (with-open [_ (prom/timer generate-duration-seconds "xml")]
    (generate-xml* body)))

(defn- encode-response-json [{:keys [body] :as response} content-type]
  (cond-> response body (-> (update :body generate-json)
                            (ring/content-type content-type))))

(defn- encode-response-xml [{:keys [body] :as response} content-type]
  (cond-> response body (-> (update :body generate-xml)
                            (ring/content-type content-type))))

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
