(ns build
  (:require [clojure.tools.build.api :as b]))

(defn copy-profiles [_]
  (doseq [file ["StructureDefinition-PruneJob"
                "CodeSystem-PruneJobParameter"
                "CodeSystem-PruneJobOutput"
                "CodeSystem-PruneIndices"
                "ValueSet-PruneIndices"]]
    (b/copy-file
     {:src (str "../../job-ig/fsh-generated/resources/" file ".json")
      :target (str "resources/blaze/job/prune/" file ".json")})))
