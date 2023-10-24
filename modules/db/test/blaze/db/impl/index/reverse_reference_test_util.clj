(ns blaze.db.impl.index.reverse-reference-test-util
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.reverse-reference :as rr]))


(defn decode-key [byte-array]
  (let [[dst-tid dst-id src-tid src-id] (rr/decode-key (bb/wrap byte-array))]
    {:dst-type (codec/tid->type dst-tid)
     :dst-id (codec/id-string dst-id)
     :src-type (codec/tid->type src-tid)
     :src-id (codec/id-string src-id)}))
