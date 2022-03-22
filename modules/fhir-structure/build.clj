(ns build
  "Blaze fhir-structure module build script.

  For more information, run:

  clojure -A:deps -T:build help/doc"
  (:refer-clojure :exclude [compile])
  (:require
    [clojure.tools.build.api :as b]))


(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))


(defn compile
  "Compile Java Code."
  [_]
  (b/javac
    {:src-dirs ["java"]
     :class-dir class-dir
     :basis basis
     :javac-opts ["-source" "11" "-target" "11" "-Xlint:unchecked"]}))
