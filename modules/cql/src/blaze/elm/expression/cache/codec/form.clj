(ns blaze.elm.expression.cache.codec.form
  (:refer-clojure :exclude [hash])
  (:require
   [blaze.byte-buffer :as bb])
  (:import
   [com.google.common.hash Hashing]
   [java.nio.charset StandardCharsets]))

(set! *warn-on-reflection* true)

(defn hash [expr-form]
  (-> (Hashing/sha256)
      (.hashString expr-form StandardCharsets/UTF_8)
      (.asBytes)))

(defn decode! [buf]
  (let [len (bb/get-int! buf)
        bytes (byte-array len)]
    (bb/copy-into-byte-array! buf bytes)
    (String. bytes StandardCharsets/UTF_8)))
