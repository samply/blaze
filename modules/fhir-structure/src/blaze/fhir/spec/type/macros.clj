(ns blaze.fhir.spec.type.macros
  (:require
    [blaze.fhir.spec.impl.intern :as intern]
    [blaze.fhir.spec.type.protocols :as p]
    [blaze.fhir.spec.type.system :as system]
    [clojure.data.xml.node :as xml-node]
    [clojure.string :as str]
    [cuerdas.core :refer [capital]])
  (:import
    [com.fasterxml.jackson.core JsonGenerator]
    [com.google.common.hash PrimitiveSink]
    [java.io Writer]))


(defn into! [to from]
  (reduce conj! to from))


(defn- write-start-object [gen]
  `(.writeStartObject ~(with-meta gen {:tag `JsonGenerator})))


(defn- write-end-object [gen]
  `(.writeEndObject ~(with-meta gen {:tag `JsonGenerator})))


(defn- write-field-name [gen field-name-form]
  `(.writeFieldName ~(with-meta gen {:tag `JsonGenerator}) ~(with-meta field-name-form {:tag `String})))


(defn- write-string [gen s]
  `(.writeString ~(with-meta gen {:tag `JsonGenerator}) ~(with-meta s {:tag `String})))


(defn- write-value [gen value]
  (cond
    (#{'Boolean 'boolean} (:tag (meta value)))
    `(.writeBoolean ~(with-meta gen {:tag `JsonGenerator}) ~value)

    (#{'Integer 'int 'BigDecimal} (:tag (meta value)))
    `(.writeNumber ~(with-meta gen {:tag `JsonGenerator}) ~value)

    :else
    (write-string gen `(system/-to-string ~value))))


(defn- xml-value [value]
  (if (= 'UUID (:tag (meta value)))
    `(str "urn:uuid:" ~value)
    `(system/-to-string ~value)))


(defn- lower-case-first [name]
  (str (str/lower-case (subs (str name) 0 1)) (subs (str name) 1)))


(defn- fhir-type-kw [name]
  (keyword "fhir" (lower-case-first name)))


(def ^:private tagged-sink
  (with-meta 'sink {:tag `PrimitiveSink}))


