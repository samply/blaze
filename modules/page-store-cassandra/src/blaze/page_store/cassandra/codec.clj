(ns blaze.page-store.cassandra.codec
  (:require
    [blaze.anomaly :as ba]
    [cognitect.anomalies :as anom]
    [jsonista.core :as j])
  (:import
    [com.fasterxml.jackson.dataformat.cbor CBORFactory]))


(def ^:private cbor-object-mapper
  (j/object-mapper {:factory (CBORFactory.)}))


(defmulti decode-sort-clause (fn [[key]] key))


(defmethod decode-sort-clause "_sort"
  [[_key param direction]] [:sort param (keyword direction)])


(defmethod decode-sort-clause :default
  [clause] clause)


(defn- decode-sort-clauses
  "Decodes the strings in sort clauses to keywords because CBOR can't store
  keywords."
  [clauses]
  (mapv decode-sort-clause clauses))


(defn- parse-msg [token cause-msg]
  (format "Error while parsing resource content with token `%s`: %s"
          token cause-msg))


(defn decode [bytes token]
  (-> (ba/try-all ::anom/incorrect (j/read-value bytes cbor-object-mapper))
      (ba/map decode-sort-clauses)
      (ba/exceptionally
        #(assoc %
           ::anom/message (parse-msg token (::anom/message %))
           :blaze.page-store/token token))))


(defmulti encode-sort-clause (fn [[key]] key))


(defmethod encode-sort-clause :sort
  [[_key param direction]] ["_sort" param (name direction)])


(defmethod encode-sort-clause :default
  [clause] clause)


(defn- encode-sort-clauses
  "Encodes the keywords in sort clauses to strings because CBOR can't store
  keywords."
  [clauses]
  (mapv encode-sort-clause clauses))


(defn encode [clauses]
  (j/write-value-as-bytes (encode-sort-clauses clauses) cbor-object-mapper))
