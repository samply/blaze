(ns blaze.db.impl.index.type-as-of-test-util
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.byte-string :as bs]
    [blaze.db.impl.codec :as codec]))


(set! *unchecked-math* :warn-on-boxed)


(defn decode-key [byte-array]
  (let [buf (bb/wrap byte-array)]
    {:type (codec/tid->type (bb/get-int! buf))
     :t (codec/descending-long (bb/get-long! buf))
     :id (codec/id-string (bs/from-byte-buffer! buf (bb/remaining buf)))}))
