(ns build
  (:require
   [hato.client :as hc]
   [clojure.tools.build.api :as b]))

(defn download-file [url output-path]
  (let [response (hc/get url {:as :byte-array})]
    (with-open [out (java.io.FileOutputStream. output-path)]
      (.write out (:body response)))))

(defn download-loinc [_]
  (download-file "https://speicherwolke.uni-leipzig.de/index.php/s/S8Bej7LPjbGACdo/download/Loinc_2.78.zip" "loinc.zip")
  (b/unzip {:zip-file "loinc.zip" :target-dir "resources/blaze/terminology_service/local/code_system/loinc"})
  (b/delete {:path "loinc.zip"}))
