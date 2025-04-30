(ns blaze.db.impl.index.system-as-of
  "Functions for accessing the SystemAsOf index."
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.iterators :as i]
   [blaze.fhir.hash :as hash])
  (:import
   [com.google.common.primitives Longs]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def ^:private ^:const ^long t-tid-size
  (+ codec/t-size codec/tid-size))

(defn- decoder
  "Returns a function which decodes an resource handle out of a key and a value
  byte buffers from the SystemAsOf index if not purged at `base-t`.

  Closes over a shared byte array for id decoding, because the String
  constructor creates a copy of the id bytes anyway. Can only be used from one
  thread.

  Both byte buffers are changed during decoding and have to be reset accordingly
  after decoding."
  [base-t]
  (let [ib (byte-array codec/max-id-size)]
    (fn [[kb vb]]
      (let [t (codec/descending-long (bb/get-long! kb))]
        (rh/resource-handle!
         (bb/get-int! kb)
         (let [id-size (bb/remaining kb)]
           (bb/copy-into-byte-array! kb ib 0 id-size)
           (codec/id ib 0 id-size))
         t base-t vb)))))

(defn encode-key
  "Encodes the key of the SystemAsOf index from `t`, `tid` and `id`."
  [t tid id]
  (-> (bb/allocate (unchecked-add-int t-tid-size (bs/size id)))
      (bb/put-long! (codec/descending-long ^long t))
      (bb/put-int! tid)
      (bb/put-byte-string! id)
      bb/array))

(defn- start-key [start-t start-tid start-id]
  (cond
    start-id
    (bs/from-byte-array (encode-key start-t start-tid start-id))

    start-tid
    (-> (bb/allocate t-tid-size)
        (bb/put-long! (codec/descending-long ^long start-t))
        (bb/put-int! start-tid)
        bb/flip!
        bs/from-byte-buffer!)

    :else
    (-> (Longs/toByteArray (codec/descending-long ^long start-t))
        bs/from-byte-array)))

(defn system-history
  "Returns a reducible collection of all historic resource handles of the
  database with the point in time `t` between `start-t` (inclusive), `start-tid`
  (optional, inclusive) and `start-id` (optional, inclusive)."
  [snapshot t start-t start-tid start-id]
  (i/entries
   snapshot :system-as-of-index
   (keep (decoder t))
   (start-key start-t start-tid start-id)))

(defn changes
  "Returns a reducible collection of all resource handles changed at `t`."
  [snapshot t]
  (i/prefix-entries snapshot :system-as-of-index (keep (decoder t)) codec/t-size
                    (start-key t nil nil)))

(defn- delete-entry! [kb]
  (bb/set-position! kb 0)
  (let [key (byte-array (bb/limit kb))]
    (bb/copy-into-byte-array! kb key)
    [:system-as-of-index key]))

(defn- prune-rf [n]
  (fn [ret {:keys [idx delete-entry] [t tid id] :key}]
    (if (= idx n)
      (reduced (assoc ret :next {:t t  :tid tid :id id}))
      (cond-> (update ret :num-entries-processed inc)
        delete-entry
        (update :delete-entries conj delete-entry)))))

(defn- prune-key! [kb]
  [(codec/descending-long (bb/get-long! kb))
   (bb/get-int! kb)
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
  "Scans the SystemAsOf index for entries which were purged at or before `t`.

  Processes at most `n` entries and optionally starts at the entry with
  `start-t`, `start-tid` and `start-id`.

  Returns a map with :delete-entries and :next where :delete-entries is a
  vector of all index entries to delete and :next is a map of :tid, :t and :id
  of the index entry to start with in the next iteration if necessary."
  ([snapshot n t]
   (reduce
    (prune-rf n)
    {:delete-entries [] :num-entries-processed 0}
    (i/entries snapshot :system-as-of-index (prune-xf t))))
  ([snapshot n t start-t start-tid start-id]
   (reduce
    (prune-rf n)
    {:delete-entries [] :num-entries-processed 0}
    (i/entries snapshot :system-as-of-index (prune-xf t)
               (start-key start-t start-tid start-id)))))
