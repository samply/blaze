(ns build
  (:require [clojure.tools.build.api :as b]))

(defn copy-profiles [_]
  (doseq [file ["StructureDefinition-AsyncInteractionJob"
                "StructureDefinition-AsyncInteractionRequestBundle"
                "StructureDefinition-AsyncInteractionResponseBundle"
                "CodeSystem-AsyncInteractionJobParameter"
                "CodeSystem-AsyncInteractionJobOutput"]]
    (b/copy-file
     {:src (str "../../job-ig/fsh-generated/resources/" file ".json")
      :target (str "target/generated-resources/blaze/job/async_interaction/" file ".json")})))
