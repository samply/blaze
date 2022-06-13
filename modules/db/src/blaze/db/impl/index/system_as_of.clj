(ns blaze.db.impl.index.system-as-of
  "Functions for accessing the SystemAsOf index."
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.byte-string :as bs]
    [blaze.coll.core :as coll]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.db.impl.iterators :as i]
    [blaze.fhir.hash :as hash]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(def ^:private ^:const ^long t-tid-size
  (+ codec/t-size codec/tid-size))


(def ^:private ^:const ^long key-size
  (+ t-tid-size codec/did-size))


(def ^:private ^:const ^long max-value-size
  (+ hash/size codec/state-size codec/max-id-size))


(defn- key-valid? [^long end-t]
  (fn [handle]
    (< end-t (rh/t handle))))


(defn- decoder
  "Returns a function which decodes an resource handle out of a key and a value
  byte buffers from the resource-as-of index.

  Closes over a shared byte array for id decoding, because the String
  constructor creates a copy of the id bytes anyway. Can only be used from one
  thread.

  The decode function creates only five objects, the resource handle, the String
  for the id, the byte array inside the String, the ByteString and the byte
  array inside the ByteString for the hash.

  Both byte buffers are changed during decoding and have to be reset accordingly
  after decoding."
  []
  (fn
    ([]
     [(bb/allocate-direct key-size)
      (bb/allocate-direct max-value-size)])
    ([kb vb]
     (let [t (codec/descending-long (bb/get-5-byte-long! kb))]
       (rh/resource-handle (bb/get-int! kb) (bb/get-long! kb) t vb)))))


(defn encode-key
  "Encodes the key of the SystemAsOf index from `t`, `tid` and `did`."
  [t tid did]
  (-> (bb/allocate key-size)
      (bb/put-5-byte-long! (codec/descending-long (unchecked-long t)))
      (bb/put-int! tid)
      (bb/put-long! did)
      bb/array))


(defn- encode-t-tid [start-t start-tid]
  (-> (bb/allocate t-tid-size)
      (bb/put-5-byte-long! (codec/descending-long (unchecked-long start-t)))
      (bb/put-int! start-tid)
      bb/array))


(defn- start-key [start-t start-tid start-did]
  (cond
    start-did
    (encode-key start-t start-tid start-did)

    start-tid
    (encode-t-tid start-t start-tid)

    :else
    (-> (bb/allocate codec/t-size)
        (bb/put-5-byte-long! (codec/descending-long (unchecked-long start-t)))
        bb/array)))


(defn system-history
  "Returns a reducible collection of all versions between `start-t` (inclusive),
  `start-tid` (optional, inclusive), `start-did` (optional, inclusive) and
  `end-t` (inclusive) of all resources.

  Versions are resource handles."
  [saoi start-t start-tid start-did end-t]
  (coll/eduction
    (take-while (key-valid? end-t))
    (i/kvs! saoi (decoder) (bs/from-byte-array (start-key start-t start-tid start-did)))))