(def ^:private tagged-writer
  (with-meta 'w {:tag `Writer}))


(defn- tagged-x [name]
  (with-meta 'x {:tag name}))


(def ^:private id-tag 0)
(def ^:private extension-tag 1)
(def ^:private value-tag 2)


(defn- parse-value [value form]
  (if (#{'Integer 'int} (:tag (meta value)))
    `(Integer/parseInt ~form)
    form))


(defn- primitive-tag [value]
  (if (= 'Integer (:tag (meta value)))
    (with-meta value {:tag 'int})
    value))


(defn- gen-equals-sym [value-sym]
  (if (= 'int (:tag (meta value-sym)))
    '=
    '.equals))


(defn- gen-hash-code [value-sym]
  (if (= 'int (:tag (meta value-sym)))
    value-sym
    `(.hashCode ~value-sym)))


(defn- gen-type
  [name value {:keys [hash-num interned] :or {interned false}}]
  `(do
     (deftype ~name [~value]
       p/FhirType
       (~'-type [~'_] ~(fhir-type-kw name))
       (~'-interned [~'_] ~interned)
       (~'-value [~'_] ~value)
       (~'-serialize-json-as-field [~'_ ~'field-name ~'generator ~'_]
         ~(write-field-name 'generator 'field-name)
         ~(write-value 'generator value))
       (~'-serialize-json [~'_ ~'generator ~'_]
         ~(write-value 'generator value))
       (~'-to-xml [~'_] (xml-node/element nil {:value (system/-to-string ~value)}))
       (~'-hash-into [~'_ ~'sink]
         (.putByte ~tagged-sink (byte ~hash-num))
         (.putByte ~tagged-sink (byte ~value-tag))
         (system/-hash-into ~value ~'sink))
       (~'-references [~'_])
       Object
       (~'equals [~'this ~'x]
         (or (identical? ~'this ~'x)
             (and (instance? ~name ~'x) (~(gen-equals-sym value) ~value (.-value ~(tagged-x name))))))
       (~'hashCode [~'_]
         ~(gen-hash-code value))
       (~'toString [~'_]
         (system/-to-string ~value)))

     (defmethod print-method ~name [~(tagged-x name) ~tagged-writer]
       (.write ~'w ~(str "#fhir/" (lower-case-first name)))
       ~(when (= 'int (:tag (meta value))) `(.write ~'w " "))
       (print-method (.-value ~'x) ~'w))))


(defn- gen-extended-record
  [name fhir-type value-sym {:keys [hash-num interned] :or {interned false}}]
  `(do
     (defrecord ~name [~'id ~'extension ~value-sym]
       p/FhirType
       (~'-type [~'_] ~fhir-type)
       (~'-interned [~'_]
         ~(if interned
            `(and (p/-interned ~'extension) (nil? ~'id))
            `(and (nil? ~value-sym) (p/-interned ~'extension) (nil? ~'id))))
       (~'-value [~'_] ~value-sym)
       (~'-serialize-json-as-field [~'_ ~'field-name ~'generator ~'provider]
         (when (some? ~value-sym)
           ~(write-field-name 'generator 'field-name)
           ~(write-value 'generator value-sym))
         (when (or ~'id ~'extension)
           ~(write-field-name 'generator `(str "_" ~'field-name))
           ~(write-start-object 'generator)
           (when ~'id
             (p/-serialize-json-as-field ~'id "id" ~'generator ~'provider))
           (when ~'extension
             (p/-serialize-json-as-field ~'extension "extension" ~'generator ~'provider))
           ~(write-end-object 'generator)))
       (~'-to-xml [~'_]
         (xml-node/element*
           nil
           (cond-> {}
             ~'id (assoc :id ~'id)
             (some? ~value-sym) (assoc :value ~(xml-value value-sym)))
           ~'extension))
       (~'-hash-into [~'_ ~'sink]
         (.putByte ~tagged-sink (byte ~hash-num))
         (when ~'id
           (.putByte ~tagged-sink (byte ~id-tag))
           (system/-hash-into ~'id ~'sink))
         (when ~'extension
           (.putByte ~tagged-sink (byte ~extension-tag))
           (p/-hash-into ~'extension ~'sink))
         (when (some? ~value-sym)
           (.putByte ~tagged-sink (byte ~value-tag))
           (system/-hash-into ~value-sym ~'sink)))
       (~'-references [~'_]
         (p/-references ~'extension)))

     (defmethod print-method ~name [x# ~tagged-writer]
       (.write ~'w ~(str "#" (namespace fhir-type) "/" (clojure.core/name fhir-type)))
       (print-method (into {} (remove (comp nil? val)) x#) ~'w))))


(defmacro def-primitive-type
  [name [value] & {:keys [interned] :or {interned false} :as opts}]
  `(do
     ~(gen-type name (primitive-tag value) opts)

     ~(gen-extended-record (symbol (str "Extended" name)) (fhir-type-kw name)
                           value opts)

     (defn ~(symbol (str (lower-case-first name) "?")) [~'x]
       (identical? ~(fhir-type-kw name) (p/-type ~'x)))

     ~(let [map-create-extended (symbol (str "map->Extended" name))]
        (if interned
          (let [create (symbol (str "->" name))]
            `(def ~(symbol (lower-case-first name))
               (let [intern# (intern/intern-value ~create)
                     intern-extended# (intern/intern-value ~map-create-extended)]
                 (fn [x#]
                   (if (string? x#)
                     (intern# x#)
                     (let [{id# :id extension# :extension} x#]
                       (cond
                         (and (nil? extension#) (nil? id#))
                         (intern# (:value x#))

                         (and (p/-interned extension#) (nil? id#))
                         (intern-extended# x#)

                         :else
                         (~(symbol (str "Extended" name ".")) id# extension# (:value x#)))))))))

          `(def ~(symbol (lower-case-first name))
             (let [intern-extended# (intern/intern-value ~map-create-extended)]
               (fn [x#]
                 (if (map? x#)
                   (let [{id# :id extension# :extension value# :value} x#]
                     (cond
                       (and (nil? value#) (p/-interned extension#) (nil? id#))
                       (intern-extended# x#)

                       (and (nil? extension#) (nil? id#))
                       (~(symbol (str name ".")) value#)

                       :else
                       (~(symbol (str "Extended" name ".")) id# extension# value#)))
                   (~(symbol (str name ".")) x#)))))))

     (def ~(symbol (str "xml->" name))
       ~(let [value-sym (gensym "value")]
          `(fn [{{id# :id ~value-sym :value} :attrs content# :content}]
             (let [extension# (seq content#)]
               (if (or id# extension#)
                 (~(symbol (lower-case-first name))
                   (cond-> {:id id# :extension extension#}
                     ~value-sym (assoc :value ~(parse-value value value-sym))))
                 (~(symbol (lower-case-first name)) ~(parse-value value value-sym)))))))))


(defmacro defextended [name [_id _extension value] & {:keys [fhir-type] :as opts}]
  (gen-extended-record name fhir-type value opts))


(defn- field-name [field-sym]
  (if (:polymorph (meta field-sym))
    `(str ~(str field-sym) (capital (name (p/-type ~field-sym))))
    (str field-sym)))


(defmacro def-complex-type
  [name [& fields] & {:keys [fhir-type hash-num interned references]
                      :or {interned false}}]
  (let [sink-sym (gensym "sink")
        sink-sym-tag (with-meta sink-sym {:tag `PrimitiveSink})]
    `(do
       (defrecord ~name [~@fields]
         p/FhirType
         (~'-type [~'_] ~(or fhir-type (keyword "fhir" (str name))))
         (~'-interned [~'_] ~interned)
         (~'-serialize-json-as-field [~'this ~'field-name ~'gen ~'provider]
           ~(write-field-name 'gen 'field-name)
           (p/-serialize-json ~'this ~'gen ~'provider))
         (~'-serialize-json [~'_ ~'gen ~'provider]
           ~(write-start-object 'gen)
           ~@(map
               (fn [field]
                 `(when ~field
                    (p/-serialize-json-as-field ~field ~(field-name field) ~'gen ~'provider)))
               fields)
           ~(write-end-object 'gen))
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
         (print-method (into {} (remove (comp nil? val)) x#) ~'w)))))
