(ns build
  (:refer-clojure :exclude [compile])
  (:require [clojure.tools.build.api :as b]))

(defn compile [_]
  (b/javac
   {:basis (b/create-basis {:project "deps.edn"})
    :src-dirs ["java"]
    :class-dir "target/classes"
    :javac-opts ["-Xlint:all" "-proc:none" "--release" "21"]}))
