(ns blaze.db.impl.index.tx-error
  "Functions for accessing the TxError index.

  The TxError index will keep track of all failed transactions, storing the
  anomaly about the failure reason. It is used to be able to return this anomaly
  as result of failing transactions."
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.db.impl.index.cbor :as cbor]
   [blaze.db.kv :as kv]
   [blaze.fhir.hash :as hash]
   [cognitect.anomalies :as anom])
  (:import
   [com.google.common.primitives Longs]))

(set! *warn-on-reflection* true)

(defn- decode-tx-cmd [tx-cmd]
  (update tx-cmd :hash (comp hash/from-byte-buffer! bb/wrap)))

(defn- decode-tx-error
  "Returns an anomaly."
  [bytes]
  (let [{:keys [category message http-status fhir-issue tx-cmd]} (cbor/read bytes)]
    (cond-> {::anom/category (keyword "cognitect.anomalies" category)}
      message
      (assoc ::anom/message message)
      http-status
      (assoc :http/status http-status)
      fhir-issue
      (assoc :fhir/issue fhir-issue)
      tx-cmd
      (assoc :blaze.db/tx-cmd (decode-tx-cmd tx-cmd)))))

(defn- encode-key [^long t]
  (Longs/toByteArray t))

(defn tx-error
  "Returns the transaction error, as anomaly, with `t` using `kv-store` or nil
  of none was found.

  Successful transactions are returned by `blaze.db.impl.index.tx-success/tx`."
  [kv-store t]
  (some-> (kv/get kv-store :tx-error-index (encode-key t)) decode-tx-error))

(defn- encode-tx-error
  [{::anom/keys [category message] :http/keys [status] :fhir/keys [issue]
    :blaze.db/keys [tx-cmd]}]
  (cbor/write
   (cond-> {:category (name category) :message message}
     status
     (assoc :http-status status)
     issue
     (assoc :fhir-issue issue)
     tx-cmd
     (assoc :tx-cmd tx-cmd))))

(defn index-entry
  "Returns an entry of the TxError index build from `t` and `anomaly`."
  [t anomaly]
  [:tx-error-index (encode-key t) (encode-tx-error anomaly)])
