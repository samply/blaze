(ns blaze.db.impl.codec
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.byte-string-builder :as bsb]
   [blaze.fhir.spec.type.system])
  (:import
   [blaze.db.impl.codec ResourceTypes]
   [com.google.common.hash HashFunction Hashing]
   [java.nio.charset StandardCharsets]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

;; ---- Sizes of Byte Arrays --------------------------------------------------

(def ^:const ^long c-hash-size Integer/BYTES)
(def ^:const ^long v-hash-size Integer/BYTES)
(def ^:const ^long tid-size Integer/BYTES)
(def ^:const ^long t-size Long/BYTES)
(def ^:const ^long state-size Long/BYTES)
(def ^:const ^long max-id-size 64)

;; ---- Type Identifier -------------------------------------------------------

(defn tid
  "Internal type identifier.

  Returns an integer."
  [type]
  (ResourceTypes/tid type))

(defn tid->type
  "Returns the FHIR resource type of `tid` or nil if `tid` is unknown."
  [tid]
  (ResourceTypes/name tid))

(def all-types
  "A vector of all FHIR resource types in no particular order."
  (ResourceTypes/names))

;; ---- Identifier Functions --------------------------------------------------

(defn valid-id?
  "Returns true if `s` is a valid resource id."
  [s]
  (.matches (re-matcher #"[A-Za-z0-9\-\.]{1,64}" s)))

(defn id-byte-string
  {:inline (fn [id] `(bs/from-iso-8859-1-string ~id))}
  [id]
  (bs/from-iso-8859-1-string id))

(defn id-string
  "Converts the byte-string representation of a resource id into it's string
  representation."
  {:inline
   (fn [id-byte-string]
     `(bs/to-string-iso-8859-1 ~id-byte-string))}
  [id-byte-string]
  (bs/to-string-iso-8859-1 id-byte-string))

(defn id
  "Creates an resource id as String from the byte array `id-bytes`."
  {:inline
   (fn [id-bytes offset length]
     `(String. ~id-bytes ~offset ~length StandardCharsets/ISO_8859_1))}
  [^bytes id-bytes ^long offset ^long length]
  (String. id-bytes offset length StandardCharsets/ISO_8859_1))

;; ---- Key Functions ---------------------------------------------------------

(defn descending-long
  "Converts positive longs so that they decrease from 0xFFFFFFFFFFFFFF.

  This function is used for the point in time `t` value, which is always ordered
  descending in indices. The value 0xFFFFFFFFFFFFFF has 7 bytes, so the first
  byte will be always the zero byte. This comes handy in indices, because the
  zero byte terminates ordering of index segments preceding the `t` value.

  7 bytes are also plenty for the `t` value because with 5 bytes one could carry
  out a transaction every millisecond for 20 years."
  {:inline
   (fn [l]
     `(bit-and (bit-not (unchecked-long ~l)) 0xFFFFFFFFFFFFFF))}
  [l]
  (bit-and (bit-not (unchecked-long l)) 0xFFFFFFFFFFFFFF))

(defn c-hash [code]
  (-> (.hashString ^HashFunction (Hashing/murmur3_32_fixed) code
                   StandardCharsets/UTF_8)
      (.asInt)))

(def c-hash->code
  (into
   {}
   (map (fn [code] [(c-hash code) code]))
   ["_id"
    "_lastUpdated"
    "_profile"
    "_profile:below"
    "active"
    "actor"
    "address"
    "birthdate"
    "bodysite"
    "category"
    "class"
    "code"
    "code-value-quantity"
    "combo-code"
    "combo-code-value-quantity"
    "combo-value-quantity"
    "context-quantity"
    "date"
    "death-date"
    "deceased"
    "description"
    "identifier"
    "issued"
    "item"
    "item:identifier"
    "onset-date"
    "patient"
    "phonetic"
    "priority"
    "probability"
    "rank"
    "series"
    "status"
    "subject"
    "system:below"
    "url"
    "url:below"
    "value-quantity"
    "variant-start"
    "version"]))

(defn v-hash [value]
  (-> (.hashString ^HashFunction (Hashing/murmur3_32_fixed) value
                   StandardCharsets/UTF_8)
      (.asBytes)
      bs/from-byte-array))

(defn tid-id
  "Returns a byte string with `tid` followed by `id`."
  [tid id]
  (-> (bsb/allocate (unchecked-add-int tid-size (bs/size id)))
      (bsb/put-int! tid)
      (bsb/put-byte-string! id)
      bsb/build))

(defn tid-byte-string
  "Returns a byte string with `tid`."
  [tid]
  (-> (bsb/allocate tid-size)
      (bsb/put-int! tid)
      bsb/build))

(defn string
  "Returns a lexicographically sortable byte string of the `string` value."
  {:inline
   (fn [string]
     `(bs/from-utf8-string ~string))}
  [string]
  (bs/from-utf8-string string))

(defprotocol NumberBytes
  (-number [number]))

(defn number
  "Converts the number in a lexicographically sortable byte string.

  The byte string has variable length."
  [number]
  (-number number))

;; See https://github.com/danburkert/bytekey/blob/6980b9e33281d875f03f4c9a953b93a384eac085/src/encoder.rs#L258
;; And https://cornerwings.github.io/2019/10/lexical-sorting/
(extend-protocol NumberBytes
  BigDecimal
  (-number [val]
   ;; Truncate at two digits after the decimal point
    (-number (.longValue (.scaleByPowerOfTen val 2))))

  Integer
  (-number [val]
    (-number (long val)))

  Long
  (-number [val]
    (let [mask (bit-shift-right (.longValue val) 63)
          val (- (abs (.longValue val)) (bit-and 1 mask))]
      (condp > val
        (bit-shift-left 1 3)
        (-> (bsb/allocate 1)
            (bsb/put-byte! (bit-xor (bit-or val (bit-shift-left 0x10 3)) mask))
            bsb/build)

        (bit-shift-left 1 11)
        (-> (bsb/allocate 2)
            (bsb/put-short! (bit-xor (bit-or val (bit-shift-left 0x11 11)) mask))
            bsb/build)

        (bit-shift-left 1 19)
        (let [masked (bit-xor (bit-or val (bit-shift-left 0x12 19)) mask)]
          (-> (bsb/allocate 3)
              (bsb/put-byte! (bit-shift-right masked 16))
              (bsb/put-short! masked)
              bsb/build))

        (bit-shift-left 1 27)
        (-> (bsb/allocate 4)
            (bsb/put-int! (bit-xor (bit-or val (bit-shift-left 0x13 27)) mask))
            bsb/build)

        (bit-shift-left 1 35)
        (let [masked (bit-xor (bit-or val (bit-shift-left 0x14 35)) mask)]
          (-> (bsb/allocate 5)
              (bsb/put-byte! (bit-shift-right masked 32))
              (bsb/put-int! masked)
              bsb/build))

        (bit-shift-left 1 43)
        (let [masked (bit-xor (bit-or val (bit-shift-left 0x15 43)) mask)]
          (-> (bsb/allocate 6)
              (bsb/put-short! (bit-shift-right masked 32))
              (bsb/put-int! masked)
              bsb/build))

        (bit-shift-left 1 51)
        (let [masked (bit-xor (bit-or val (bit-shift-left 0x16 51)) mask)]
          (-> (bsb/allocate 7)
              (bsb/put-byte! (bit-shift-right masked 48))
              (bsb/put-short! (bit-shift-right masked 32))
              (bsb/put-int! masked)
              bsb/build))

        (bit-shift-left 1 59)
        (-> (bsb/allocate 8)
            (bsb/put-long! (bit-xor (bit-or val (bit-shift-left 0x17 59)) mask))
            bsb/build)

        (-> (bsb/allocate 9)
            (bsb/put-byte! (bit-xor (bit-shift-left 0x18 3) mask))
            (bsb/put-long! (bit-xor val mask))
            bsb/build)))))

(defn decode-number [byte-string]
  (let [bb (bs/as-read-only-byte-buffer byte-string)
        header (bit-and (long (bb/get-byte! bb)) 0xFF)
        mask (bit-and (bit-shift-right (unchecked-byte (bit-xor header 0x80)) 7) 0xFF)
        n (bit-and (bit-xor (bit-shift-right header 3) mask) 0x0F)]
    (loop [val (bit-shift-left (bit-and (bit-xor header mask) 0x07) (* 8 n))
           i 1]
      (if (<= i n)
        (let [byte (bit-and (long (bb/get-byte! bb)) 0xFF)]
          (recur
           (+ val (bit-shift-left (bit-xor byte mask) (* 8 (- n i))))
           (inc i)))
        (let [final-mask (bit-shift-right (bit-shift-left mask 63) 63)]
          (bit-xor val final-mask))))))

(defn quantity [unit value]
  (bs/concat (v-hash (or unit "")) (number value)))
