(ns build
  (:require
   [clojure.java.io :as io]
   [clojure.tools.build.api :as b]
   [hato.client :as hc])
  (:import
   [java.io FileOutputStream]
   [java.security MessageDigest]
   [java.util.zip ZipFile]))

(set! *warn-on-reflection* true)

(defn download-file [url output-path]
  (println "Download:" output-path)
  (let [http-client (hc/build-http-client {:redirect-policy :normal})
        response (hc/get url {:http-client http-client :as :byte-array})]
    (with-open [out (FileOutputStream. ^String output-path)]
      (.write out ^bytes (:body response)))))

(defn- fetch-loinc [filename]
  (try
    (download-file "https://deps.blaze-server.eu/Loinc_2.83_578bb2001df9ebf74b5566a11ccf0ebb.zip" filename)
    (catch Exception e
      (io/delete-file filename true)
      (throw (ex-info (format "Error while downloading the LOINC release archive (%s). Please download %s from https://loinc.org/downloads/ and put it into the directory modules/terminology-service-local." (ex-message e) filename)
                      {:file filename})))))

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

(def ^:private used-files
  "The files of the LOINC release archive Blaze uses.

  These are the files read in
  `blaze.terminology-service.local.code-system.loinc.context` and the license,
  which section 9 of it requires to be included in every copy. The archive holds
  about 1 GB of further files that would only bloat the uberjar and slow down
  its build."
  ["LoincLicense_5.8.txt"
   "LoincTable/Loinc.csv"
   "AccessoryFiles/AnswerFile/AnswerList.csv"
   "AccessoryFiles/PartFile/Part.csv"])

(defn- extract-file
  "Extracts the file with `name` from `zip` into `target-dir`."
  [^ZipFile zip target-dir ^String name]
  (if-let [entry (.getEntry zip name)]
    (let [file (io/file target-dir name)]
      (io/make-parents file)
      (with-open [in (.getInputStream zip entry)]
        (io/copy in file)))
    (throw (ex-info (format "Missing file %s in the LOINC release archive." name)
                    {:name name}))))

(defn download-loinc
  "Downloads the LOINC release archive and extracts the files Blaze uses. Uses
  an archive already placed in this module directory."
  [_]
  (let [filename "Loinc_2.83.zip"]
    (when-not (.exists (io/file filename))
      (fetch-loinc filename))
    (verify-download filename "077a0718e87d8309ffe3a673f75b836a8e783dc36a646413ef97c71c12eab27e")
    (with-open [zip (ZipFile. filename)]
      (run! #(extract-file zip "target/generated-resources/blaze/terminology_service/local/code_system/loinc" %)
            used-files))
    (b/delete {:path filename}))
  (b/write-file {:path "target/prep-done" :string ""}))
