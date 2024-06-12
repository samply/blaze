(ns blaze.elm.expression.cache.codec
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.elm.expression.cache.bloom-filter :as-alias bloom-filter]
   [blaze.elm.expression.cache.codec.form :as form])
  (:import
   [clojure.lang ILookup]
   [com.fasterxml.jackson.databind.util ByteBufferBackedInputStream]
   [com.google.common.hash BloomFilter Funnel Funnels HashCode]
   [java.io ByteArrayOutputStream DataOutputStream]
   [java.nio.charset StandardCharsets]))

(set! *warn-on-reflection* true)

(def ^:private ^:const ^long version 0)

(definterface IBloomFilterContainer
  (merge [other]))

(deftype BloomFilterContainer [hash ^long t exprForm ^long patientCount
                               ^BloomFilter filter ^long memSize]
  IBloomFilterContainer
  (merge [_ other]
    (when (.isCompatible filter (.-filter ^BloomFilterContainer other))
      (let [newFilter (doto (.copy filter) (.putAll (.-filter ^BloomFilterContainer other)))]
        (when (< (.expectedFpp newFilter) 0.01)
          (BloomFilterContainer. nil (min t (.-t ^BloomFilterContainer other)) nil
                                 (.approximateElementCount newFilter)
                                 newFilter memSize)))))
  ILookup
  (valAt [r key]
    (.valAt r key nil))
  (valAt [_ key not-found]
    (case key
      ::bloom-filter/hash hash
      ::bloom-filter/t t
      ::bloom-filter/expr-form exprForm
      ::bloom-filter/patient-count patientCount
      ::bloom-filter/filter filter
      ::bloom-filter/mem-size memSize
      not-found)))

(def ^Funnel id-funnel
  (Funnels/stringFunnel StandardCharsets/ISO_8859_1))

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

(defn put-entry
  "Creates a put-entry for column-family `cql-bloom-filter` with the hash of
  `bloom-filter` as key and `bloom-filter` as value."
  {:arglists '([bloom-filter])}
  [{::bloom-filter/keys [hash] :as bloom-filter}]
  [:put :cql-bloom-filter (.asBytes ^HashCode hash) (encode-value bloom-filter)])

(defn delete-entry
  "Creates a delete-entry for column-family `cql-bloom-filter` with the hash of
  `bloom-filter` as key."
  {:arglists '([bloom-filter])}
  [{::bloom-filter/keys [hash]}]
  [:delete :cql-bloom-filter (.asBytes ^HashCode hash)])

(defn- decode-value* [hash buf]
  (assert (zero? (bb/get-byte! buf)) "assume version is always zero")
  (let [t (bb/get-long! buf)
        expr-form (form/decode! buf)
        mem-size (bb/remaining buf)
        filter (BloomFilter/readFrom (ByteBufferBackedInputStream. buf) id-funnel)]
    (BloomFilterContainer. hash t expr-form (.approximateElementCount filter) filter mem-size)))

(defn decode-value [hash byte-array]
  (decode-value* hash (bb/wrap byte-array)))
