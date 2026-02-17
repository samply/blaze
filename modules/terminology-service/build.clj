(ns build
  (:require
   [clojure.java.io :as io]
   [clojure.tools.build.api :as b]
   [hato.client :as hc])
  (:import
   [java.io FileOutputStream]
   [java.security MessageDigest]))

(set! *warn-on-reflection* true)

(defn download-file [url output-path]
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

(defn download-loinc [_]
  (download-file "https://speicherwolke.uni-leipzig.de/index.php/s/S8Bej7LPjbGACdo/download/Loinc_2.78.zip" "loinc.zip")
  (verify-download "loinc.zip" "ab5528a4c703bdc79deabbdd5e1def1335d127a643da97b68f686814ed526d46")
  (b/unzip {:zip-file "loinc.zip" :target-dir "resources/blaze/terminology_service/local/code_system/loinc"})
  (b/delete {:path "loinc.zip"}))
