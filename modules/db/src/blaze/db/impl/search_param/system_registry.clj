(ns blaze.db.impl.search-param.system-registry
  "A registry that assigns every system URI a 3-byte wide identifier."
  (:require
   [blaze.anomaly :as ba]
   [blaze.byte-string :as bs]
   [blaze.db.impl.search-param.registry.util :as u]
   [blaze.db.kv :as kv])
  (:import
   [com.google.common.primitives Ints]
   [java.util Arrays]))

(set! *warn-on-reflection* true)

(def ^:const null-id #blaze/byte-string"000000")

(defn- out-of-range-msg [id]
  (format "The system identifier `%d` is out of range. The range goes from 1 to 2^24 (exclusive)."
          id))

(defn encode-id [id]
  (if (< 0 id (bit-shift-left 1 24))
    (Arrays/copyOfRange (Ints/toByteArray id) 1 4)
    (ba/conflict (out-of-range-msg id))))

(defn decode-id [^bytes bytes]
  (Ints/fromBytes 0 (aget bytes 0) (aget bytes 1) (aget bytes 2)))

(defn- id-of-system* [kv-store system]
  (if-let [id (kv/get kv-store :system (u/encode-value system))]
    (bs/from-byte-array id)
    (u/register-value! kv-store :system encode-id decode-id system)))

(defn id-of
  "Returns the identifier of `system` as stored in `kv-store`.

  Stores a new mapping of `system` to an identifier if not already known.

  Nil is mapped to a byte string of three null bytes. Returns an anomaly if all
  2^24 identifiers are exhausted."
  [kv-store system]
  (if (nil? system) null-id (id-of-system* kv-store system)))
