(ns build
  (:require
   [clojure.tools.build.api :as b]
   [hato.client :as hc])
  (:import
   [java.io FileOutputStream]))

(set! *warn-on-reflection* true)

(defn download-file [url output-path]
  (let [http-client (hc/build-http-client {:redirect-policy :normal})
        response (hc/get url {:http-client http-client :as :byte-array})]
    (with-open [out (FileOutputStream. ^String output-path)]
      (.write out ^bytes (:body response)))))

(defn download-loinc [_]
  (download-file "https://speicherwolke.uni-leipzig.de/index.php/s/S8Bej7LPjbGACdo/download/Loinc_2.78.zip" "loinc.zip")
  (b/unzip {:zip-file "loinc.zip" :target-dir "resources/blaze/terminology_service/local/code_system/loinc"})
  (b/delete {:path "loinc.zip"}))
