(ns blaze.rest-api.middleware.output
  "JSON/XML serialization middleware."
  (:require
    [blaze.async.comp :as ac :refer [do-sync]]
    [blaze.fhir.spec :as fhir-spec]
    [clojure.data.xml :as xml]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [prometheus.alpha :as prom]
    [ring.util.response :as ring])
  (:import
    [java.io ByteArrayOutputStream]))


(prom/defhistogram generate-duration-seconds
  "FHIR generating latencies in seconds."
  {:namespace "fhir"}
  (take 17 (iterate #(* 2 %) 0.00001))
  "format")


(defn- generate-json [body]
  (with-open [_ (prom/timer generate-duration-seconds "json")]
    (fhir-spec/unform-json body)))


(defn- xml-response?
  [{{:strs [accept]} :headers {format "_format"} :query-params}]
  (let [accept (or format accept)]
    (or (and accept
             (or (str/starts-with? accept "application/fhir+xml")
                 (str/starts-with? accept "application/xml")
                 (str/starts-with? accept "text/xml")))
        (= "xml" format))))


(defn- generate-xml [body]
  (let [out (ByteArrayOutputStream.)]
    (with-open [_ (prom/timer generate-duration-seconds "xml")
                writer (io/writer out)]
      (xml/emit (fhir-spec/unform-xml body) writer))
    (.toByteArray out)))


(defn- encode-response-xml [response]
  (-> (update response :body generate-xml)
      (ring/content-type "application/fhir+xml;charset=utf-8")))


(defn- encode-response-json [response]
  (-> (update response :body generate-json)
      (ring/content-type "application/fhir+json;charset=utf-8")))


(defn- encode-response [request response]
  (if (xml-response? request)
    (encode-response-xml response)
    (encode-response-json response)))


(defn- handle-response [request {:keys [body] :as response}]
  (cond->> response body (encode-response request)))


(defn wrap-output
  "Middleware to output resources in JSON or XML."
  [handler]
  (fn [request]
    (do-sync [response (handler request)]
      (handle-response request response))))
