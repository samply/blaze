(ns blaze.db.impl.index.system-as-of
  "Functions for accessing the SystemAsOf index."
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.iterators :as i])
  (:import
   [com.google.common.primitives Longs]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def ^:private ^:const ^long t-tid-size
  (+ codec/t-size codec/tid-size))

(defn- decoder
  "Returns a function which decodes a resource handle out of a key and a value
  byte buffers from the SystemAsOf index if not purged at `base-t`.

  Closes over a shared byte array for id decoding, because the String
  constructor creates a copy of the id bytes anyway. Can only be used from one
  thread.

  Both byte buffers are changed during decoding and have to be reset accordingly
  after decoding."
  [base-t since-t]
  (let [ib (byte-array codec/max-id-size)]
    (fn [[kb vb]]
      (let [resource-t (codec/descending-long (bb/get-long! kb))]
        (when (< (long since-t) resource-t)
          (rh/resource-handle!
           (bb/get-int! kb)
           (let [id-size (bb/remaining kb)]
             (bb/copy-into-byte-array! kb ib 0 id-size)
             (codec/id ib 0 id-size))
           resource-t base-t vb))))))

(defn encode-key
  "Encodes the key of the SystemAsOf index from `t`, `tid` and `id`."
  [t tid id]
  (-> (bb/allocate (unchecked-add-int t-tid-size (bs/size id)))
      (bb/put-long! (codec/descending-long ^long t))
      (bb/put-int! tid)
      (bb/put-byte-string! id)
      bb/array))

(defn- encode-t-tid [start-t start-tid]
  (-> (bb/allocate t-tid-size)
      (bb/put-long! (codec/descending-long ^long start-t))
      (bb/put-int! start-tid)
      bb/array))

(defn- start-key [start-t start-tid start-id]
  (cond
    start-id
    (encode-key start-t start-tid start-id)

    start-tid
    (encode-t-tid start-t start-tid)

    :else
    (Longs/toByteArray (codec/descending-long ^long start-t))))

(defn system-history
  "Returns a reducible collection of all historic resource handles of `batch-db`
  between `start-t` (inclusive), `start-tid` (optional, inclusive) and
  `start-id` (optional, inclusive)."
  {:arglists '([batch-db start-t start-tid start-id])}
  [{:keys [snapshot t since-t]} start-t start-tid start-id]
  (i/entries
   snapshot :system-as-of-index
   (keep (decoder t since-t))
   (bs/from-byte-array (start-key start-t start-tid start-id))))

(defn changes
  "Returns a reducible collection of all resource handles changed at `t`."
  [snapshot t]
  (i/prefix-entries snapshot :system-as-of-index (keep (decoder t 0)) codec/t-size
                    (bs/from-byte-array (start-key t nil nil))))
