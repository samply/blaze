(ns blaze.db.impl.index.type-stats-test-util
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec]))


(set! *unchecked-math* :warn-on-boxed)


(defn decode-key-human
  ([] (bb/allocate-direct (+ codec/tid-size codec/t-size)))
  ([buf]
   {:type (codec/tid->type (bb/get-int! buf))
    :t (codec/descending-long (bb/get-long! buf))}))


(defn decode-value-human
  ([] (bb/allocate-direct (+ Long/BYTES Long/BYTES)))
  ([buf]
   {:total (bb/get-long! buf)
    :num-changes (bb/get-long! buf)}))


(defn decode-index-entry [[k v]]
  [(decode-key-human (bb/wrap k))
   (decode-value-human (bb/wrap v))])
