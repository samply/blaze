(ns blaze.db.tx-log.local.codec
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.db.permissions :as p]
   [blaze.db.tx-log.util :as tx-log-u]
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

(defn encode-tx-data
  "Encodes the transaction data of `tx-cmds` submitted at `instant` with
  `permissions`.

  The permissions are a key of their own, absent if `permissions` is nil, so
  that transaction data written before permissions existed decode as absent
  permissions."
  [instant tx-cmds permissions]
  (j/write-value-as-bytes
   (cond-> {:instant (inst-ms instant)
            :tx-cmds tx-cmds}
     permissions (assoc :permissions permissions))
   cbor-object-mapper))

(defn- invalid-key-msg [size]
  (format "Skip transaction data with a key of %d bytes instead of %d." size Long/BYTES))

(defn- parse-cbor-error-msg [t e]
  (format "Skip transaction with point in time of %d because there was an error while parsing tx-data: %s"
          t (ex-message e)))

(defn- decode-hash [{:keys [hash] :as tx-cmd}]
  (if hash
    (assoc tx-cmd :hash (hash/from-byte-buffer! (bb/wrap hash)))
    tx-cmd))

(defn- decode-instant [x]
  (when (int? x)
    (Instant/ofEpochMilli x)))

(defn- decode
  "Decodes the transaction data `x` read from CBOR with point in time `t`.

  Decodes only values of the expected shape, so that the spec check afterwards
  rejects all others. Tx-cmds that aren't sequential stay as they are, so nil
  tx-cmds don't become an empty transaction. An instant that isn't an integer
  becomes nil."
  [x t]
  (cond-> (-> (update x :instant decode-instant)
              (assoc :t t))
    (sequential? (:tx-cmds x)) (update :tx-cmds #(mapv decode-hash %))
    (contains? x :permissions) (update :permissions p/decode)))

(defn- parse
  "Parses and decodes the transaction data `value` with point in time `t`.

  Returns nil if `value` isn't valid CBOR or has a shape that can't be decoded,
  like a hash that isn't a byte string."
  [value t]
  (try
    (some-> (j/read-value value cbor-object-mapper) (decode t))
    (catch Exception e
      (log/warn (parse-cbor-error-msg t e)))))

(defn decode-tx-data
  "Decodes the transaction data of the key buffer `kb` and the value buffer
  `vb`.

  Returns nil if the key isn't a point in time or the transaction data is
  invalid (see `blaze.db.tx-log.util/valid-tx-data`), so that it is skipped like
  an invalid record of the Kafka transaction log."
  [[kb vb]]
  (if (= Long/BYTES (bb/remaining kb))
    (let [t (bb/get-long! kb)
          size (bb/remaining vb)
          value (byte-array size)]
      (bb/copy-into-byte-array! vb value 0 size)
      (some-> (parse value t) (tx-log-u/valid-tx-data)))
    (log/warn (invalid-key-msg (bb/remaining kb)))))

(defn encode-entry [t instant tx-cmds permissions]
  [:default (encode-key t) (encode-tx-data instant tx-cmds permissions)])
