(ns blaze.db.tx-log.local.codec
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.fhir.hash :as hash]
   [jsonista.core :as j]
   [taoensso.timbre :as log])
  (:import
   [com.fasterxml.jackson.dataformat.cbor CBORFactory]
   [com.google.common.primitives Longs]
   [java.time Instant]))

(set! *warn-on-reflection* true)

(def ^:private cbor-object-mapper
  (j/object-mapper
   {:factory (CBORFactory.)
    :decode-key-fn true
    :modules [hash/object-mapper-module]}))

(defn encode-key [t]
  (Longs/toByteArray t))

(defn encode-tx-data [instant tx-cmds]
  (j/write-value-as-bytes
   {:instant (inst-ms instant)
    :tx-cmds tx-cmds}
   cbor-object-mapper))

(defn- parse-cbor-error-msg [t e]
  (format "Skip transaction with point in time of %d because there was an error while parsing tx-data: %s"
          t (ex-message e)))

(defn- parse [value t]
  (try
    (j/read-value value cbor-object-mapper)
    (catch Exception e
      (log/warn (parse-cbor-error-msg t e)))))

(defn- decode-hash [{:keys [hash] :as tx-cmd}]
  (if hash
    (assoc tx-cmd :hash (hash/from-byte-buffer! (bb/wrap hash)))
    tx-cmd))

(defn- decode-instant [x]
  (when (int? x)
    (Instant/ofEpochMilli x)))

(defn decode-tx-data [kb vb]
  (let [t (bb/get-long! kb)
        size (bb/remaining vb)
        value (byte-array size)]
    (bb/copy-into-byte-array! vb value 0 size)
    (-> (parse value t)
        (update :tx-cmds #(mapv decode-hash %))
        (update :instant decode-instant)
        (assoc :t t))))

(defn encode-entry [t instant tx-cmds]
  [:default (encode-key t) (encode-tx-data instant tx-cmds)])
