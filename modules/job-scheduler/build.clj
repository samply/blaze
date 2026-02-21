(ns build
  (:require [clojure.tools.build.api :as b]))

(defn copy-profiles [_]
  (doseq [file ["StructureDefinition-Job"
                "CodeSystem-JobType"
                "CodeSystem-JobOutput"]]
    (b/copy-file
     {:src (str "../../job-ig/fsh-generated/resources/" file ".json")
      :target (str "target/generated-resources/blaze/job_scheduler/" file ".json")})))
