(ns blaze.test-util.ring
  (:require
    [blaze.async.comp :as ac]
    [blaze.fhir.spec :as fhir-spec]
    [clojure.data.xml :as xml]
    [clojure.java.io :as io]
    [ring.core.protocols :as p])
  (:import
    [java.io ByteArrayOutputStream]))


(set! *warn-on-reflection* true)


(defn call
  "Calls async Ring `handler` with `request`, blocking on the response."
  [handler request]
  (let [future (ac/future)
        respond (partial ac/complete! future)
        raise (partial ac/complete-exceptionally! future)]
    (handler request respond raise)
    @future))


(defn parse-json [body]
  (let [out (ByteArrayOutputStream.)]
    (p/write-body-to-stream body nil out)
    (fhir-spec/parse-json (.toByteArray out))))


(defn parse-xml [body]
  (let [out (ByteArrayOutputStream.)]
    (p/write-body-to-stream body nil out)
    (with-open [reader (io/reader (.toByteArray out))]
      (fhir-spec/conform-xml (xml/parse reader)))))
