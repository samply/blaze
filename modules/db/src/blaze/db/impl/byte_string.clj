(ns blaze.db.impl.byte-string
  (:import
    [com.google.common.io BaseEncoding]
    [com.google.protobuf ByteString]
    [java.io Writer]
    [java.nio ByteBuffer])
  (:refer-clojure :exclude [concat nth subs < <= > >=]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn byte-string? [x]
  (instance? ByteString x))


(defn from-bytes [bs]
  (ByteString/copyFrom ^bytes bs))


(defn from-byte-buffer
  "Returns the remaining bytes from `byte-buffer` as byte string."
  [byte-buffer]
  (ByteString/copyFrom ^ByteBuffer byte-buffer))


(defn from-hex [s]
  (ByteString/copyFrom (.decode (BaseEncoding/base16) s)))


(defn nth [bs index]
  (.byteAt ^ByteString bs index))


(defn size [bs]
  (.size ^ByteString bs))


(defn subs
  ([bs start]
   (.substring ^ByteString bs start))
  ([bs start end]
   (.substring ^ByteString bs start end)))


(defn concat [a b]
  (.concat ^ByteString a b))


(defn starts-with?
  "Test whether `byte-string` starts with `prefix`."
  [byte-string prefix]
  (.startsWith ^ByteString byte-string prefix))


(defn < [a b]
  (neg? (.compare (ByteString/unsignedLexicographicalComparator) a b)))


(defn <= [a b]
  (clojure.core/<= (.compare (ByteString/unsignedLexicographicalComparator) a b) 0))


(defn > [a b]
  (pos? (.compare (ByteString/unsignedLexicographicalComparator) a b)))


(defn >= [a b]
  (clojure.core/>= (.compare (ByteString/unsignedLexicographicalComparator) a b) 0))


(defn hex [bs]
  (.encode (BaseEncoding/base16) (.toByteArray ^ByteString bs)))


(defn to-byte-array [bs]
  (.toByteArray ^ByteString bs))


(defn as-read-only-byte-buffer [bs]
  (.asReadOnlyByteBuffer ^ByteString bs))


(defmethod print-method ByteString [^ByteString bs ^Writer w]
  (.write w "#google/byte-string\"")
  (.write w ^String (hex bs))
  (.write w "\""))


(defmethod print-dup ByteString [^ByteString bs ^Writer w]
  (.write w "#=(com.google.protobuf.ByteString/copyFrom ")
  (print-dup (.toByteArray bs) w)
  (.write w ")"))
