(ns blaze.rest-api.middleware.output
  "JSON/XML serialization middleware."
  (:require
    [blaze.fhir.spec :as fhir-spec]
    [clojure.data.xml :as xml]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [muuntaja.parse :as parse]
    [prometheus.alpha :as prom]
    [ring.util.response :as ring]
    [taoensso.timbre :as log])
  (:import
    [java.io ByteArrayOutputStream]))


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


(defn- generate-xml* [body]
  (let [out (ByteArrayOutputStream.)]
    (with-open [writer (io/writer out)]
      (xml/emit (fhir-spec/unform-xml body) writer))
    (.toByteArray out)))


(defn- generate-xml [body]
  (log/trace "generate XML")
  (with-open [_ (prom/timer generate-duration-seconds "xml")]
    (generate-xml* body)))


(defn- encode-response-json [response]
  (-> (update response :body generate-json)
      (ring/content-type "application/fhir+json;charset=utf-8")))


(defn- encode-response-xml [response]
  (-> (update response :body generate-xml)
      (ring/content-type "application/fhir+xml;charset=utf-8")))


(defn- json-format? [format]
  (or (str/includes? format "json") (#{"*/*" "application/*" "text/*"} format)))


(defn- format-key [format]
  (cond
    (json-format? format) :json
    (str/includes? format "xml") :xml))


(defn- request-format
  [{{:strs [accept]} :headers {format "_format"} :query-params}]
  (or (some-> format format-key)
      (if-let [first-accept (first (parse-accept accept))]
        (format-key first-accept)
        :json)))


(defn- encode-response [opts request response]
  (case (request-format request)
    :json (encode-response-json response)
    :xml (encode-response-xml response)
    (when (:accept-all? opts) (dissoc response :body))))


(defn- handle-response [opts request {:keys [body] :as response}]
  (cond->> response body (encode-response opts request)))


(defn wrap-output
  "Middleware to output resources in JSON or XML."
  ([handler]
   (wrap-output handler {}))
  ([handler opts]
   (fn [request respond raise]
     (handler request #(respond (handle-response opts request %)) raise))))
