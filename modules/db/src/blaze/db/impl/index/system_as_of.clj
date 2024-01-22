(ns blaze.db.impl.index.system-as-of
  "Functions for accessing the SystemAsOf index."
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.iterators :as i])
  (:import
   [com.google.common.primitives Longs]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def ^:private ^:const ^long t-tid-size
  (+ codec/t-size codec/tid-size))

(defn- key-valid? [^long end-t]
  (fn [handle]
    (< end-t (rh/t handle))))

(defn- decoder
  "Returns a function which decodes an resource handle out of a key and a value
  byte buffers from the SystemAsOf index.

  Closes over a shared byte array for id decoding, because the String
  constructor creates a copy of the id bytes anyway. Can only be used from one
  thread.

  The decode function creates only five objects, the resource handle, the String
  for the id, the byte array inside the String, the ByteString and the byte
  array inside the ByteString for the hash.

  Both byte buffers are changed during decoding and have to be reset accordingly
  after decoding."
  []
  (let [ib (byte-array codec/max-id-size)]
    (fn [entry]
      (let [kb (i/key entry)
            t (codec/descending-long (bb/get-long! kb))]
        (rh/resource-handle!
         (bb/get-int! kb)
         (let [id-size (bb/remaining kb)]
           (bb/copy-into-byte-array! kb ib 0 id-size)
           (codec/id ib 0 id-size))
         t (i/value entry))))))

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
  "Returns a reducible collection of all versions between `start-t` (inclusive),
  `start-tid` (optional, inclusive), `start-id` (optional, inclusive) and
  `end-t` (inclusive) of all resources.

  Versions are resource handles."
  [snapshot start-t start-tid start-id end-t]
  (coll/eduction
   (comp
    (map (decoder))
    (take-while (key-valid? end-t)))
   (i/entries snapshot :system-as-of-index
              (bs/from-byte-array (start-key start-t start-tid start-id)))))
