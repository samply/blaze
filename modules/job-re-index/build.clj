(ns build
  (:require [clojure.tools.build.api :as b]))

(defn copy-profiles [_]
  (doseq [file ["StructureDefinition-ReIndexJob"
                "CodeSystem-ReIndexJobParameter"
                "CodeSystem-ReIndexJobOutput"]]
    (b/copy-file
     {:src (str "../../job-ig/fsh-generated/resources/" file ".json")
      :target (str "target/generated-resources/blaze/job/re_index/" file ".json")})))
