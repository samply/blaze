(ns blaze.db.impl.index.util
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.db.impl.codec :as codec]))

(defn id-reader
  "Closes over a shared byte array for id decoding, because the String
  constructor creates a copy of the id bytes anyway. Can only be used from one
  thread."
  []
  (let [ib (byte-array codec/max-id-size)]
    (fn read-id [kb]
      (let [id-size (bb/remaining kb)]
        (bb/copy-into-byte-array! kb ib 0 id-size)
        (codec/id ib 0 id-size)))))

(defmacro read-t!
  "Reads a `t` value (descending-long encoded) from `byte-buffer`, optionally at
  the absolute `index`."
  ([byte-buffer]
   `(codec/descending-long (bb/get-long! ~byte-buffer)))
  ([byte-buffer index]
   `(codec/descending-long (bb/get-long! ~byte-buffer ~index))))
