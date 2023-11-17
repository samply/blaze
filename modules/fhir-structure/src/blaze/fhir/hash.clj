(ns blaze.fhir.hash
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.fhir.spec.type :as type])
  (:import
   [com.fasterxml.jackson.core JsonGenerator]
   [com.fasterxml.jackson.databind.module SimpleModule]
   [com.fasterxml.jackson.databind.ser.std StdSerializer]
   [com.google.common.hash Hashing]
   [com.google.common.io BaseEncoding]))

(set! *warn-on-reflection* true)

(def ^:const ^long size 32)
(def ^:const ^long prefix-size Integer/BYTES)

(definterface Prefix
  (^long prefix []))

(deftype Hash [^long l0 ^long l1 ^long l2 ^long l3]
  Prefix
  (prefix [_]
    (bit-and (unsigned-bit-shift-right l0 32) 0xFFFFFFFF))
  Object
  (equals [this x]
    (or (identical? this x)
        (and (instance? Hash x)
             (= l0 (.l0 ^Hash x))
             (= l1 (.l1 ^Hash x))
             (= l2 (.l2 ^Hash x))
             (= l3 (.l3 ^Hash x)))))
  (hashCode [_]
    (unchecked-int l0))
  (toString [_]
    (->> (-> (bb/allocate size)
             (bb/put-long! l0)
             (bb/put-long! l1)
             (bb/put-long! l2)
             (bb/put-long! l3)
             bb/array)
         (.encode (BaseEncoding/base16)))))

(def deleted-hash
  "The hash of a deleted version of a resource."
  (Hash. 0 0 0 0))

(defn hash? [x]
  (instance? Hash x))

(defn from-byte-buffer! [byte-buffer]
  (Hash. (bb/get-long! byte-buffer)
         (bb/get-long! byte-buffer)
         (bb/get-long! byte-buffer)
         (bb/get-long! byte-buffer)))

(defn from-hex [s]
  (from-byte-buffer! (bb/wrap (.decode (BaseEncoding/base16) s))))

(defn into-byte-buffer!
  {:inline
   (fn [byte-buffer hash]
     `(-> ~byte-buffer
          (bb/put-long! (.l0 ~(with-meta hash {:tag `Hash})))
          (bb/put-long! (.l1 ~(with-meta hash {:tag `Hash})))
          (bb/put-long! (.l2 ~(with-meta hash {:tag `Hash})))
          (bb/put-long! (.l3 ~(with-meta hash {:tag `Hash})))))}
  [byte-buffer hash]
  (-> byte-buffer
      (bb/put-long! (.l0 ^Hash hash))
      (bb/put-long! (.l1 ^Hash hash))
      (bb/put-long! (.l2 ^Hash hash))
      (bb/put-long! (.l3 ^Hash hash))))

(defn to-byte-array [hash]
  (-> (bb/allocate size)
      (into-byte-buffer! hash)
      (bb/array)))

(defn prefix
  "Returns the first 4 bytes of `hash`."
  {:inline
   (fn [hash]
     `(.prefix ~(with-meta hash {:tag `Hash})))}
  [hash]
  (.prefix ^Hash hash))

(defn prefix-from-byte-buffer!
  {:inline (fn [byte-buffer] `(bit-and (bb/get-int! ~byte-buffer) 0xFFFFFFFF))}
  [byte-buffer]
  (bit-and (bb/get-int! byte-buffer) 0xFFFFFFFF))

(defn prefix-from-hex [s]
  (Long/parseLong s 16))

(defn prefix-into-byte-buffer!
  {:inline
   (fn [byte-buffer hash-prefix]
     `(bb/put-int! ~byte-buffer (unchecked-int ~hash-prefix)))}
  [byte-buffer hash-prefix]
  (bb/put-int! byte-buffer (unchecked-int hash-prefix)))

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
    (type/hash-into resource hasher)
    (from-byte-buffer! (bb/wrap (.asBytes (.hash hasher))))))
