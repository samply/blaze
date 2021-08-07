(ns blaze.db.impl.index.system-stats-test-util
  (:require
    [blaze.db.impl.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn decode-key-human
  ([] (bb/allocate-direct codec/t-size))
  ([buf]
   {:t (codec/descending-long (bb/get-long! buf))}))


(defn decode-value-human
  ([] (bb/allocate-direct (+ Long/BYTES Long/BYTES)))
  ([buf]
   {:total (bb/get-long! buf)
    :num-changes (bb/get-long! buf)}))


(defn decode-index-entry [[k v]]
  [(decode-key-human (bb/wrap k))
   (decode-value-human (bb/wrap v))])
