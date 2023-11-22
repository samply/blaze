(ns blaze.db.node.version
  (:refer-clojure :exclude [key])
  (:import
   [com.google.common.primitives Ints]
   [java.nio.charset StandardCharsets]))

(set! *warn-on-reflection* true)

(def key
  (.getBytes "version" StandardCharsets/ISO_8859_1))

(defn encode-value [version]
  (Ints/toByteArray version))

(defn decode-value [bytes]
  (Ints/fromByteArray bytes))
