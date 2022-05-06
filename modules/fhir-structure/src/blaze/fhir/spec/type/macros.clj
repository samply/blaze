(ns blaze.fhir.spec.type.macros
  (:require
    [blaze.coll.core :as coll]
    [blaze.fhir.spec.type.protocols :as p]
    [blaze.fhir.spec.type.system :as system]
    [clojure.data.xml.node :as xml-node]
    [clojure.string :as str]
    [cuerdas.core :refer [capital kebab]])
  (:import
    [com.google.common.hash PrimitiveSink]
    [com.fasterxml.jackson.core JsonGenerator]
    [com.fasterxml.jackson.databind JsonSerializer SerializerProvider]
    [com.fasterxml.jackson.databind.ser.std StdSerializer]
    [java.io Writer]))


(defn- serializer-sym [name]
  (-> (symbol (str (kebab name) "-serializer"))
      (with-meta {:private true :tag `JsonSerializer})))


(defn- dyn-serialize* [^JsonGenerator gen ^SerializerProvider provider value]
  (.defaultSerializeValue provider value gen))


(defn dyn-serialize [gen provider value]
  (if (map? value)
    (dyn-serialize* gen provider (dissoc value :fhir/type))
    (dyn-serialize* gen provider value)))


(defn into! [to from]
  (reduce conj! to from))


(defn- write-string [gen s]
  `(.writeString ~(with-meta gen {:tag `JsonGenerator}) ~(with-meta s {:tag `String})))


(defn- serialize [serializer x gen provider]
  `(.serialize ~(with-meta serializer {:tag `JsonSerializer}) ~x ~gen ~provider))


(defmacro defprimitivetype
  [name [& _fields] & {:keys [hash-num interned] :or {interned false}}]
  `(deftype ~name [~'value]
     p/FhirType
     (~'-type [~'_] ~(keyword "fhir" (str (str/lower-case (subs (str name) 0 1))
                                          (subs (str name) 1))))
     (~'-interned [~'_] ~interned)
     (~'-value [~'_] ~'value)
     (~'-to-xml [~'_] (xml-node/element nil {:value ~'value}))
     (~'-hash-into [~'_ ~'sink]
       (.putByte ~(with-meta 'sink {:tag `PrimitiveSink}) (byte ~hash-num))
       (.putByte ~(with-meta 'sink {:tag `PrimitiveSink}) (byte 2))
       (system/-hash-into ~'value ~'sink))
     (~'-references [~'_])
     Object
     (~'equals [~'this ~'x]
       (or (identical? ~'this ~'x)
           (and (instance? ~name ~'x) (.equals ~'value (.value ~(with-meta 'x {:tag name}))))))
     (~'hashCode [~'_]
       (.hashCode ~'value))
     (~'toString [~'_]
       ~'value)))


(defmacro defcomplextype
  [name [& fields] & {:keys [fhir-type hash-num interned references field-serializers]
                      :or {interned false}}]
  (let [sink-sym (gensym "sink")
        sink-sym-tag (with-meta sink-sym {:tag `PrimitiveSink})
        value-sym (gensym "value")
        value-sym-tag (with-meta value-sym {:tag name})]
    `(do
       (defrecord ~name [~@fields]
         p/FhirType
         (~'-type [~'_] ~(or fhir-type (keyword "fhir" (str name))))
         (~'-interned [~'_] ~interned)
         (~'-hash-into [~'_ ~sink-sym]
           (.putByte ~sink-sym-tag (byte ~hash-num))
           ~@(map-indexed
               (fn [idx field]
                 `(when ~field
                    (.putByte ~sink-sym-tag (byte ~idx))
                    (~(if (= 'id field) `system/-hash-into `p/-hash-into)
                      ~field ~sink-sym)))
               fields))
         ~(if references
            `(~'-references [~'_]
               ~references)
            `(~'-references [~'_]
               (-> (transient [])
                   ~@(keep
                       (fn [field]
                         (when-not (= 'id field)
                           `(into! (p/-references ~field))))
                       fields)
                   (persistent!)))))

       (defmethod print-method ~name [x# ~(with-meta 'w {:tag `Writer})]
         (.write ~'w ~(str "#fhir/" name))
         (print-method (into {} (remove (comp nil? val)) x#) ~'w))

       (def ~(serializer-sym name)
         (proxy [StdSerializer] [~name]
           (serialize [~value-sym
                       ~(with-meta 'gen {:tag `JsonGenerator})
                       ~'provider]
             (.writeStartObject ~'gen ~value-sym)
             ~@(map
                 (fn [field]
                   `(when-let [~field (~(symbol (str ".-" field)) ~value-sym-tag)]
                      ~@(let [serializer (get field-serializers field)]
                          (case serializer
                            :string
                            `[(.writeStringField ~'gen ~(str field) ~field)]
                            :strings
                            `[(.writeArrayFieldStart ~'gen ~(str field))
                              (dotimes [~'i (coll/count ~field)]
                                ~(write-string 'gen `(coll/nth ~field ~'i)))
                              (.writeEndArray ~'gen)]
                            :decimal
                            `[(.writeNumberField ~'gen ~(str field) ~(with-meta field {:tag `BigDecimal}))]
                            :dynamic
                            `[(.writeFieldName ~'gen ~(str field))
                              (dyn-serialize ~'gen ~'provider ~field)]
                            :dynamic-type
                            `[(.writeFieldName ~'gen (str ~(str field) (capital (name (p/-type ~field)))))
                              (dyn-serialize ~'gen ~'provider ~field)]
                            (if (= :many (:cardinality (meta serializer)))
                              `[(.writeArrayFieldStart ~'gen ~(str field))
                                (dotimes [~'i (coll/count ~field)]
                                  ~(serialize serializer `(coll/nth ~field ~'i) 'gen 'provider))
                                (.writeEndArray ~'gen)]
                              `[(.writeFieldName ~'gen ~(str field))
                                (.serialize ~(with-meta serializer {:tag `JsonSerializer}) ~field ~'gen ~'provider)])))))
                 fields)
             (.writeEndObject ~'gen)))))))
