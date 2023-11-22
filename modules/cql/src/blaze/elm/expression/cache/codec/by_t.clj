(ns blaze.elm.expression.cache.codec.by-t
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.elm.expression.cache.bloom-filter :as-alias bloom-filter]
   [blaze.elm.expression.cache.codec.form :as form])
  (:import
   [java.nio.charset StandardCharsets]))

(set! *warn-on-reflection* true)

(defn- encode-key [{::bloom-filter/keys [t expr-form]}]
  (-> (bb/allocate (+ Long/BYTES 32))
      (bb/put-long! t)
      (bb/put-byte-array! ^bytes (form/hash expr-form))
      bb/array))

(defn- encode-value [{::bloom-filter/keys [expr-form patient-count mem-size]}]
  (let [form (.getBytes ^String expr-form StandardCharsets/UTF_8)]
    (-> (bb/allocate (+ Integer/BYTES (alength form) Long/BYTES Long/BYTES))
        (bb/put-int! (alength form))
        (bb/put-byte-array! form)
        (bb/put-long! patient-count)
        (bb/put-long! mem-size)
        bb/array)))

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

(defn decode-value [byte-array]
  (decode-value* (bb/wrap byte-array)))

(defn decoder [kb vb]
  (assoc (decode-value* vb)
         ::bloom-filter/t (bb/get-long! kb)))
