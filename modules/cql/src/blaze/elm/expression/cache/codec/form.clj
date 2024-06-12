(ns blaze.elm.expression.cache.codec.form
  (:refer-clojure :exclude [hash])
  (:require
   [blaze.byte-buffer :as bb])
  (:import
   [com.google.common.hash HashCode Hashing]
   [java.nio.charset StandardCharsets]))

(set! *warn-on-reflection* true)

(defn hash ^HashCode [expr-form]
  (-> (Hashing/sha256)
      (.hashString expr-form StandardCharsets/UTF_8)))

(defn decode! [buf]
  (let [len (bb/get-int! buf)
        bytes (byte-array len)]
    (bb/copy-into-byte-array! buf bytes)
    (String. bytes StandardCharsets/UTF_8)))
