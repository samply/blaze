(ns blaze.db.impl.index.search-param-value-resource.impl
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec]))


(set! *unchecked-math* :warn-on-boxed)


(defn id-size
  {:inline
   (fn [buf]
     `(int (bb/get-byte! ~buf (unchecked-dec-int (unchecked-subtract-int (bb/limit ~buf) codec/hash-prefix-size)))))}
  [buf]
  (int (bb/get-byte! buf (unchecked-dec-int (unchecked-subtract-int (bb/limit buf) codec/hash-prefix-size)))))
