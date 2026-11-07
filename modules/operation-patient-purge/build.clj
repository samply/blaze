(ns build
  (:require [clojure.tools.build.api :as b]))

(defn copy-profiles [_]
  (doseq [file ["GraphDefinition-PatientPurge"]]
    (b/copy-file
     {:src (str "../ig/fsh-generated/resources/" file ".json")
      :target (str "target/generated-resources/blaze/operation/patient/purge/" file ".json")})))
