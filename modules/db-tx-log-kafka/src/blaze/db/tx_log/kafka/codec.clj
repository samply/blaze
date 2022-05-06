(ns blaze.db.tx-log.kafka.codec
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.fhir.hash :as hash]
    [jsonista.core :as j]
    [taoensso.timbre :as log])
  (:import
    [com.fasterxml.jackson.dataformat.cbor CBORFactory]
    [org.apache.kafka.common.serialization Serializer Deserializer]))


(def ^:private cbor-object-mapper
  (j/object-mapper
    {:factory (CBORFactory.)
     :decode-key-fn true
     :modules [hash/object-mapper-module]}))


(deftype CborSerializer []
  Serializer
  (serialize [_ _ data]
    (j/write-value-as-bytes data cbor-object-mapper)))


(def ^Serializer serializer (CborSerializer.))


(defn- parse-cbor [data]
  (try
    (j/read-value data cbor-object-mapper)
    (catch Exception e
      (log/warn (format "Error while parsing tx-data: %s" (ex-message e))))))


(defn- decode-hash [{:keys [hash] :as tx-cmd}]
  (if hash
    (assoc tx-cmd :hash (hash/from-byte-buffer! (bb/wrap hash)))
    tx-cmd))


(defn- decode-hashes [cmds]
  (when (sequential? cmds)
    (mapv decode-hash cmds)))


(deftype CborDeserializer []
  Deserializer
  (deserialize [_ _ data]
    (decode-hashes (parse-cbor data))))


(def ^Deserializer deserializer (CborDeserializer.))
