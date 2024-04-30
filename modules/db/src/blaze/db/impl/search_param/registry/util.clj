(ns blaze.db.impl.search-param.registry.util
  (:require
   [blaze.anomaly :refer [when-ok]]
   [blaze.byte-string :as bs]
   [blaze.db.kv :as kv])
  (:import
   [java.nio.charset StandardCharsets]))

(set! *warn-on-reflection* true)

(defn- max-id-key [column-family]
  (.getBytes (str (name column-family) "-max-id") StandardCharsets/ISO_8859_1))

(defn- get-max-id [kv-store column-family decode-id]
  (or (some-> (kv/get kv-store :default (max-id-key column-family)) decode-id) 0))

(defn encode-value [value]
  (.getBytes ^String value StandardCharsets/UTF_8))

(defn register-value! [kv-store column-family encode-id decode-id value]
  (when-ok [id (encode-id (inc (get-max-id kv-store column-family decode-id)))]
    (kv/put! kv-store [[column-family (encode-value value) id]
                       [:default (max-id-key column-family) id]])
    (bs/from-byte-array id)))
