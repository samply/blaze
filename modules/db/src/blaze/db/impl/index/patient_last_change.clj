(ns blaze.db.impl.index.patient-last-change
  "Functions for accessing the PatientLastChange index."
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.db.impl.bytes :as bytes]
   [blaze.db.impl.codec :as codec]
   [blaze.db.kv :as kv])
  (:import
   [java.nio.charset StandardCharsets]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn- encode-key [patient-id t]
  (-> (bb/allocate (unchecked-add-int (bs/size patient-id) codec/t-size))
      (bb/put-byte-string! patient-id)
      (bb/put-long! (codec/descending-long ^long t))
      bb/array))

(defn index-entry [patient-id t]
  [:patient-last-change-index (encode-key patient-id t) bytes/empty])

(defn last-change-t
  "Returns the `t` of last change of any resource in the patient compartment not
  newer than `t` or nil if the patient has no resources."
  [plci patient-id t]
  (kv/seek! plci (encode-key patient-id t))
  (when (kv/valid? plci)
    (let [bb (bb/wrap (kv/key plci))
          patient-id-size (bs/size patient-id)]
      (when (and (< patient-id-size (bb/remaining bb))
                 (= patient-id (bs/from-byte-buffer! bb patient-id-size)))
        (-> (bb/get-long! bb)
            (codec/descending-long))))))

(def ^:private state-key
  (.getBytes "patient-last-change-state" StandardCharsets/ISO_8859_1))

(defn- encode-state [{:keys [type t]}]
  (if (identical? :current type)
    (byte-array [0])
    (-> (bb/allocate (inc Long/BYTES))
        (bb/put-byte! 1)
        (bb/put-long! t)
        bb/array)))

(defn decode-state [bytes]
  (let [buf (bb/wrap bytes)]
    (if (zero? (bb/get-byte! buf))
      {:type :current}
      {:type :building
       :t (bb/get-long! buf)})))

(defn state
  "Returns the state of the PatientLastChange index.

  The state is on of :current or last-t."
  [kv-store]
  (or (some-> (kv/get kv-store state-key) decode-state)
      {:type :building :t 0}))

(defn state-index-entry [state]
  [state-key (encode-state state)])
