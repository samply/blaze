(ns build
  (:require [clojure.tools.build.api :as b]))

(defn copy-profiles [_]
  (doseq [file ["Bundle-JobSearchParameterBundle"
                "CodeSystem-ColumnFamily"
                "CodeSystem-Database"
                "ValueSet-ColumnFamily"
                "ValueSet-Database"]]
    (b/copy-file
     {:src (str "../../job-ig/fsh-generated/resources/" file ".json")
      :target (str "resources/blaze/db/" file ".json")})))
