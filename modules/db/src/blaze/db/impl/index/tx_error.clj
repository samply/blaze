(ns blaze.db.impl.index.tx-error
  (:require
    [blaze.db.impl.byte-buffer :as bb]
    [blaze.db.kv :as kv]
    [cheshire.core :as cheshire]
    [cognitect.anomalies :as anom]))


(defn- decode-tx-error
  "Returns an anomaly."
  [bytes]
  (let [{:keys [category message http-status]} (cheshire/parse-cbor bytes keyword)]
    (cond->
      {::anom/category (keyword "cognitect.anomalies" category)}
      message
      (assoc ::anom/message message)
      http-status
      (assoc :http/status http-status))))


(defn- encode-key [t]
  (-> (bb/allocate Long/BYTES)
      (bb/put-long! t)
      (bb/array)))


(defn tx-error
  "Returns the transaction error, as anomaly, with `t` using `kv-store` or nil
  of none was found.

  Successful transactions are returned by `blaze.db.impl.index.tx-success/tx`."
  [kv-store t]
  (some-> (kv/get kv-store :tx-error-index (encode-key t))
          (decode-tx-error)))


(defn- encode-tx-error [{::anom/keys [category message] :http/keys [status]}]
  (cheshire/generate-cbor
    {:category (name category) :message message :http-status status}))


(defn index-entry [t anomaly]
  [:tx-error-index (encode-key t) (encode-tx-error anomaly)])

