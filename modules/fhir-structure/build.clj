(ns build
  (:refer-clojure :exclude [compile])
  (:require
   [clojure.java.io :as io]
   [clojure.tools.build.api :as b]
   [hato.client :as hc]
   [jsonista.core :as j])
  (:import
   [java.io FileOutputStream]
   [java.security MessageDigest]))

(set! *warn-on-reflection* true)

(defn compile [_]
  (b/javac
   {:basis (b/create-basis {:project "deps.edn"})
    :src-dirs ["java"]
    :class-dir "target/classes"
    :javac-opts ["-Xlint:all" "-proc:none" "--release" "21"]}))

(defn download-file [url output-path]
  (println "Download:" output-path)
  (let [http-client (hc/build-http-client {:redirect-policy :normal})
        response (hc/get url {:http-client http-client :as :byte-array})]
    (with-open [out (FileOutputStream. ^String output-path)]
      (.write out ^bytes (:body response)))))

(defn- bytes->hex
  "Converts a byte array to a hexadecimal string."
  [^bytes byte-array]
  (apply str (map #(format "%02x" %) byte-array)))

(defn- file-sha256
  "Computes the SHA-256 hash of a file efficiently using a buffer."
  [file-path]
  (let [digest (MessageDigest/getInstance "SHA-256")
        buffer (byte-array 8192)]
    (with-open [is (io/input-stream file-path)]
      (loop []
        (let [bytes-read (.read is buffer)]
          (when (pos? bytes-read)
            (.update digest buffer 0 bytes-read)
            (recur)))))
    (bytes->hex (.digest digest))))

(defn verify-download
  "Verifies that the downloaded file matches the expected SHA."
  [file-path expected-sha]
  (let [actual-sha (file-sha256 file-path)]
    (if (= expected-sha actual-sha)
      (println "SHA verification passed for:" file-path)
      (throw (ex-info "SHA mismatch!"
                      {:file file-path
                       :expected expected-sha
                       :actual actual-sha})))))

(def metadata
  {"4.0.1"
   {:token "KQGqr6Wz29xHK9W"
    :sha "ecd74b1d57d86869992b4171e490d1a3fcebb8877110756b73f7be0f7159f534"}})

(defn download-definitions [{:keys [version]}]
  (let [filename (format "fhir-definitions-%s.zip" version)]
    (when-not (.exists (io/file filename))
      (download-file (format "https://speicherwolke.uni-leipzig.de/index.php/s/%s/download/%s" (get-in metadata [version :token]) filename) filename))
    (verify-download filename (get-in metadata [version :sha]))
    (b/unzip {:zip-file filename :target-dir (str "target/generated-resources/blaze/fhir/" version)})
    (b/delete {:path filename})))

(def single-definitions
  "Resources to extract from the base definition bundles into files of their
  own, by bundle filename.

  The CodeSystems and ValueSets back the required bindings of the Task
  elements."
  {"profiles-resources.json"
   #{["StructureDefinition" "Task"]}
   "valuesets.json"
   #{["CodeSystem" "task-status"]
     ["ValueSet" "task-status"]
     ["CodeSystem" "task-intent"]
     ["ValueSet" "task-intent"]
     ["CodeSystem" "request-intent"]
     ["ValueSet" "request-intent"]
     ["CodeSystem" "request-priority"]
     ["ValueSet" "request-priority"]
     ["CodeSystem" "identifier-use"]
     ["ValueSet" "identifier-use"]}})

(defn extract-single-definitions
  "Extracts the resources listed in `single-definitions` from the base
  definition bundles into files of their own, named `<type>-<id>.json`.

  This way consumers like the admin-api validator only have to parse these
  small files at startup instead of whole multi-MB bundles, which would take
  seconds and allocate several hundred MB of garbage."
  [{:keys [version]}]
  (let [dir (str "target/generated-resources/blaze/fhir/" version)]
    (doseq [[bundle-name wanted] single-definitions]
      (let [{:keys [entry]} (j/read-value (io/file dir bundle-name) j/keyword-keys-object-mapper)]
        (run!
         (fn [{{:keys [resourceType id] :as resource} :resource}]
           (when (wanted [resourceType id])
             (j/write-value (io/file dir (str resourceType "-" id ".json")) resource)))
         entry)))))

(defn all [_]
  (compile nil)
  (download-definitions {:version "4.0.1"})
  (extract-single-definitions {:version "4.0.1"})
  (b/write-file {:path "target/prep-done" :string ""}))
