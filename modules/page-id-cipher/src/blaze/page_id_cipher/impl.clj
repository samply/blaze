(ns blaze.page-id-cipher.impl
  "Tink key set handling for the page ID cipher.

  Key sets consist of AES128-GCM keys. Exactly one key of a key set is the
  primary key that is used for encryption. All keys of a key set can be used for
  decryption, which is what makes page IDs outlive a key rotation."
  (:require
   [clojure.core.protocols :as p]
   [clojure.datafy :as datafy]
   [clojure.string :as str])
  (:import
   [com.google.crypto.tink Aead InsecureSecretKeyAccess KeyStatus KeysetHandle
    KeysetHandle$Entry Parameters RegistryConfiguration TinkProtoKeysetFormat]
   [com.google.crypto.tink.aead AeadConfig PredefinedAeadParameters]))

(set! *warn-on-reflection* true)
(AeadConfig/register)

(def ^:private ^Parameters parameters
  PredefinedAeadParameters/AES128_GCM)

(defn gen-new-key-set-handle
  "Returns a new key set handle with a single primary key."
  []
  (-> (KeysetHandle/newBuilder)
      (.addEntry (-> (KeysetHandle/generateEntryFromParameters parameters)
                     (.withFixedId 0)
                     (.makePrimary)))
      (.build)))

(defn size [handle]
  (.size ^KeysetHandle handle))

(defn- last-entry [handle]
  (.getAt ^KeysetHandle handle (unchecked-dec-int (size handle))))

(defn- add-new-entry [handle]
  (-> (KeysetHandle/newBuilder ^KeysetHandle handle)
      (.addEntry (-> (KeysetHandle/generateEntryFromParameters parameters)
                     (.withFixedId (inc (.getId ^KeysetHandle$Entry (last-entry handle))))))
      (.build)))

(defn- remove-first-entry [handle]
  (let [builder (KeysetHandle/newBuilder ^KeysetHandle handle)]
    (.deleteAt builder 0)
    (.build builder)))

(defn- set-last-entry-primary [handle]
  (let [builder (KeysetHandle/newBuilder ^KeysetHandle handle)]
    (.makePrimary (.getAt builder (dec (size handle))))
    (.build builder)))

(defn- last-entry-primary? [handle]
  (.isPrimary ^KeysetHandle$Entry (last-entry handle)))

(defn rotate-keys
  "Rotates keys in the key set `handle` according to the following rules:

  [primary-key] -> [primary-key new-key]
  [primary-key new-key] -> [old-key primary-key]
  [old-key primary-key] -> [old-key primary-key new-key]
  [old-key primary-key new-key] -> [old-key old-key primary-key]
  [old-key old-key primary-key] -> [old-key primary-key new-key]
  [old-key primary-key new-key] -> [old-key old-key primary-key]

  So a new key is first added as a non-primary key, is made primary on the next
  rotation, stays primary for two rotations and is only removed three rotations
  after it stopped being primary. That way a page ID stays decryptable for at
  least three and at most five rotation periods after it was created. That
  upper bound limits how long a historic database state stays reachable through
  a paging link; see `blaze.page-id-cipher`."
  [handle]
  (if (last-entry-primary? handle)
    (cond-> (add-new-entry handle)
      (= 3 (size handle))
      (remove-first-entry))
    (set-last-entry-primary handle)))

(defn get-aead
  "Returns the AEAD primitive of `key-set-handle`.

  It encrypts with the primary key and decrypts with all keys of the key set."
  [key-set-handle]
  (.getPrimitive ^KeysetHandle key-set-handle (RegistryConfiguration/get) Aead))

(defn serialize-key-set
  "Serializes the key set `handle` into the binary Tink keyset format."
  [handle]
  (TinkProtoKeysetFormat/serializeKeyset ^KeysetHandle handle
                                         (InsecureSecretKeyAccess/get)
                                         (RegistryConfiguration/get)))

(defn parse-key-set
  "Parses `bytes` in the binary Tink keyset format into a key set handle."
  [bytes]
  (TinkProtoKeysetFormat/parseKeyset ^bytes bytes
                                     (InsecureSecretKeyAccess/get)
                                     (RegistryConfiguration/get)))

(extend-protocol p/Datafiable
  KeysetHandle
  (datafy [handle]
    (mapv #(datafy/datafy (.getAt handle (int %))) (range (.size handle))))

  KeysetHandle$Entry
  (datafy [entry]
    {:id (.getId entry)
     :primary (.isPrimary entry)
     :status (datafy/datafy (.getStatus entry))})

  KeyStatus
  (datafy [status]
    (keyword "key.status" (str/lower-case (str status)))))
