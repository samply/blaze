(ns blaze.fhir.hash
  (:require
   [blaze.byte-buffer :as bb])
  (:import
   [blaze.fhir Hash]
   [blaze.fhir.spec.type Base]
   [com.fasterxml.jackson.core JsonGenerator]
   [com.fasterxml.jackson.databind.module SimpleModule]
   [com.fasterxml.jackson.databind.ser.std StdSerializer]
   [com.google.common.hash Hashing]
   [java.io Writer]))

(set! *warn-on-reflection* true)

(def ^:const ^long size 32)
(def ^:const ^long prefix-size Integer/BYTES)

(def deleted-hash
  "The hash of a deleted version of a resource."
  Hash/DELETED)

(defn hash? [x]
  (instance? Hash x))

(defn from-byte-buffer! [byte-buffer]
  (Hash/fromByteBuffer byte-buffer))

(defn from-hex [s]
  (Hash/fromHex s))

(defn into-byte-buffer! [byte-buffer hash]
  (.copyTo ^Hash hash byte-buffer)
  byte-buffer)

(defn prefix-into-byte-buffer!
  [byte-buffer hash]
  (bb/put-int! byte-buffer (.prefix ^Hash hash)))

(defn to-byte-array [hash]
  (-> (bb/allocate size)
      (into-byte-buffer! hash)
      (bb/array)))

(defn prefix
  "Returns the first 4 bytes of `hash`."
  [hash]
  (bit-and (.prefix ^Hash hash) 0xFFFFFFFF))

(defn prefix-from-byte-buffer!
  [byte-buffer]
  (bit-and (bb/get-int! byte-buffer) 0xFFFFFFFF))

(defn prefix-from-hex [s]
  (Long/parseLong s 16))

(def ^:private serializer
  (proxy [StdSerializer] [Hash]
    (serialize [^Hash hash ^JsonGenerator gen _]
      (.writeBinary gen (to-byte-array hash)))))

(def object-mapper-module
  (doto (SimpleModule. "Hash")
    (.addSerializer Hash serializer)))

(defn generate
  "Calculates a SHA256 hash for `resource`.

  The hash need to be cryptographic because otherwise it would be possible to
  introduce a resource into Blaze which has the same hash as the target
  resource, overwriting it."
  ^Hash [resource]
  (let [hasher (.newHasher (Hashing/sha256))]
    (Base/hashInto resource hasher)
    (from-byte-buffer! (bb/wrap (.asBytes (.hash hasher))))))

(defmethod print-method Hash [^Hash hash ^Writer w]
  (.write w "#blaze/hash\"")
  (.write w (.toString hash))
  (.write w "\""))

(defmethod print-dup Hash [^Hash hash ^Writer w]
  (.write w "#=(blaze.fhir.Hash/fromHex ")
  (print-dup (.toString hash) w)
  (.write w ")"))
