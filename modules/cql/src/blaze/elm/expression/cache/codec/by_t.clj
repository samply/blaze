(ns blaze.elm.expression.cache.codec.by-t
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string-builder :as bsb]
   [blaze.elm.expression.cache.bloom-filter :as-alias bloom-filter]
   [blaze.elm.expression.cache.codec.form :as form])
  (:import
   [com.google.common.hash HashCode]
   [java.nio.charset StandardCharsets]))

(set! *warn-on-reflection* true)

(defn- encode-key [{::bloom-filter/keys [t hash]}]
  (-> (bsb/allocate (+ Long/BYTES 32))
      (bsb/put-long! t)
      (bsb/put-byte-array! (.asBytes ^HashCode hash))
      bsb/to-bytes))

(defn- encode-value [{::bloom-filter/keys [expr-form patient-count mem-size]}]
  (let [form (.getBytes ^String expr-form StandardCharsets/UTF_8)]
    (-> (bsb/allocate (+ Integer/BYTES (alength form) Long/BYTES Long/BYTES))
        (bsb/put-int! (alength form))
        (bsb/put-byte-array! form)
        (bsb/put-long! patient-count)
        (bsb/put-long! mem-size)
        bsb/to-bytes)))

(defn put-entry [bloom-filter]
  [:put :cql-bloom-filter-by-t (encode-key bloom-filter)
   (encode-value bloom-filter)])

(defn delete-entry [bloom-filter]
  [:delete :cql-bloom-filter-by-t (encode-key bloom-filter)])

(defn- decode-value* [buf]
  (let [form (form/decode! buf)
        patient-count (bb/get-long! buf)
        mem-size (bb/get-long! buf)]
    #::bloom-filter{:expr-form form :patient-count patient-count
                    :mem-size mem-size}))

(defn decoder [[kb vb]]
  (let [hash (byte-array 32)
        t (bb/get-long! kb)]
    (bb/copy-into-byte-array! kb hash)
    (assoc (decode-value* vb)
           ::bloom-filter/t t
           ::bloom-filter/hash (HashCode/fromBytes hash))))
