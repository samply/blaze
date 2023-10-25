(ns blaze.elm.expression.cache.codec
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.expression.cache.bloom-filter :as-alias bloom-filter])
  (:import
    [clojure.lang ILookup]
    [com.fasterxml.jackson.databind.util ByteBufferBackedInputStream]
    [com.google.common.hash BloomFilter Funnel Funnels Hashing]
    [java.io ByteArrayOutputStream DataOutputStream]
    [java.nio.charset StandardCharsets]))


(set! *warn-on-reflection* true)


(def ^:private ^:const ^long version 0)
(def ^:private ^:const ^long key-size 32)


(deftype BloomFilterContainer [^long t exprForm ^long patientCount filter ^long memSize]
  ILookup
  (valAt [r key]
    (.valAt r key nil))
  (valAt [_ key not-found]
    (case key
      ::bloom-filter/t t
      ::bloom-filter/expr-form exprForm
      ::bloom-filter/patient-count patientCount
      ::bloom-filter/filter filter
      ::bloom-filter/mem-size memSize
      not-found)))


(def ^Funnel id-funnel
  (Funnels/stringFunnel StandardCharsets/ISO_8859_1))


(defn- form-str ^String [expression]
  (pr-str (core/-form expression)))


(defn encode-key [expression]
  (-> (Hashing/sha256)
      (.hashString (form-str expression) StandardCharsets/UTF_8)
      (.asBytes)))


(defn- encode-value [{::bloom-filter/keys [t filter]} expression]
  (let [out (ByteArrayOutputStream.)
        data-out (DataOutputStream. out)
        form (.getBytes (form-str expression) StandardCharsets/UTF_8)]
    (.writeByte data-out version)
    (.writeLong data-out t)
    (.writeInt data-out (alength form))
    (.write data-out form)
    (.writeTo ^BloomFilter filter data-out)
    (.toByteArray out)))


(defn index-entry [bloom-filter expression]
  [:cql-bloom-filter (encode-key expression) (encode-value bloom-filter expression)])


(defn- read-form! [buf]
  (let [len (bb/get-int! buf)
        bytes (byte-array len)]
    (bb/copy-into-byte-array! buf bytes)
    (String. bytes StandardCharsets/UTF_8)))


(defn- decode-value* [buf]
  (assert (zero? (bb/get-byte! buf)) "assume version is always zero")
  (let [t (bb/get-long! buf)
        form (read-form! buf)
        mem-size (bb/remaining buf)
        filter (BloomFilter/readFrom (ByteBufferBackedInputStream. buf) id-funnel)]
    (BloomFilterContainer. t form (.approximateElementCount filter) filter mem-size)))


(defn decode-value [byte-array]
  (decode-value* (bb/wrap byte-array)))


(defn decoder
  ([]
   [(bb/allocate-direct key-size)
    (bb/allocate-direct 1024)])
  ([_ vb]
   (decode-value* vb)))
