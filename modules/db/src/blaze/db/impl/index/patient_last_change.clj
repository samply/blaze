(ns blaze.db.impl.index.patient-last-change
  "Functions for accessing the PatientLastChange index."
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string-builder :as bsb]
   [blaze.db.impl.bytes :as bytes]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.util :refer [read-t!]]
   [blaze.db.kv :as kv])
  (:import
   [java.lang AutoCloseable]
   [java.nio.charset StandardCharsets]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn- encode-key [patient-id t]
  (-> (bsb/allocate (+ (codec/id-size patient-id) codec/t-size))
      (bsb/put-iso-8859-1-string! patient-id)
      (bsb/put-long! (codec/descending-long t))
      bsb/to-bytes))

(defn index-entry [patient-id t]
  [:patient-last-change-index (encode-key patient-id t) bytes/empty])

(defn last-change-t
  "Returns the `t` of last change of any resource in the patient compartment not
  newer than `t` or nil if the patient has no resources."
  [plci patient-id t]
  (kv/seek! plci (encode-key patient-id t))
  (when (kv/valid? plci)
    (let [bb (bb/wrap (kv/key plci))
          patient-id-size (codec/id-size patient-id)]
      (when (and (< patient-id-size (bb/remaining bb))
                 (= patient-id (bb/get-iso-8859-1-string! bb patient-id-size)))
        (read-t! bb)))))

(defn last-change-t-xf
  "Returns a stateful transducer that receives Patient resource handles and
  emits `(f resource-handle last-change-t)` where `last-change-t` is the `t` of
  last change of any resource in the patient compartment not newer than `t` or
  nil if the patient has no resources.

  Opens only one iterator on `snapshot` for all resource handles and closes it
  on completion. Can only be used by a single thread."
  [snapshot t f]
  (fn [rf]
    (let [plci (kv/new-iterator snapshot :patient-last-change-index)]
      (fn
        ([result]
         (.close ^AutoCloseable plci)
         (rf result))
        ([result handle]
         (rf result (f handle (last-change-t plci (:id handle) t))))))))

(def ^:private state-key
  (.getBytes "patient-last-change-state" StandardCharsets/ISO_8859_1))

(defn- encode-state [{:keys [type t]}]
  (if (identical? :current type)
    (byte-array [0])
    (-> (bsb/allocate (inc Long/BYTES))
        (bsb/put-byte! 1)
        (bsb/put-long! t)
        bsb/to-bytes)))

(defn- decode-state [bytes]
  (let [buf (bb/wrap bytes)]
    (if (zero? (bb/get-byte! buf))
      {:type :current}
      {:type :building
       :t (bb/get-long! buf)})))

(defn state
  "Returns the state of the PatientLastChange index.

  The initial state is `{:type :building :t 0}`."
  [kv-store]
  (or (some-> (kv/get kv-store :default state-key) decode-state)
      {:type :building :t 0}))

(defn state-index-entry [state]
  [:default state-key (encode-state state)])
