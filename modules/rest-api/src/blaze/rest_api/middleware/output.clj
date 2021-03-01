(ns blaze.rest-api.middleware.output
  "JSON/XML serialization middleware."
  (:require
    [blaze.async.comp :as ac]
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


(defn handle-response [request {:keys [body] :as response}]
  (if body
    (if (xml-response? request)
      (-> (update response :body generate-xml)
          (ring/content-type "application/fhir+xml;charset=utf-8"))
      (-> (update response :body generate-json)
          (ring/content-type "application/fhir+json;charset=utf-8")))
    response))


(defn wrap-output
  "Middleware to output resources in JSON or XML."
  [handler]
  (fn [request]
    (-> (handler request)
        (ac/then-apply #(handle-response request %)))))


(comment
  (def json "{\"category\":[{\"coding\":[{\"code\":\"vital-signs\",\"display\":\"vital-signs\",\"system\":\"http://terminology.hl7.org/CodeSystem/observation-category\"}]}],\"code\":{\"coding\":[{\"code\":\"39156-5\",\"display\":\"Body Mass Index\",\"system\":\"http://loinc.org\"}],\"text\":\"Body Mass Index\"},\"effectiveDateTime\":\"2011-02-23T09:13:01+01:00\",\"encounter\":{\"reference\":\"Encounter/C53O522JS4MRTJV6\"},\"id\":\"C53O522JS4MRTJWC\",\"issued\":\"2011-02-23T09:13:01.071+01:00\",\"meta\":{\"lastUpdated\":\"2021-02-04T21:24:33.605Z\",\"versionId\":\"1456\"},\"resourceType\":\"Observation\",\"status\":\"final\",\"subject\":{\"reference\":\"Patient/C53O522JS4MRTJVQ\"},\"valueQuantity\":{\"code\":\"kg/m2\",\"system\":\"http://unitsofmeasure.org\",\"unit\":\"kg/m2\",\"value\":16.065926382869083}}")

  (def resource (fhir-spec/conform-json (fhir-spec/parse-json json)))

  (= (count (String. (generate-json resource)))
     (count json))

  ;; cheshire: 310 ms
  ;; jsonista: 110 ms
  (time (dotimes [_ 10000] (generate-json resource)))
  )
