(ns blaze.db.impl.index.search-param-value-resource.impl
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.fhir.hash :as hash]))


(set! *unchecked-math* :warn-on-boxed)


(defn id-size
  {:inline
   (fn [buf]
     `(int (bb/get-byte! ~buf (unchecked-dec-int (unchecked-subtract-int (bb/limit ~buf) hash/prefix-size)))))}
  [buf]
  (int (bb/get-byte! buf (unchecked-dec-int (unchecked-subtract-int (bb/limit buf) hash/prefix-size)))))
