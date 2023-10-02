(ns blaze.db.node.version
  (:refer-clojure :exclude [key])
  (:import
    [com.google.common.primitives Longs]
    [java.nio.charset StandardCharsets]))


(set! *warn-on-reflection* true)


(def key
  (.getBytes "version" StandardCharsets/ISO_8859_1))


(defn encode-value [version]
  (Longs/toByteArray version))


(defn decode-value [bytes]
  (Longs/fromByteArray bytes))
