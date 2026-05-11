(ns blaze.db.impl.index.type-as-of
  "Functions for accessing the TypeAsOf index."
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.byte-string-builder :as bsb]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.index.util :as u :refer [read-t!]]
   [blaze.db.impl.iterators :as i]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def ^:private ^:const ^long tid-t-size
  (+ codec/tid-size codec/t-size))

(defn- decoder
  "Returns a transducer which decodes resource handles out of key and value byte
  buffers from the TypeAsOf index. Skips entries purged at `base-t` and
  terminates the reduction once `since-t` is reached, because the index is
  ordered by descending `t` within a `tid` prefix.

  Can only be used from one thread.

  Both buffers are changed during decoding and have to be reset accordingly
  after decoding."
  [tid base-t since-t]
  (let [read-id! (u/id-reader)]
    (fn [rf]
      (fn
        ([result] (rf result))
        ([result [kb vb]]
         (bb/set-position! kb codec/tid-size)
         (let [resource-t (read-t! kb)]
           (if (< (long since-t) resource-t)
             (let [handle (rh/resource-handle! tid (read-id! kb) resource-t
                                               base-t vb)]
               (cond-> result handle (rf handle)))
             (reduced result))))))))

(defn encode-key
  "Encodes the key of the TypeAsOf index from `tid`, `t` and `id`."
  [tid t id]
  (-> (bsb/allocate (unchecked-add-int tid-t-size (bs/size id)))
      (bsb/put-int! tid)
      (bsb/put-long! (codec/descending-long ^long t))
      (bsb/put-byte-string! id)
      bsb/to-bytes))

(defn- start-key [tid start-t start-id]
  (if start-id
    (bs/from-byte-array (encode-key tid start-t start-id))
    (-> (bsb/allocate tid-t-size)
        (bsb/put-int! tid)
        (bsb/put-long! (codec/descending-long ^long start-t))
        bsb/build)))

(defn type-history
  "Returns a reducible collection of all historic resource handles with type
  `tid` of the database with the point in time `t` between `start-t` (inclusive)
  and `start-id` (optional, inclusive)."
  [snapshot t since-t tid start-t start-id]
  (i/prefix-entries
   snapshot :type-as-of-index
   (decoder tid t since-t)
   codec/tid-size (start-key tid start-t start-id)))
