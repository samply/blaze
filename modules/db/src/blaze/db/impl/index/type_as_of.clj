(ns blaze.db.impl.index.type-as-of
  "Functions for accessing the TypeAsOf index."
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.iterators :as i]
   [blaze.fhir.hash :as hash]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def ^:private ^:const ^long tid-t-size
  (+ codec/tid-size codec/t-size))

(defn- decoder
  "Returns a function which decodes an resource handle out of a key and a value
  byte buffer from the TypeAsOf index if not purged at `base-t`.

  Closes over a shared byte array for id decoding, because the String
  constructor creates a copy of the id bytes anyway. Can only be used from one
  thread.

  Both buffers are changed during decoding and have to be reset accordingly
  after decoding."
  [tid base-t]
  (let [ib (byte-array codec/max-id-size)]
    (fn [[kb vb]]
      (bb/set-position! kb codec/tid-size)
      (let [t (codec/descending-long (bb/get-long! kb))]
        (rh/resource-handle!
         tid
         (let [id-size (bb/remaining kb)]
           (bb/copy-into-byte-array! kb ib 0 id-size)
           (codec/id ib 0 id-size))
         t base-t vb)))))

(defn encode-key
  "Encodes the key of the TypeAsOf index from `tid`, `t` and `id`."
  [tid t id]
  (-> (bb/allocate (unchecked-add-int tid-t-size (bs/size id)))
      (bb/put-int! tid)
      (bb/put-long! (codec/descending-long ^long t))
      (bb/put-byte-string! id)
      bb/array))

(defn- start-key [tid start-t start-id]
  (if start-id
    (bs/from-byte-array (encode-key tid start-t start-id))
    (-> (bb/allocate tid-t-size)
        (bb/put-int! tid)
        (bb/put-long! (codec/descending-long ^long start-t))
        bb/flip!
        bs/from-byte-buffer!)))

(defn type-history
  "Returns a reducible collection of all historic resource handles with type
  `tid` of the database with the point in time `t` between `start-t` (inclusive)
  and `start-id` (optional, inclusive)."
  [snapshot tid t start-t start-id]
  (i/prefix-entries
   snapshot :type-as-of-index
   (keep (decoder tid t))
   codec/tid-size (start-key tid start-t start-id)))

(defn- delete-entry! [kb]
  (bb/set-position! kb 0)
  (let [key (byte-array (bb/limit kb))]
    (bb/copy-into-byte-array! kb key)
    [:type-as-of-index key]))

(defn- prune-rf [n]
  (fn [ret {:keys [idx delete-entry] [tid t id] :key}]
    (if (= idx n)
      (reduced (assoc ret :next {:tid tid :t t :id id}))
      (cond-> (update ret :num-entries-processed inc)
        delete-entry
        (update :delete-entries conj delete-entry)))))

(defn- prune-key! [kb]
  [(bb/get-int! kb)
   (codec/descending-long (bb/get-long! kb))
   (bs/from-byte-buffer! kb (bb/remaining kb))])

(defn- prune-xf [t]
  (map-indexed
   (fn [idx [kb vb]]
     (bb/set-position! vb (+ hash/size codec/state-size))
     (cond->
      {:idx idx :key (prune-key! kb)}
       (rh/purged!? vb t)
       (assoc :delete-entry (delete-entry! kb))))))

(defn prune
  "Scans the TypeAsOf index for entries which were purged at or before `t`.

  Processes at most `n` entries and optionally starts at the entry with
  `start-tid`, `start-t` and `start-id`.

  Returns a map with :delete-entries and :next where :delete-entries is a
  vector of all index entries to delete and :next is a map of :tid, :t and :id
  of the index entry to start with in the next iteration if necessary."
  ([snapshot n t]
   (reduce
    (prune-rf n)
    {:delete-entries [] :num-entries-processed 0}
    (i/entries snapshot :type-as-of-index (prune-xf t))))
  ([snapshot n t start-tid start-t start-id]
   (reduce
    (prune-rf n)
    {:delete-entries [] :num-entries-processed 0}
    (i/entries snapshot :type-as-of-index (prune-xf t)
               (start-key start-tid start-t start-id)))))
