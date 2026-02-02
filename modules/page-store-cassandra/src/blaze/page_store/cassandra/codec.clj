(ns blaze.page-store.cassandra.codec
  (:require
   [blaze.anomaly :as ba]
   [cognitect.anomalies :as anom]
   [jsonista.core :as j])
  (:import
   [com.fasterxml.jackson.dataformat.cbor CBORFactory]))

(def ^:private cbor-object-mapper
  (j/object-mapper {:factory (CBORFactory.)}))

(defmulti decode-clause (fn [[key]] key))

(defmethod decode-clause "_sort"
  [[_key param direction]] [:sort param (keyword direction)])

(defmethod decode-clause :default
  [clause] clause)

(defn- multiple-clauses? [disjunction]
  (sequential? (first disjunction)))

(defn- decode-disjunction [disjunction]
  (if (multiple-clauses? disjunction)
    (mapv decode-clause disjunction)
    (decode-clause disjunction)))

(defn- decode-conjunction
  "Decodes the strings in sort clauses to keywords because CBOR can't store
  keywords."
  [clauses]
  (mapv decode-disjunction clauses))

(defn- parse-msg [token cause-msg]
  (format "Error while parsing resource content with token `%s`: %s"
          token cause-msg))

(defn decode [bytes token]
  (-> (ba/try-all ::anom/incorrect (j/read-value bytes cbor-object-mapper))
      (ba/map decode-conjunction)
      (ba/exceptionally
       #(assoc %
               ::anom/message (parse-msg token (::anom/message %))
               :blaze.page-store/token token))))

(defmulti encode-clause (fn [[key]] key))

(defmethod encode-clause :sort
  [[_key param direction]] ["_sort" param (name direction)])

(defmethod encode-clause :default
  [clause] clause)

(defn- encode-disjunction [disjunction]
  (if (multiple-clauses? disjunction)
    (mapv encode-clause disjunction)
    (encode-clause disjunction)))

(defn- encode-conjunction
  "Encodes the keywords in sort clauses to strings because CBOR can't store
  keywords."
  [clauses]
  (mapv encode-disjunction clauses))

(defn encode [clauses]
  (j/write-value-as-bytes (encode-conjunction clauses) cbor-object-mapper))
