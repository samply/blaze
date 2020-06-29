(ns blaze.db.impl.index.type-as-of
  (:require
    [blaze.coll.core :as coll]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource :as resource]
    [blaze.db.impl.iterators :as i])
  (:import
    [clojure.lang IReduceInit]
    [java.nio ByteBuffer]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defrecord TypeAsOfKV [^long tid ^long t id hash ^long state])


(defn- key-valid? [^long tid ^long end-t]
  (fn [^TypeAsOfKV kv]
    (and (= (.tid kv) tid) (< end-t (.t kv)))))


(defn- new-resource [node]
  (fn [^TypeAsOfKV entry]
    (resource/new-resource node (.tid entry) (.id entry) (.hash entry)
                           (.state entry) (.t entry))))


(defn- decoder
  "Returns a function which decodes an `TypeAsOfKV` out of a key and a value
  ByteBuffer from the resource-as-of index.

  Closes over a shared byte array for id decoding, because the String
  constructor creates a copy of the id bytes anyway. Can only be used from one
  thread.

  The decode function creates only four objects, the TypeAsOfKV, the String
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
       (TypeAsOfKV.
         (codec/get-tid! kb)
         (codec/get-t! kb)
         (let [id-size (.remaining kb)]
           (.get kb ib 0 id-size)
           (String. ib 0 id-size codec/iso-8859-1))
         (codec/get-hash! vb)
         (codec/get-state! vb))))))


(defn- start-key [tid start-t start-id]
  (if start-id
    (codec/type-as-of-key tid start-t start-id)
    (codec/type-as-of-key tid start-t)))


(defn type-history
  "Returns a reducible collection of all versions between `start-t` (inclusive),
  `start-id` (optional, inclusive) and `end-t` (inclusive) of resources with
  `tid`.

  Versions are resources itself."
  ^IReduceInit
  [node taoi tid start-t start-id end-t]
  (coll/eduction
    (comp
      (take-while (key-valid? tid end-t))
      (map (new-resource node)))
    (i/kvs taoi (decoder) (start-key tid start-t start-id))))
