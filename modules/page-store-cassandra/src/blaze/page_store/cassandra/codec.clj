(ns blaze.page-store.cassandra.codec
  (:require
    [blaze.anomaly :as ba]
    [blaze.byte-buffer :as bb]
    [cognitect.anomalies :as anom]
    [jsonista.core :as j])
  (:import
    [com.fasterxml.jackson.dataformat.cbor CBORFactory]))


(def ^:private cbor-object-mapper
  (j/object-mapper {:factory (CBORFactory.)}))


(defn- parse-msg [token cause-msg]
  (format "Error while parsing resource content with token `%s`: %s"
          token cause-msg))


(defn decode [bytes token]
  (-> (ba/try-all ::anom/incorrect (j/read-value bytes cbor-object-mapper))
      (ba/exceptionally
        #(assoc %
           ::anom/message (parse-msg token (::anom/message %))
           :blaze.page-store/token token))))


(defn encode [clauses]
  (bb/wrap (j/write-value-as-bytes clauses cbor-object-mapper)))
