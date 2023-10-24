(ns blaze.byte-string
  (:refer-clojure :exclude [concat empty nth subs < <= > >=])
  (:import
    [com.google.common.io BaseEncoding]
    [com.google.protobuf ByteString]
    [com.fasterxml.jackson.core JsonGenerator]
    [com.fasterxml.jackson.databind.module SimpleModule]
    [com.fasterxml.jackson.databind.ser.std StdSerializer]
    [java.io Writer]
    [java.nio ByteBuffer]
    [java.nio.charset Charset]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn byte-string? [x]
  (instance? ByteString x))


(def empty
  ByteString/EMPTY)


(defn from-byte-array
  {:inline
   (fn [bs]
     `(ByteString/copyFrom ~(with-meta bs {:tag 'bytes})))}
  [bs]
  (ByteString/copyFrom ^bytes bs))


(defn from-utf8-string
  {:inline (fn [s] `(ByteString/copyFromUtf8 ~s))}
  [s]
  (ByteString/copyFromUtf8 s))


(defn from-string
  {:inline
   (fn [s charset]
     `(ByteString/copyFrom ~(if (symbol? s) (with-meta s {:tag `String}) s)
                           ~charset))}
  [s charset]
  (ByteString/copyFrom ^String s ^Charset charset))


(defn from-byte-buffer!
  "Returns all the remaining or `size` (optional) many bytes from `byte-buffer`
  as byte string."
  {:inline
   (fn
     ([byte-buffer]
      `(ByteString/copyFrom ~(with-meta byte-buffer {:tag `ByteBuffer})))
     ([byte-buffer size]
      `(ByteString/copyFrom ~(with-meta byte-buffer {:tag `ByteBuffer})
                            (int ~size))))}
  ([byte-buffer]
   (ByteString/copyFrom ^ByteBuffer byte-buffer))
  ([byte-buffer size]
   (ByteString/copyFrom ^ByteBuffer byte-buffer (int size))))


(defn from-hex [s]
  (ByteString/copyFrom (.decode (BaseEncoding/base16) s)))


(defn nth
  "Returns the byte at `index` from `bs`."
  {:inline
   (fn [bs index]
     `(.byteAt ^ByteString ~(with-meta bs {:tag `ByteString}) (int ~index)))}
  [bs index]
  (.byteAt ^ByteString bs index))


(defn size
  {:inline (fn [bs] `(.size ~(with-meta bs {:tag `ByteString})))}
  [bs]
  (.size ^ByteString bs))


(defn subs
  {:inline
   (fn
     ([bs start]
      `(.substring ~(with-meta bs {:tag `ByteString}) (int ~start)))
     ([bs start end]
      `(.substring ~(with-meta bs {:tag `ByteString}) (int ~start) (int ~end))))}
  ([bs start]
   (.substring ^ByteString bs start))
  ([bs start end]
   (.substring ^ByteString bs start end)))


(defn concat
  {:inline
   (fn [a b]
     `(.concat ~(with-meta a {:tag `ByteString}) ~b))}
  [a b]
  (.concat ^ByteString a b))


(defn < [a b]
  (neg? (.compare (ByteString/unsignedLexicographicalComparator) a b)))


(defn <=
  ([a b]
   (clojure.core/<= (.compare (ByteString/unsignedLexicographicalComparator) a b) 0))
  ([a b c]
   (and (<= a b) (<= b c))))


(defn > [a b]
  (pos? (.compare (ByteString/unsignedLexicographicalComparator) a b)))


(defn >= [a b]
  (clojure.core/>= (.compare (ByteString/unsignedLexicographicalComparator) a b) 0))


(defn hex
  "Returns an upper-case hexadecimal string representation of `bs`."
  [bs]
  (.encode (BaseEncoding/base16) (.toByteArray ^ByteString bs)))


(defn to-byte-array
  {:inline (fn [bs] `(.toByteArray ~(with-meta bs {:tag `ByteString})))}
  [bs]
  (.toByteArray ^ByteString bs))


(defn to-string
  {:inline
   (fn [bs charset] `(.toString ~(with-meta bs {:tag `ByteString}) ~charset))}
  [bs charset]
  (.toString ^ByteString bs ^Charset charset))


(defn as-read-only-byte-buffer [bs]
  (.asReadOnlyByteBuffer ^ByteString bs))


(def ^:private serializer
  (proxy [StdSerializer] [ByteString]
    (serialize [^ByteString bs ^JsonGenerator gen _]
      (.writeBinary gen (.toByteArray bs)))))


(def object-mapper-module
  (doto (SimpleModule. "ByteString")
    (.addSerializer ByteString serializer)))


(defmethod print-method ByteString [^ByteString bs ^Writer w]
  (.write w "#blaze/byte-string\"")
  (.write w ^String (hex bs))
  (.write w "\""))


(defmethod print-dup ByteString [^ByteString bs ^Writer w]
  (.write w "#=(com.google.protobuf.ByteString/copyFrom ")
  (print-dup (.toByteArray bs) w)
  (.write w ")"))
