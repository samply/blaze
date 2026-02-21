(ns build
  (:require [clojure.tools.build.api :as b]))

(defn copy-profiles [_]
  (doseq [file ["CodeSystem-CompactJobParameter"
                "CodeSystem-CompactJobOutput"
                "StructureDefinition-CompactJob"]]
    (b/copy-file
     {:src (str "../../job-ig/fsh-generated/resources/" file ".json")
      :target (str "target/generated-resources/blaze/job/compact/" file ".json")})))
