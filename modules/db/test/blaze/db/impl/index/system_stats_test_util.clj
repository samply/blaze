(ns blaze.db.impl.index.system-stats-test-util
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec]))


(set! *unchecked-math* :warn-on-boxed)


(defn decode-key [byte-array]
  (let [buf (bb/wrap byte-array)]
    {:t (codec/descending-long (bb/get-5-byte-long! buf))}))


(defn decode-val [byte-array]
  (let [buf (bb/wrap byte-array)]
    {:total (bb/get-long! buf)
     :num-changes (bb/get-long! buf)}))
