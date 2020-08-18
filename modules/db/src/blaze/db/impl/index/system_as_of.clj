(ns blaze.db.impl.index.system-as-of
  (:require
    [blaze.coll.core :as coll]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.db.impl.iterators :as i])
  (:import
    [clojure.lang IReduceInit]
    [java.nio ByteBuffer]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn- key-valid? [^long end-t]
  (fn [handle]
    (< end-t ^long (rh/t handle))))


(defn- decoder
  "Returns a function which decodes an resource handle out of a key and a value
  ByteBuffer from the resource-as-of index.

  Closes over a shared byte array for id decoding, because the String
  constructor creates a copy of the id bytes anyway. Can only be used from one
  thread.

  The decode function creates only four objects, the resource handle, the String
  for the id, the byte array inside the string and the byte array for the hash.

  Both ByteBuffers are changed during decoding and have to be reset accordingly
  after decoding."
  []
  (let [ib (byte-array codec/max-id-size)]
    (fn
      ([]
       [(ByteBuffer/allocateDirect (+ codec/t-size codec/tid-size codec/max-id-size))
        (ByteBuffer/allocateDirect (+ codec/hash-size codec/state-size))])
      ([^ByteBuffer kb ^ByteBuffer vb]
       (let [t (codec/get-t! kb)]
         (rh/resource-handle
           (codec/get-tid! kb)
           (let [id-size (.remaining kb)]
             (.get kb ib 0 id-size)
             (String. ib 0 id-size codec/iso-8859-1))
           t
           (codec/get-hash! vb)
           (codec/get-state! vb)))))))


(defn- start-key [start-t start-tid start-id]
  (cond
    start-id (codec/system-as-of-key start-t start-tid start-id)
    start-tid (codec/system-as-of-key start-t start-tid)
    :else (codec/system-as-of-key start-t)))


(defn system-history
  "Returns a reducible collection of all versions between `start-t` (inclusive),
  `start-tid` (optional, inclusive), `start-id` (optional, inclusive) and
  `end-t` (inclusive) of all resources.

  Versions are resource handles."
  ^IReduceInit
  [saoi start-t start-tid start-id end-t]
  (coll/eduction
    (take-while (key-valid? end-t))
    (i/kvs saoi (decoder) (start-key start-t start-tid start-id))))
