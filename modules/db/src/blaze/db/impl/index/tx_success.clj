(ns blaze.db.impl.index.tx-success
  "Functions for accessing the TxSuccess index.

  The TxSuccess index contains the real point in time, as java.time.Instant,
  successful transactions happened. In other words, this index maps each t which
  is just a monotonically increasing number to a real point in time."
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.db.impl.index.cbor :as cbor]
   [blaze.db.impl.iterators :as i]
   [blaze.db.kv :as kv])
  (:import
   [com.github.benmanes.caffeine.cache CacheLoader LoadingCache]
   [com.google.common.primitives Longs]
   [java.time Instant]))

(set! *warn-on-reflection* true)

(defn- decode-tx [value-bytes t]
  (let [{:keys [inst]} (cbor/read value-bytes)]
    {:blaze.db/t t
     :blaze.db.tx/instant (Instant/ofEpochMilli inst)}))

(defn- encode-key [^long t]
  (Longs/toByteArray t))

(defn cache-loader [kv-store]
  (reify CacheLoader
    (load [_ t]
      (some-> (kv/get kv-store :tx-success-index (encode-key t))
              (decode-tx t)))))

(defn tx
  "Returns the transaction with `t` using `kv-store` or nil of none was found.

  Errored transactions are returned by `blaze.db.impl.index.tx-error/tx-error`."
  [^LoadingCache tx-cache t]
  (.get tx-cache t))

(defn last-t
  "Returns the last known `t` or nil if the store is empty."
  [kv-store]
  (with-open [snapshot (kv/new-snapshot kv-store)]
    (i/seek-key-first snapshot :tx-success-index bb/get-long!)))

(defn- encode-value
  "Encodes the value of the TxSuccess index.

  Currently only the `instant` is encoded. A map is used in CBOR format to be
  able to add additional data later."
  [instant]
  (cbor/write {:inst (inst-ms instant)}))

(defn index-entry
  "Returns an entry of the TxSuccess index build from `t` and `instant`."
  [t instant]
  [:tx-success-index (encode-key t) (encode-value instant)])
