(ns blaze.fhir.spec.type.json
  (:require
    [blaze.fhir.spec.type.protocols :as p]
    [clojure.string :as str])
  (:import
    [java.util Arrays]
    [clojure.lang PersistentVector]
    [com.fasterxml.jackson.core JsonGenerator SerializableString]
    [java.nio.charset StandardCharsets]
    [java.io Writer]))


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
  (asUnquotedUTF8 [_]
    (Arrays/copyOf ^bytes utf-8-bytes (alength utf-8-bytes)))
  (asQuotedUTF8 [this]
    (.asUnquotedUTF8 this)))


(defmethod print-dup (Class/forName "[B") [^bytes year ^Writer w]
  (.write w "#=(byte-array [")
  (.write w ^String (str/join " " (map int (vec year))))
  (.write w "])"))


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


(defn- has-primary-content-rf [_ x]
  (when (p/-has-primary-content x) (reduced true)))


(defn- has-secondary-content-rf [_ x]
  (when (p/-has-secondary-content x) (reduced true)))


(defn write-primitive-list-field [^JsonGenerator generator ^SerializableString field-name ^PersistentVector list]
  (when (.reduce list has-primary-content-rf nil)
    (.writeFieldName generator field-name)
    (.writeStartArray generator)
    (.reduce list #(p/-serialize-json %2 generator) nil)
    (.writeEndArray generator))
  (when (.reduce list has-secondary-content-rf nil)
    (.writeFieldName generator (str "_" (.getValue field-name)))
    (.writeStartArray generator)
    (.reduce list #(p/-serialize-json-secondary %2 generator) nil)
    (.writeEndArray generator)))


(defn write-primitive-string-field [^JsonGenerator generator ^SerializableString field-name value]
  (if (string? value)
    (do (.writeFieldName generator field-name)
        (.writeString generator ^String value))
    (write-field generator field-name value)))


(defn write-non-primitive-field [^JsonGenerator generator ^SerializableString field-name value]
  (.writeFieldName generator field-name)
  (p/-serialize-json value generator))
