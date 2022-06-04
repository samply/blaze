(ns blaze.db.node.version
  (:refer-clojure :exclude [get key])
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.db.kv :as kv])
  (:import
    [java.nio.charset StandardCharsets]))


(set! *warn-on-reflection* true)


(def key
  (.getBytes "version" StandardCharsets/ISO_8859_1))


(defn encode-value [version]
  (-> (bb/allocate Integer/BYTES)
      (bb/put-int! version)
      (bb/array)))


(defn- decode-value [bytes]
  (bb/get-int! (bb/wrap bytes)))


(defn get [store]
  (or (some-> (kv/get store key) decode-value) 0))


(defn set! [store version]
  (kv/put! store key (encode-value version)))
