(ns blaze.elm.expression.cache.codec
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.elm.expression.cache.bloom-filter :as-alias bloom-filter]
   [blaze.elm.expression.cache.codec.form :as form])
  (:import
   [clojure.lang ILookup]
   [com.fasterxml.jackson.databind.util ByteBufferBackedInputStream]
   [com.google.common.hash BloomFilter Funnel Funnels]
   [java.io ByteArrayOutputStream DataOutputStream]
   [java.nio.charset StandardCharsets]))

(set! *warn-on-reflection* true)

(def ^:private ^:const ^long version 0)

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

(defn encode-key [expr-form]
  (form/hash expr-form))

(defn- encode-value [{::bloom-filter/keys [t expr-form filter]}]
  (let [out (ByteArrayOutputStream.)
        data-out (DataOutputStream. out)
        form (.getBytes ^String expr-form StandardCharsets/UTF_8)]
    (.writeByte data-out version)
    (.writeLong data-out t)
    (.writeInt data-out (alength form))
    (.write data-out ^bytes form)
    (.writeTo ^BloomFilter filter data-out)
    (.toByteArray out)))

(defn put-entry [{::bloom-filter/keys [expr-form] :as bloom-filter}]
  [:put :cql-bloom-filter (encode-key expr-form) (encode-value bloom-filter)])

(defn- decode-value* [buf]
  (assert (zero? (bb/get-byte! buf)) "assume version is always zero")
  (let [t (bb/get-long! buf)
        form (form/decode! buf)
        mem-size (bb/remaining buf)
        filter (BloomFilter/readFrom (ByteBufferBackedInputStream. buf) id-funnel)]
    (BloomFilterContainer. t form (.approximateElementCount filter) filter mem-size)))

(defn decode-value [byte-array]
  (decode-value* (bb/wrap byte-array)))
