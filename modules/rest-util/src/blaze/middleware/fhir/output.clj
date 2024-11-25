(ns blaze.middleware.fhir.output
  "FHIR Resource serialization middleware.

  Currently supported formats:
  * standard: JSON, XML;
  * special: binary."
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.handler.util :as handler-util]
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [muuntaja.parse :as parse]
   [prometheus.alpha :as prom]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]
   [jsonista.core :as j])
  (:import
   [java.io ByteArrayOutputStream]))

(set! *warn-on-reflection* true)

(prom/defhistogram generate-duration-seconds
  "FHIR generating latencies in seconds."
  {:namespace "fhir"}
  (take 17 (iterate #(* 2 %) 0.00001))
  "format")

(def ^:private parse-accept (parse/fast-memoize 1000 parse/parse-accept))

(defn- e->byte-array [e byte-array-fn]
  (-> e
      ba/anomaly
      handler-util/operation-outcome
      byte-array-fn))

(defn- generate-json** [body]
  (fhir-spec/unform-json body))


(comment
  (defn- parse-json [body]
    (fhir-spec/conform-json (fhir-spec/parse-json body)))


  (parse-json (generate-json** {:fhir/type :fhir/Patient :id "0"}))
;; => {:fhir/type :fhir/Patient, :id "0"}
  :end)

(defn- generate-json* [response]
  (try
    (update response :body generate-json**)
    (catch Throwable e
      (update response :body (constantly (generate-json** (handler-util/operation-outcome (ba/anomaly e))))))))


(comment
  (def invalid-body {:fhir/type :fhir/Patient :id "0" :gender #fhir/code"foo\u001Ebar"})
  (def valid-body   {:fhir/type :fhir/Patient :id "0"})

  (ring/response valid-body)
  ;; => {:status 200, :headers {}, :body {:fhir/type :fhir/Patient, :id "0"}}

  (def valid-resp (ring/response valid-body))

  (ring/response invalid-body)
  ;; => {:status 200, :headers {}, :body {:fhir/type :fhir/Patient, :id "0", :gender #fhir/code"foobar"}}

  (def invalid-resp (ring/response invalid-body))

  (generate-json* valid-resp)
;; => {:status 200, :headers {}, :body #object["[B" 0x72aa21ba "[B@72aa21ba"]}
  (-> valid-resp
      generate-json*
      parse-json)
;; => {:cognitect.anomalies/category :cognitect.anomalies/incorrect, :cognitect.anomalies/message "Invalid JSON representation of a resource.", :x #:cognitect.anomalies{:category :cognitect.anomalies/incorrect, :message "No implementation of method: :-read-value of protocol: #'jsonista.core/ReadValue found for class: clojure.lang.PersistentArrayMap"}, :fhir/issues [#:fhir.issues{:severity "error", :code "value", :diagnostics "Given resource does not contain a `resourceType` property."}]}

  (parse-json (j/write-value-as-string {:fhir/type :fhir/Patient :id "0"}))
;; => {:cognitect.anomalies/category :cognitect.anomalies/incorrect, :cognitect.anomalies/message "Invalid JSON representation of a resource.", :x {:fhir/type "fhir/Patient", :id "0"}, :fhir/issues [#:fhir.issues{:severity "error", :code "value", :diagnostics "Given resource does not contain a `resourceType` property."}]}

  :end)


(defn- generate-json [response]
  (log/trace "generate JSON")
  (with-open [_ (prom/timer generate-duration-seconds "json")]
    (generate-json* response)))

(defn- xml-byte-array [body]
  (let [out (ByteArrayOutputStream.)]
    (with-open [writer (io/writer out)]
      (xml/emit (fhir-spec/unform-xml body) writer))
    (.toByteArray out)))


(comment

  (defn- parse-xml [body]
    (with-open [reader (io/reader body)]
      (fhir-spec/conform-xml (xml/parse reader))))

  (parse-xml (xml-byte-array {:fhir/type :fhir/Patient :id "0"}))
  ;; => {:id "0", :fhir/type :fhir/Patient}

  :end)


(defn- generate-xml* [response]
  (try
    (update response :body xml-byte-array)
    (catch Throwable e
      (assoc response
             :body (e->byte-array e xml-byte-array)
             :status 500))))

(defn- generate-xml [response]
  (log/trace "generate XML")
  (with-open [_ (prom/timer generate-duration-seconds "xml")]
    (generate-xml* response)))

(defn- encode-response-json [{:keys [body] :as response} content-type]
  (cond-> response body (-> generate-json
                            (ring/content-type content-type))))

(defn- encode-response-xml [{:keys [body] :as response} content-type]
  (cond-> response body (-> generate-xml
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
