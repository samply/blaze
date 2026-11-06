(ns blaze.db.tx-log.kafka.codec
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.db.permissions :as p]
   [blaze.fhir.hash :as hash]
   [jsonista.core :as j]
   [taoensso.timbre :as log])
  (:import
   [com.fasterxml.jackson.dataformat.cbor CBORFactory]
   [org.apache.kafka.common.serialization Serializer Deserializer]))

(set! *warn-on-reflection* true)

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

(def permissions-header
  "The key of the record header holding the permissions of a transaction.

  The permissions are stored in a header instead of the record value, because
  older nodes expect the value to be the bare transaction commands. A record
  value of any other shape would make them skip the transaction."
  "blaze-permissions")

(defn encode-permissions
  "Encodes `permissions` into the value of the permissions record header."
  [permissions]
  (j/write-value-as-bytes permissions cbor-object-mapper))

(defn decode-permissions
  "Decodes the `value` of the permissions record header.

  Returns nil if `value` isn't valid CBOR. The result has to be validated,
  because it can be anything."
  [value]
  (p/decode (parse-cbor value)))
