(ns blaze.db.impl.index.type-as-of
  "Functions for accessing the TypeAsOf index."
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


(def ^:private ^:const ^long tid-t-size
  (+ codec/tid-size codec/t-size))


(def ^:private ^:const ^long key-size
  (+ tid-t-size codec/did-size))


(def ^:private ^:const ^long max-value-size
  (+ hash/size codec/state-size codec/max-id-size))


(defn- key-valid? [^long tid ^long end-t]
  (fn [handle]
    (when (= (rh/tid handle) tid) (< end-t (rh/t handle)))))


(defn- decoder
  "Returns a function which decodes an resource handle out of a key and a value
  byte buffers from the TypeAsOf index.

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
     (let [tid (bb/get-int! kb)
           t (codec/descending-long (bb/get-5-byte-long! kb))]
       (rh/resource-handle
         tid
         (bb/get-long! kb)
         t
         vb)))))


(defn encode-key
  "Encodes the key of the TypeAsOf index from `tid`, `t` and `did`."
  [tid t did]
  (-> (bb/allocate key-size)
      (bb/put-int! tid)
      (bb/put-5-byte-long! (codec/descending-long (unchecked-long t)))
      (bb/put-long! (unchecked-long did))
      bb/array))


(defn- start-key [tid start-t start-did]
  (if start-did
    (bs/from-byte-array (encode-key tid start-t start-did))
    (-> (bb/allocate tid-t-size)
        (bb/put-int! tid)
        (bb/put-5-byte-long! (codec/descending-long (unchecked-long start-t)))
        bb/flip!
        bs/from-byte-buffer!)))


(defn type-history
  "Returns a reducible collection of all versions between `start-t` (inclusive),
  `start-did` (optional, inclusive) and `end-t` (inclusive) of resources with
  `tid`.

  Versions are resource handles."
  [taoi tid start-t start-did end-t]
  (coll/eduction
    (take-while (key-valid? tid end-t))
    (i/kvs! taoi (decoder) (start-key tid start-t start-did))))
