(ns blaze.db.impl.index.reverse-reference
  "Functions for accessing the ReverseReference index."
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.byte-string :as bs]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.iterators :as i]))


(defn encode-key
  "Encodes the key of the ReverseReference index from `dst-tid`, `dst-id`,
  `src-tid` and `src-id`."
  [dst-tid dst-id src-tid src-id]
  (-> (bb/allocate (+ codec/tid-size (bs/size dst-id) codec/tid-size (bs/size src-id)))
      (bb/put-int! dst-tid)
      (bb/put-byte-string! dst-id)
      (bb/put-byte! 0)
      (bb/put-int! src-tid)
      (bb/put-byte-string! src-id)
      bb/array))


(def index-entry []
  ;; TODO: continue here with using encode-key
  )


(defn- encode-prefix
  [dst-tid dst-id]
  (-> (bb/allocate (+ codec/tid-size (bs/size dst-id) ))
      (bb/put-int! dst-tid)
      (bb/put-byte-string! dst-id)
      bb/flip!
      bs/from-byte-buffer!))


(defn decode-key [kb]
  (let [dst-tid (bb/get-int! kb)
        dst-id-size (long (bb/size-up-to-null kb))
        dst-id (bs/from-byte-buffer! kb dst-id-size)
        src-tid (bb/get-int! kb)
        src-id (bs/from-byte-buffer! kb)]
    [dst-tid dst-id src-tid src-id]))


(defn- decode-key-src [kb]
  ;; TODO: would be more efficient not to read the src-tid and src-id and trash it
  (subvec (decode-key kb) 2))


(defn source-tid-id
  "Returns a reducible collection of all `[tid id]` tuples referencing the
  resource with `dst-tid` and `dst-id`."
  [rri dst-tid dst-id]
  (let [prefix (encode-prefix dst-tid dst-id)]
    (i/prefix-keys! rri prefix decode-key-src prefix)))
