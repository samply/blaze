(ns build
  (:refer-clojure :exclude [compile])
  (:require [clojure.tools.build.api :as b]))

(defn compile [_]
  (b/javac
   {:basis (b/create-basis {:project "deps.edn"})
    :src-dirs ["java"]
    :class-dir "target/classes"
    :javac-opts ["-Xlint:all" "-proc:none" "--release" "21"]}))

(defn copy-profiles [_]
  (doseq [file ["Bundle-JobSearchParameterBundle"
                "CodeSystem-ColumnFamily"
                "CodeSystem-Database"
                "ValueSet-ColumnFamily"
                "ValueSet-Database"]]
    (b/copy-file
     {:src (str "../../job-ig/fsh-generated/resources/" file ".json")
      :target (str "target/generated-resources/blaze/db/" file ".json")})))

(defn all [_]
  (compile nil)
  (copy-profiles nil)
  (b/write-file {:path "target/prep-done" :string ""}))
