(ns blaze.db.impl.index.type-as-of
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


(defn- key-valid? [^long tid ^long end-t]
  (fn [handle]
    (and (= (rh/tid handle) tid) (< end-t ^long (rh/t handle)))))


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
       [(ByteBuffer/allocateDirect (+ codec/tid-size codec/t-size codec/max-id-size))
        (ByteBuffer/allocateDirect (+ codec/hash-size codec/state-size))])
      ([^ByteBuffer kb ^ByteBuffer vb]
       (let [tid (codec/get-tid! kb)
             t (codec/get-t! kb)]
         (rh/resource-handle
           tid
           (let [id-size (.remaining kb)]
             (.get kb ib 0 id-size)
             (String. ib 0 id-size codec/iso-8859-1))
           t
           (codec/get-hash! vb)
           (codec/get-state! vb)))))))


(defn- start-key [tid start-t start-id]
  (if start-id
    (codec/type-as-of-key tid start-t start-id)
    (codec/type-as-of-key tid start-t)))


(defn type-history
  "Returns a reducible collection of all versions between `start-t` (inclusive),
  `start-id` (optional, inclusive) and `end-t` (inclusive) of resources with
  `tid`.

  Versions are resource handles."
  ^IReduceInit
  [taoi tid start-t start-id end-t]
  (coll/eduction
    (take-while (key-valid? tid end-t))
    (i/kvs taoi (decoder) (start-key tid start-t start-id))))
