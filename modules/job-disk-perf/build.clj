(ns build
  (:require [clojure.tools.build.api :as b]))

(defn copy-profiles [_]
  (doseq [file ["CodeSystem-DiskPerfJobParameter"
                "CodeSystem-DiskPerfJobOutput"
                "CodeSystem-DiskPerfPhase"
                "ValueSet-DiskPerfPhase"
                "CodeSystem-DiskPerfRating"
                "ValueSet-DiskPerfRating"
                "StructureDefinition-disk-perf-concurrency"
                "StructureDefinition-DiskPerfJob"]]
    (b/copy-file
     {:src (str "../ig/fsh-generated/resources/" file ".json")
      :target (str "target/generated-resources/blaze/job/disk_perf/" file ".json")})))
