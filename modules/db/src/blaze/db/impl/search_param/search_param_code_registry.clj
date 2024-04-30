(ns blaze.db.impl.search-param.search-param-code-registry
  "A registry that assigns every search param code a 3-byte wide identifier."
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
  (format "The search-param-code identifier `%d` is out of range. The range goes from 1 to 2^24 (exclusive)."
          id))

(defn encode-id [id]
  (if (< 0 id (bit-shift-left 1 24))
    (Arrays/copyOfRange (Ints/toByteArray id) 1 4)
    (ba/conflict (out-of-range-msg id))))

(defn decode-id [^bytes bytes]
  (Ints/fromBytes 0 (aget bytes 0) (aget bytes 1) (aget bytes 2)))

(defn- id-of-search-param-code* [kv-store value]
  (if-let [id (kv/get kv-store :search-param-code (u/encode-value value))]
    (bs/from-byte-array id)
    (u/register-value! kv-store :search-param-code encode-id decode-id value)))

(defn id-of
  "Returns the identifier of `search-param-code` as stored in `kv-store`.

  Stores a new mapping of `search-param-code` to an identifier if not already
  known.

  Nil is mapped to a byte string of three null bytes. Returns an anomaly if all
  2^24 identifiers are exhausted."
  [kv-store search-param-code]
  (if (nil? search-param-code)
    null-id
    (id-of-search-param-code* kv-store search-param-code)))
