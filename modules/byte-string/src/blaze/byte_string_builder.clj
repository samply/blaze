(ns blaze.byte-string-builder
  (:import
   [blaze ByteString ByteString$Builder]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn allocate
  "Allocates a new ByteString builder with the given fixed `capacity`.

  The builder writes primitive values and ByteStrings into a single backing
  array in big-endian byte order. Use [[build]] to obtain an immutable
  ByteString or [[to-bytes]] to obtain the raw byte array; both are zero-copy."
  {:inline (fn [capacity] `(ByteString$Builder. (int ~capacity)))}
  [capacity]
  (ByteString$Builder. (int capacity)))

(defn put-byte!
  "Puts the byte `x` into `builder` at the current position."
  {:inline
   (fn [builder x]
     `(.putByte ~(vary-meta builder assoc :tag `ByteString$Builder) (byte ~x)))}
  [builder x]
  (.putByte ^ByteString$Builder builder (byte x)))

(defn put-short!
  "Puts the short `x` into `builder` at the current position."
  {:inline
   (fn [builder x]
     `(.putShort ~(vary-meta builder assoc :tag `ByteString$Builder) (short ~x)))}
  [builder x]
  (.putShort ^ByteString$Builder builder (short x)))

(defn put-int!
  "Puts the int `x` into `builder` at the current position."
  {:inline
   (fn [builder x]
     `(.putInt ~(vary-meta builder assoc :tag `ByteString$Builder) (int ~x)))}
  [builder x]
  (.putInt ^ByteString$Builder builder (int x)))

(defn put-long!
  "Puts the long `x` into `builder` at the current position."
  {:inline
   (fn [builder x]
     `(.putLong ~(vary-meta builder assoc :tag `ByteString$Builder) (long ~x)))}
  [builder x]
  (.putLong ^ByteString$Builder builder (long x)))

(defn put-byte-array!
  "Copies all bytes of `byte-array` into `builder` at the current position."
  {:inline
   (fn [builder byte-array]
     `(.putByteArray ~(vary-meta builder assoc :tag `ByteString$Builder)
                     ~byte-array))}
  [builder byte-array]
  (.putByteArray ^ByteString$Builder builder ^bytes byte-array))

(defn put-byte-string!
  "Copies all bytes of `byte-string` into `builder` at the current position."
  {:inline
   (fn [builder byte-string]
     `(.putByteString ~(vary-meta builder assoc :tag `ByteString$Builder)
                      ~(vary-meta byte-string assoc :tag `ByteString)))}
  [builder byte-string]
  (.putByteString ^ByteString$Builder builder ^ByteString byte-string))

(defn put-null-terminated-byte-string!
  "Copies all bytes of `byte-string` into `builder` and appends a null byte."
  {:inline
   (fn [builder byte-string]
     `(.putNullTerminatedByteString
       ~(vary-meta builder assoc :tag `ByteString$Builder)
       ~(vary-meta byte-string assoc :tag `ByteString)))}
  [builder byte-string]
  (.putNullTerminatedByteString ^ByteString$Builder builder ^ByteString byte-string))

(defn build
  "Returns a ByteString backed by `builder`'s underlying array.

  Throws if the builder's position does not equal its capacity. After this
  call the builder must not be used further."
  {:inline
   (fn [builder]
     `(.build ~(vary-meta builder assoc :tag `ByteString$Builder)))}
  [builder]
  (.build ^ByteString$Builder builder))

(defn to-bytes
  "Returns the byte array backing `builder`.

  Throws if the builder's position does not equal its capacity. After this
  call the builder must not be used further."
  {:inline
   (fn [builder]
     `(.toBytes ~(vary-meta builder assoc :tag `ByteString$Builder)))}
  [builder]
  (.toBytes ^ByteString$Builder builder))
