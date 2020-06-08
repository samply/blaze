(ns blaze.db.impl.index.system-as-of
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


(defrecord SystemAsOfKV [^long t ^long tid id hash ^long state])


(defn- key-valid? [^long end-t]
  (fn [^SystemAsOfKV kv]
    (< end-t (.t kv))))


(defn- new-resource [node]
  (fn [^SystemAsOfKV entry]
    (resource/new-resource node (.tid entry) (.id entry) (.hash entry) (.state entry)
                           (.t entry))))


(defn- decoder
  "Returns a function which decodes an `SystemAsOfKV` out of a key and a value
  ByteBuffer from the resource-as-of index.

  Closes over a shared byte array for id decoding, because the String
  constructor creates a copy of the id bytes anyway. Can only be used from one
  thread.

  The decode function creates only four objects, the SystemAsOfKV, the String
  for the id, the byte array inside the string and the byte array for the hash.

  Both ByteBuffers are changed during decoding and have to be reset accordingly
  after decoding."
  []
  (let [ib (byte-array codec/max-id-size)]
    (fn [^ByteBuffer kb ^ByteBuffer vb]
      (SystemAsOfKV.
        (codec/get-t! kb)
        (codec/get-tid! kb)
        (let [id-size (.remaining kb)]
          (.get kb ib 0 id-size)
          (String. ib 0 id-size codec/iso-8859-1))
        (codec/get-hash! vb)
        (codec/get-state! vb)))))


(defn- start-key [start-t start-tid start-id]
  (cond
    start-id (codec/system-as-of-key start-t start-tid start-id)
    start-tid (codec/system-as-of-key start-t start-tid)
    :else (codec/system-as-of-key start-t)))


(defn system-history
  "Returns a reducible collection of all versions between `start-t` (inclusive),
  `start-tid` (optional, inclusive), `start-id` (optional, inclusive) and
  `end-t` (inclusive) of all resources.

  Versions are resources itself."
  ^IReduceInit
  [node saoi start-t start-tid start-id end-t]
  (coll/eduction
    (comp
      (take-while (key-valid? end-t))
      (map (new-resource node)))
    (i/kvs saoi (decoder) (start-key start-t start-tid start-id))))
