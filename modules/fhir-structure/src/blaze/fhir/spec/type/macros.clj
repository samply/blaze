(ns blaze.fhir.spec.type.macros
  (:require
    [blaze.fhir.spec.type.protocols :as p]
    [blaze.fhir.spec.type.system :as system]
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


(defmacro defcomplextype
  [name [& fields] & {:keys [fhir-type hash-num field-serializers]}]
  (let [sink-sym (gensym "sink")
        sink-sym-tag (with-meta sink-sym {:tag `PrimitiveSink})
        value-sym (gensym "value")
        value-sym-tag (with-meta value-sym {:tag name})]
    `(do
       (defrecord ~name [~@fields]
         p/FhirType
         (-type [~'_] ~(or fhir-type (keyword "fhir" (str name))))
         (-hash-into [~'_ ~sink-sym]
           (.putByte ~sink-sym-tag (byte ~hash-num))
           ~@(map-indexed
               (fn [idx field]
                 `(when ~field
                    (.putByte ~sink-sym-tag (byte ~idx))
                    (~(if (= 'id field) `system/-hash-into `p/-hash-into)
                      ~field ~sink-sym)))
               fields)))

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
                                (dotimes [i# (count ~field)]
                                  (.serialize ~(with-meta serializer {:tag `JsonSerializer}) (nth ~field i#) ~'gen ~'provider))
                                (.writeEndArray ~'gen)]
                              `[(.writeFieldName ~'gen ~(str field))
                                (.serialize ~(with-meta serializer {:tag `JsonSerializer}) ~field ~'gen ~'provider)])))))
                 fields)
             (.writeEndObject ~'gen)))))))
