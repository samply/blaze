(ns blaze.db.impl.index.util
  (:require
   [blaze.byte-buffer :as bb]))

(defn ensure-size [buf size]
  (if (< (bb/capacity buf) (long size))
    (bb/allocate (max (long size) (bit-shift-left (bb/capacity buf) 1)))
    buf))
