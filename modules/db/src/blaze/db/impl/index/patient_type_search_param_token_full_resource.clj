(ns blaze.db.impl.index.patient-type-search-param-token-full-resource
  "Functions for accessing the PatientTypeSearchParamTokenFullResource index."
  (:refer-clojure :exclude [keys])
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.db.impl.bytes :as bytes]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.iterators :as i]
   [blaze.fhir.hash :as hash]))

(set! *unchecked-math* :warn-on-boxed)

(defn- key-size-value-system-id ^long [patient-id value]
  (+ (bs/size patient-id) 1 codec/tb-size codec/search-param-code-id-size
     (bs/size value) 1 codec/system-id-size))

(defn- decode-id-hash-prefix
  "Returns a tuple of `[id hash-prefix]`."
  [buf]
  (bb/skip-null-terminated! buf)
  (bb/inc-position! buf codec/tb-size)
  (bb/inc-position! buf codec/search-param-code-id-size)
  (bb/skip-null-terminated! buf)
  (bb/inc-position! buf codec/system-id-size)
  [(bs/from-byte-buffer-null-terminated! buf)
   (hash/prefix-from-byte-buffer! buf)])

(defn- encode-seek-key
  [patient-id tb search-param-code-id value system-id]
  (-> (bb/allocate (key-size-value-system-id patient-id value))
      (bb/put-null-terminated-byte-string! patient-id)
      (bb/put-byte! tb)
      (bb/put-byte-string! search-param-code-id)
      (bb/put-null-terminated-byte-string! value)
      (bb/put-byte-string! system-id)
      bb/flip!
      bs/from-byte-buffer!))

(defn prefix-keys
  "Returns a reducible collection of decoded `[id hash-prefix]` tuples from keys
  starting at `patient-id`, `tb`, `search-param-code-id`, `value` and
  `system-id` and ending when `patient-id`, `tb`, `search-param-code-id`,
  `value` and `system-id` is no longer a prefix."
  [snapshot patient-id tb search-param-code-id value system-id]
  (i/prefix-keys
   snapshot
   :patient-type-search-param-token-full-resource-index
   decode-id-hash-prefix
   (key-size-value-system-id patient-id value)
   (encode-seek-key patient-id tb search-param-code-id value system-id)))

(defn- key-size ^long [patient-id value id]
  (+ (key-size-value-system-id patient-id value) (bs/size id) 1
     hash/prefix-size))

(defn- encode-key [patient-id tb search-param-code-id value system-id id hash]
  (-> (bb/allocate (key-size patient-id value id))
      (bb/put-null-terminated-byte-string! patient-id)
      (bb/put-byte! tb)
      (bb/put-byte-string! search-param-code-id)
      (bb/put-null-terminated-byte-string! value)
      (bb/put-byte-string! system-id)
      (bb/put-null-terminated-byte-string! id)
      (hash/prefix-into-byte-buffer! (hash/prefix hash))
      bb/array))

(defn index-entry
  "Returns an entry of the TypeSearchParamTokenFullResource index build from
  `patient-id`, `tb`, `search-param-code-id`, `value`, `system-id`, `id` and
  `hash`."
  [patient-id tb search-param-code-id value system-id id hash]
  [:patient-type-search-param-token-full-resource-index
   (encode-key patient-id tb search-param-code-id value system-id id hash) bytes/empty])
