(ns build
  (:refer-clojure :exclude [compile])
  (:require
   [clojure.tools.build.api :as b]
   [hato.client :as hc])
  (:import
   [java.io FileOutputStream]))

(set! *warn-on-reflection* true)

(defn compile [_]
  (b/javac
   {:basis (b/create-basis {:project "deps.edn"})
    :src-dirs ["java"]
    :class-dir "target/classes"
    :javac-opts ["-Xlint:all" "-proc:none" "--release" "17"]}))

(defn download-file [url output-path]
  (let [http-client (hc/build-http-client {:redirect-policy :normal})
        response (hc/get url {:http-client http-client :as :byte-array})]
    (with-open [out (FileOutputStream. ^String output-path)]
      (.write out ^bytes (:body response)))))

(defn download-definitions [_]
  (download-file "https://hl7.org/fhir/6.0.0-ballot3/definitions.json.zip" "definitions.json.zip")
  (b/unzip {:zip-file "definitions.json.zip" :target-dir "resources/blaze/fhir"})
  (b/delete {:path "definitions.json.zip"})
  (b/delete {:path "resources/blaze/fhir/conceptmaps.json"})
  (b/delete {:path "resources/blaze/fhir/dataelements.json"})
  (b/delete {:path "resources/blaze/fhir/fhir.schema.json.zip"})
  (b/delete {:path "resources/blaze/fhir/profiles-others.json"})
  (b/delete {:path "resources/blaze/fhir/valuesets.json"})
  (b/delete {:path "resources/blaze/fhir/version.info"}))

(defn all [_]
  (compile nil)
  (download-definitions nil))
