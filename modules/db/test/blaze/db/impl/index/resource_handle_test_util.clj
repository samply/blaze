(ns blaze.db.impl.index.resource-handle-test-util
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.fhir.hash :as hash]))

(defn resource-handle
  "Creates a resource handle of `type` with `id`, `t` and `hash`.

  The hash defaults to the hash of a minimal resource with `type` and `id`."
  ([type id t]
   (resource-handle type id t (hash/generate {:fhir/type (keyword "fhir" type) :id id})))
  ([type id t hash]
   (rh/resource-handle! (codec/tid type) id t 0
                        (-> (bb/allocate 40)
                            (bb/put-byte-array! ^bytes (hash/to-byte-array hash))
                            (bb/put-long! 0)
                            bb/flip!))))
