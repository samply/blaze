(ns blaze.page-id-cipher.impl
  (:require
   [clojure.core.protocols :as p]
   [clojure.datafy :as datafy]
   [clojure.string :as str])
  (:import
   [com.google.crypto.tink Aead KeyStatus KeysetHandle KeysetHandle$Entry Parameters]
   [com.google.crypto.tink.aead AeadConfig PredefinedAeadParameters]))

(set! *warn-on-reflection* true)
(AeadConfig/register)

(def ^:private ^Parameters parameters
  PredefinedAeadParameters/AES128_GCM)

(defn gen-new-key-set-handle []
  (-> (KeysetHandle/newBuilder)
      (.addEntry (-> (KeysetHandle/generateEntryFromParameters parameters)
                     (.withFixedId 0)
                     (.makePrimary)))
      (.build)))

(defn size [handle]
  (.size ^KeysetHandle handle))

(defn- last-entry [handle]
  (.getAt ^KeysetHandle handle (dec (size handle))))

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
  "Rotates keys in the key set `handle` according the following rules:

  [primary-key] -> [primary-key new-key]
  [primary-key new-key] -> [old-key primary-key]
  [old-key primary-key] -> [old-key primary-key new-key]
  [old-key primary-key new-key] -> [old-key old-key primary-key]
  [old-key old-key primary-key] -> [old-key primary-key new-key]
  [old-key primary-key new-key] -> [old-key old-key primary-key]"
  [handle]
  (if (last-entry-primary? handle)
    (cond-> (add-new-entry handle)
      (= 3 (size handle))
      (remove-first-entry))
    (set-last-entry-primary handle)))

(defn get-aead [key-set-handle]
  (.getPrimitive ^KeysetHandle key-set-handle Aead))

(extend-protocol p/Datafiable
  KeysetHandle
  (datafy [handle]
    (mapv #(datafy/datafy (.getAt handle %)) (range (.size handle))))

  KeysetHandle$Entry
  (datafy [entry]
    {:id (.getId entry)
     :primary (.isPrimary entry)
     :status (datafy/datafy (.getStatus entry))})

  KeyStatus
  (datafy [status]
    (keyword "key.status" (str/lower-case (str status)))))
