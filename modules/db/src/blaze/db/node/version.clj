(ns blaze.db.node.version
  (:refer-clojure :exclude [key])
  (:require
    [blaze.byte-buffer :as bb])
  (:import
    [java.nio.charset StandardCharsets]))


(set! *warn-on-reflection* true)


(def key
  (.getBytes "version" StandardCharsets/ISO_8859_1))


(defn encode-value [version]
  (-> (bb/allocate Integer/BYTES)
      (bb/put-int! version)
      (bb/array)))


(defn decode-value [bytes]
  (bb/get-int! (bb/wrap bytes)))
