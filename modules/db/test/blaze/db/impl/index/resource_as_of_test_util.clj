(ns blaze.db.impl.index.resource-as-of-test-util
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.db.impl.codec :as codec]))

(set! *unchecked-math* :warn-on-boxed)

(defn decode-key [byte-array]
  (let [buf (bb/wrap byte-array)
        tid (bb/get-int! buf)
        id-size (- (bb/remaining buf) codec/t-size)]
    {:type (codec/tid->type tid)
     :id (codec/id-string (bs/from-byte-buffer! buf id-size))
     :t (codec/descending-long (bb/get-long! buf))}))
