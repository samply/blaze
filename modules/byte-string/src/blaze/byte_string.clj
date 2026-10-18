(ns blaze.byte-string
  (:refer-clojure :exclude [< <= > concat empty nth subs])
  (:require
   [clojure.string :as str])
  (:import
   [blaze ByteString]
   [clojure.lang IObj]
   [java.io Writer]
   [java.nio ByteBuffer]
   [java.nio.charset StandardCharsets]
   [java.util HexFormat]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def ^:private ^HexFormat hex-format
  (.withUpperCase (HexFormat/of)))

(defn byte-string? [x]
  (instance? ByteString x))

(def empty
  ByteString/EMPTY)

(defn- tag [x tag]
  (cond-> x (instance? IObj x) (with-meta {:tag tag})))

(defn from-byte-array
  {:inline
   (fn [bs]
     `(ByteString/copyFrom ~(tag bs 'bytes)))}
  [bs]
  (ByteString/copyFrom ^bytes bs))

(defn from-utf8-string
  {:inline
   (fn [s]
     `(ByteString/copyFrom ~(tag s `String) StandardCharsets/UTF_8))}
  [s]
  (ByteString/copyFrom ^String s StandardCharsets/UTF_8))

(defn from-byte-buffer!
  "Returns the remaining or `size` bytes from `byte-buffer` as byte string.

  Increments the position of `byte-buffer` by `size`.

  Throws IndexOutOfBoundsException if `(< (remaining byte-buffer) size)`."
  {:inline
   (fn
     ([byte-buffer]
      `(ByteString/copyFrom ~(tag byte-buffer `ByteBuffer)))
     ([byte-buffer size]
      `(ByteString/copyFrom ~(tag byte-buffer `ByteBuffer) (int ~size))))}
  ([byte-buffer]
   (ByteString/copyFrom ^ByteBuffer byte-buffer))
  ([byte-buffer size]
   (ByteString/copyFrom ^ByteBuffer byte-buffer (int size))))

(defn from-hex [s]
  (ByteString/copyFrom (.parseHex hex-format ^String s)))

(defn nth
  "Returns the byte at `index` from `bs`."
  {:inline
   (fn [bs index]
     `(.byteAt ~(tag bs `ByteString) (int ~index)))}
  [bs index]
  (.byteAt ^ByteString bs index))

(defn get-int
  "Returns the int composed of the four bytes starting at `index` of `bs` in
  big-endian byte order."
  {:inline
   (fn [bs index]
     `(.intAt ~(tag bs `ByteString) (int ~index)))}
  [bs index]
  (.intAt ^ByteString bs index))

(defn size
  {:inline (fn [bs] `(.size ~(tag bs `ByteString)))}
  [bs]
  (.size ^ByteString bs))

(defn subs
  {:inline
   (fn
     ([bs start]
      `(.substring ~(tag bs `ByteString) (int ~start)))
     ([bs start end]
      `(.substring ~(tag bs `ByteString) (int ~start) (int ~end))))}
  ([bs start]
   (.substring ^ByteString bs start))
  ([bs start end]
   (.substring ^ByteString bs start end)))

(defn concat
  "Concatenates the byte strings `a` and `b`."
  {:inline
   (fn [a b]
     `(.concat ~(tag a `ByteString) ~b))}
  [a b]
  (.concat ^ByteString a b))

(defn < [a b]
  (neg? (.compareTo ^ByteString a b)))

(defn <=
  ([a b]
   (clojure.core/<= (.compareTo ^ByteString a b) 0))
  ([a b c]
   (and (<= a b) (<= b c))))

(defn > [a b]
  (pos? (.compareTo ^ByteString a b)))

(defn to-byte-array
  {:inline (fn [bs] `(.toByteArray ~(tag bs `ByteString)))}
  [bs]
  (.toByteArray ^ByteString bs))

(defn to-string-iso-8859-1
  "Decodes the bytes of `bs` from `start` to the end as ISO-8859-1 string."
  {:inline
   (fn [bs start]
     `(.toString ~(tag bs `ByteString) (int ~start) StandardCharsets/ISO_8859_1))}
  [bs start]
  (.toString ^ByteString bs (int start) StandardCharsets/ISO_8859_1))

(defn as-read-only-byte-buffer [bs]
  (.asReadOnlyByteBuffer ^ByteString bs))

(defmethod print-method ByteString [^ByteString bs ^Writer w]
  (.write w "#blaze/byte-string\"")
  (.write w ^String (.hex bs))
  (.write w "\""))

(defmethod print-dup byte/1 [^bytes bytes ^Writer w]
  (.write w "#=(byte-array [")
  (.write w ^String (str/join " " (map int (vec bytes))))
  (.write w "])"))

(defmethod print-dup ByteString [^ByteString bs ^Writer w]
  (.write w "#=(blaze.ByteString/copyFrom ")
  (print-dup (.toByteArray bs) w)
  (.write w ")"))
