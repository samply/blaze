(ns blaze.db.impl.index.cbor
  (:refer-clojure :exclude [read])
  (:require
   [blaze.fhir.hash :as hash]
   [jsonista.core :as j])
  (:import
   [com.fasterxml.jackson.dataformat.cbor CBORFactory]))

(def ^:private cbor-object-mapper
  (j/object-mapper
   {:factory (CBORFactory.)
    :decode-key-fn true
    :modules [hash/object-mapper-module]}))

(defn read [bytes]
  (j/read-value bytes cbor-object-mapper))

(defn write [x]
  (j/write-value-as-bytes x cbor-object-mapper))
