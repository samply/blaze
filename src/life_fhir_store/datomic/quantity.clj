(ns life-fhir-store.datomic.quantity
  "FHIR Quantity serialization.

  Use `read` and `write` functions."
  (:import
    [clojure.lang PersistentArrayMap]
    [java.nio ByteBuffer])
  (:refer-clojure :exclude [read]))


(defprotocol Write
  (write [this]))


(extend-protocol Write
  PersistentArrayMap
  (write [{:keys [value]}]
    (write value))

  Double
  (write [this]
    (-> (doto (ByteBuffer/allocate 9)
          (.put (byte 0))
          (.putDouble this))
        (.array))))


(defn read [bytes]
  (let [bb (ByteBuffer/wrap bytes)]
    (case (.get bb)
      0
      (.getDouble bb))))


(comment
  (let [bytes (write 1.0)]
    (criterium.core/quick-bench (read bytes)))

  (read (write 1.0))

  )
