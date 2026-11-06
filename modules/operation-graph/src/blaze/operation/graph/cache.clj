(ns blaze.operation.graph.cache
  "Caching of compiled graphs."
  (:refer-clojure :exclude [compile])
  (:require
   [blaze.operation.graph.compiler :as c])
  (:import
   [com.github.benmanes.caffeine.cache Cache]))

(set! *warn-on-reflection* true)

(defn compile
  "Returns the compiled graph of `graph-def` from `compiled-graph-cache` or
  compiles it.

  Returns an anomaly in case of compile errors."
  [compiled-graph-cache {:keys [id] {version :versionId} :meta :as graph-def}]
  (let [key [id (:value version)]]
    (.get ^Cache compiled-graph-cache key (fn [_] (c/compile graph-def)))))
