(ns blaze.fhir.spec.type.json
  (:refer-clojure :exclude [str])
  (:require
   [blaze.fhir.spec.type.protocols :as p]
   [blaze.util :refer [str]])
  (:import
   [com.fasterxml.jackson.core JsonGenerator SerializableString]
   [java.io Writer]
   [java.nio.charset StandardCharsets]
   [java.util Arrays]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(deftype FieldName [^String s ^bytes utf-8-bytes]
  SerializableString
  (getValue [_]
    s)
  (charLength [_]
    (.length s))
  (appendQuotedUTF8 [_ buffer offset]
    (let [num-bytes (alength utf-8-bytes)]
      (if (> (unchecked-add-int offset num-bytes) (alength buffer))
        (int -1)
        (do (System/arraycopy utf-8-bytes 0 buffer offset num-bytes)
            num-bytes))))
  (appendQuoted [_ buffer offset]
    (let [length (.length s)]
      (if (> (unchecked-add-int offset length) (alength buffer))
        (int -1)
        (do (.getChars s 0 length buffer offset)
            length))))
  (asUnquotedUTF8 [_]
    (Arrays/copyOf ^bytes utf-8-bytes (alength utf-8-bytes)))
  (asQuotedUTF8 [this]
    (.asUnquotedUTF8 this))
  Object
  (toString [_]
    s))

(defmethod print-method FieldName [^FieldName fieldName ^Writer w]
  (.write w "#blaze/field-name")
  (print-dup (.-s fieldName) w))

(defn field-name ^SerializableString [s]
  (->FieldName s (.getBytes ^String s StandardCharsets/UTF_8)))

(defn write-field [^JsonGenerator generator ^SerializableString field-name value]
  (when (p/-has-primary-content value)
    (.writeFieldName generator field-name)
    (p/-serialize-json value generator))
  (when (p/-has-secondary-content value)
    (.writeFieldName generator (str "_" (.getValue field-name)))
    (p/-serialize-json-secondary value generator)))

(defn write-primitive-list-field [^JsonGenerator generator ^SerializableString field-name list]
  (when (some p/-has-primary-content list)
    (.writeFieldName generator field-name)
    (.writeStartArray generator)
    (run! #(p/-serialize-json % generator) list)
    (.writeEndArray generator))
  (when (some p/-has-secondary-content list)
    (.writeFieldName generator (str "_" (.getValue field-name)))
    (.writeStartArray generator)
    (run! #(p/-serialize-json-secondary % generator) list)
    (.writeEndArray generator)))

(defn write-primitive-string-field [^JsonGenerator generator ^SerializableString field-name value]
  (if (string? value)
    (do (.writeFieldName generator field-name)
        (.writeString generator ^String value))
    (write-field generator field-name value)))

(defn write-non-primitive-field [^JsonGenerator generator ^SerializableString field-name value]
  (.writeFieldName generator field-name)
  (p/-serialize-json value generator))
