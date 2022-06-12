(ns blaze.db.impl.index.resource-id-test-util
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec])
  (:import
    [com.google.common.primitives Longs]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn decode-key [byte-array]
  (let [buf (bb/wrap byte-array)]
    {:type (codec/tid->type (bb/get-int! buf))
     :id (codec/id-from-byte-buffer buf)}))


(defn decode-val [byte-array]
  {:did (Longs/fromByteArray byte-array)})
