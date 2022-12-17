(ns build
  (:refer-clojure :exclude [compile])
  (:require [clojure.tools.build.api :as b]))


(defn compile [_]
  (b/compile-clj
    {:basis (b/create-basis
              {:project "deps.edn"
               :compile-opts {:direct-linking true}})
     :class-dir "target/classes"
     :ns-compile ['blaze.metrics.collector]}))
