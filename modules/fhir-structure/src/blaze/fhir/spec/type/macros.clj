(ns blaze.fhir.spec.type.macros
  (:refer-clojure :exclude [str])
  (:require
   [blaze.fhir.spec.impl.intern :as intern]
   [blaze.fhir.spec.type.json :as json]
   [blaze.fhir.spec.type.protocols :as p]
   [blaze.fhir.spec.type.string-util :as su]
   [blaze.fhir.spec.type.system :as system]
   [blaze.fhir.spec.xml :as spec-xml]
   [blaze.util :refer [str]]
   [clojure.data.xml.node :as xml-node]
   [clojure.string :as str])
  (:import
   [clojure.lang ILookup]
   [com.fasterxml.jackson.core JsonGenerator SerializableString]
   [com.fasterxml.jackson.core.io JsonStringEncoder]
   [com.google.common.hash PrimitiveSink]
   [java.io Writer]
   [java.nio.charset StandardCharsets]
   [java.util Arrays]))

(set! *warn-on-reflection* true)

(defn into! [to from]
  (reduce conj! to from))

(defn- write-start-object [gen]
  `(.writeStartObject ~(with-meta gen {:tag `JsonGenerator})))

(defn- write-end-object [gen]
  `(.writeEndObject ~(with-meta gen {:tag `JsonGenerator})))

(defn- write-string [gen s]
  `(.writeString ~(with-meta gen {:tag `JsonGenerator}) ~(with-meta s {:tag `String})))

(defn- write-value [gen value]
  (cond
    (#{'Boolean 'boolean} (:tag (meta value)))
    `(.writeBoolean ~(with-meta gen {:tag `JsonGenerator}) ~value)

    (#{'Integer 'int 'BigDecimal} (:tag (meta value)))
    `(.writeNumber ~(with-meta gen {:tag `JsonGenerator}) ~value)

    (#{'String} (:tag (meta value)))
    `(.writeString ~(with-meta gen {:tag `JsonGenerator}) ~value)

    :else
    (write-string gen `(system/-to-string ~value))))

(defn- write-null [gen]
  `(.writeNull ~(with-meta gen {:tag `JsonGenerator})))

(defn- xml-value [value]
  (if (= 'UUID (:tag (meta value)))
    `(str "urn:uuid:" ~value)
    `(spec-xml/replace-invalid-chars (system/-to-string ~value))))

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

(defn- gen-serializable-string [value]
  (let [unquoted-utf-8-bytes (with-meta 'unquoted-utf-8-bytes {:tag 'bytes})
        quoted-utf-8-bytes (with-meta 'quoted-utf-8-bytes {:tag 'bytes})]
    `[SerializableString
      (~'getValue [~'_]
                  ~value)
      (~'appendQuotedUTF8 [~'_ ~'buffer ~'offset]
                          (let [num-bytes# (alength ~quoted-utf-8-bytes)]
                            (if (> (unchecked-add-int ~'offset num-bytes#) (alength ~'buffer))
                              (int -1)
                              (do (System/arraycopy ~quoted-utf-8-bytes 0 ~'buffer ~'offset num-bytes#)
                                  num-bytes#))))
      (~'appendQuoted [~'_ ~'buffer ~'offset]
                      (let [length# (.length ~value)]
                        (if (> (unchecked-add-int ~'offset length#) (alength ~'buffer))
                          (int -1)
                          (do (.getChars ~value 0 length# ~'buffer ~'offset)
                              length#))))
      (~'asUnquotedUTF8 [~'_]
                        (Arrays/copyOf ~unquoted-utf-8-bytes (alength ~unquoted-utf-8-bytes)))
      (~'asQuotedUTF8 [~'_]
                      (Arrays/copyOf ~quoted-utf-8-bytes (alength ~quoted-utf-8-bytes)))]))

(defn- gen-type
  [name value {:keys [fhir-type hash-num interned]
               :or {fhir-type (fhir-type-kw name) interned false}}]
  `(do
     (deftype
      ~name
      ~(cond-> [value]
         (and interned (#{'String} (:tag (meta value))))
         (conj 'unquoted-utf-8-bytes 'quoted-utf-8-bytes))
       p/FhirType
       (~'-type [~'_] ~fhir-type)
       (~'-interned [~'_] ~interned)
       (~'-value [~'_] ~value)
       (~'-assoc-id [~'_ id#]
         (~(symbol (lower-case-first name)) {:id id# :value ~'value}))
       (~'-assoc-extension [~'_ ~'extension]
         (~(symbol (lower-case-first name)) {:extension ~'extension :value ~'value}))
       (~'-assoc-value [~'_ ~'val]
         (~(if (and interned (#{'String} (:tag (meta value))))
             (symbol (str "create-" (lower-case-first name)))
             (symbol (str name ".")))
          ~'val))
       (~'-has-primary-content [~'_] true)
       ~(if (and interned (#{'String} (:tag (meta value))))
          `(~'-serialize-json [~'this ~'generator]
                              (.writeString ~(with-meta 'generator {:tag `JsonGenerator}) ~'this))
          `(~'-serialize-json [~'_ ~'generator]
                              ~(write-value 'generator value)))
       (~'-has-secondary-content [~'_] false)
       (~'-serialize-json-secondary [~'_ ~'generator]
         ~(write-null 'generator))
       (~'-to-xml [~'_] (xml-node/element nil {:value (spec-xml/replace-invalid-chars (system/-to-string ~value))}))
       (~'-hash-into [~'_ ~'sink]
         (.putByte ~tagged-sink (byte ~hash-num))
         (.putByte ~tagged-sink (byte ~value-tag))
         (system/-hash-into ~value ~'sink))
       (~'-references [~'_])
       ~@(when (and interned (#{'String} (:tag (meta value))))
           (gen-serializable-string value))
       ILookup
       (~'valAt [~'_ ~'key]
         (when (identical? :value ~'key)
           ~value))
       (~'valAt [~'_ ~'key ~'not-found]
         (if (identical? :value ~'key)
           ~value
           ~'not-found))
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

(defn write-extended-attributes [^JsonGenerator generator id extension]
  (.writeStartObject generator)
  (when id
    (.writeFieldName generator "id")
    (p/-serialize-json id generator))
  (when extension
    (.writeFieldName generator "extension")
    (p/-serialize-json extension generator))
  (.writeEndObject generator))

(defn- gen-extended-record
  [name value-sym {:keys [fhir-type hash-num interned value-constructor
                          value-form]
                   :or {interned false}}]
  `(do
     (defrecord ~name [~'id ~'extension ~value-sym]
       p/FhirType
       (~'-type [~'_] ~fhir-type)
       (~'-interned [~'_]
         ~(if interned
            `(and (p/-interned ~'extension) (nil? ~'id))
            `(and (nil? ~value-sym) (p/-interned ~'extension) (nil? ~'id))))
       (~'-value [~'_] ~(or value-form value-sym))
       (~'-assoc-id [~'this ~'id]
         (assoc ~'this :id ~'id))
       (~'-assoc-extension [~'this ~'extension]
         (assoc ~'this :extension ~'extension))
       (~'-assoc-value [~'this ~'val]
         (assoc ~'this :value ~(if value-constructor `(~value-constructor ~'val) 'val)))
       (~'-has-primary-content [~'_] (some? ~value-sym))
       (~'-serialize-json [~'_ ~'generator]
         (if (some? ~value-sym)
           ~(write-value 'generator (or value-form value-sym))
           ~(write-null 'generator)))
       (~'-has-secondary-content [~'_]
         (or ~'id (seq ~'extension)))
       (~'-serialize-json-secondary [~'_ ~'generator]
         (write-extended-attributes ~'generator ~'id ~'extension))
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
         (when-not (nil? ~value-sym)
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

     ~(when (and interned (#{'String} (:tag (meta value))))
        `(defn ~(symbol (str "create-" (lower-case-first name))) [~value]
           (~(symbol (str name "."))
            ~value
            (.getBytes ~value StandardCharsets/UTF_8)
            (.quoteAsUTF8 (JsonStringEncoder/getInstance) ~value))))

     ~(gen-extended-record (symbol (str "Extended" name)) value
                           (cond-> opts
                             (nil? (:fhir-type opts))
                             (assoc :fhir-type (fhir-type-kw name))))

     (defn ~(symbol (str (lower-case-first name) "?")) [~'x]
       (identical? ~(fhir-type-kw name) (blaze.fhir.spec.type/type ~'x)))

     ~(let [fn-sym (symbol (lower-case-first name))
            map-create-extended (symbol (str "map->Extended" name))]
        (if interned
          (let [create (symbol (str "create-" (lower-case-first name)))]
            `(def ~fn-sym
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

          `(def ~fn-sym
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

(defmacro defextended [name [_id _extension value] & {:as opts}]
  (gen-extended-record name value opts))

(defn- polymorphic-field-names [base-field-name types]
  (into
   {}
   (map
    (fn [type]
      [(keyword "fhir" (str type))
       (json/field-name (str base-field-name (su/capital (str type))))]))
   types))

(defn- field-name
  "Pre-calculates the field name for non-polymorphic fields or emits code that
  gets the field name from a pre-calculated map of possible field names by type."
  [field-sym]
  (if (:polymorph (meta field-sym))
    (let [field-names (polymorphic-field-names (str field-sym) (:types (meta field-sym)))]
      `(~field-names (blaze.fhir.spec.type/type ~(with-meta field-sym nil))))
    (json/field-name (str field-sym))))

(defn write-field [generator field-sym]
  `(when-not (nil? ~(with-meta field-sym nil))
     ~@(cond
         (= 'String (:tag (meta field-sym)))
         `[(.writeFieldName ~(with-meta generator {:tag `JsonGenerator}) ~(field-name field-sym))
           (.writeString ~(with-meta generator {:tag `JsonGenerator}) ~(with-meta field-sym nil))]

         (:primitive-string (meta field-sym))
         `[(json/write-primitive-string-field ~generator ~(field-name field-sym) ~(with-meta field-sym nil))]

         (:primitive (meta field-sym))
         `[(json/write-field ~generator ~(field-name field-sym) ~(with-meta field-sym nil))]

         (:primitive-list (meta field-sym))
         `[(json/write-primitive-list-field ~generator ~(field-name field-sym) ~(with-meta field-sym nil))]

         :else
         `[(json/write-non-primitive-field ~generator ~(field-name field-sym) ~(with-meta field-sym nil))])))

(defmacro def-complex-type
  [name [& fields] & {:keys [fhir-type hash-num interned references]}]
  (let [m-sym (gensym "m")
        interned (or interned `(every? #(p/-interned %) (vals ~m-sym)))
        sink-sym (gensym "sink")
        sink-sym-tag (with-meta sink-sym {:tag `PrimitiveSink})]
    `(do
       (defrecord ~name [~@fields]
         p/FhirType
         (~'-type [~'_] ~(or fhir-type (keyword "fhir" (str name))))
         (~'-interned [~m-sym] ~interned)
         (~'-value [~'_])
         (~'-has-primary-content [~'_] true)
         (~'-serialize-json [~'_ ~'gen]
           ~(write-start-object 'gen)
           ~@(map
              (fn [field]
                (write-field 'gen field))
              fields)
           ~(write-end-object 'gen))
         (~'-has-secondary-content [~'_] false)
         (~'-serialize-json-secondary [~'this ~'gen]
           (throw (ex-info "A complex type has no secondary content." ~'this)))
         (~'-hash-into [~'_ ~sink-sym]
           (.putByte ~sink-sym-tag (byte ~hash-num))
           ~@(map-indexed
              (fn [idx field]
                `(when-not (nil? ~field)
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

       (def ~(symbol (su/pascal->kebab (str name)))
         (let [intern# (intern/intern-value ~(symbol (str "map->" name)))]
           (fn ~(symbol (su/pascal->kebab (str name))) [{:keys [~@fields] :as ~m-sym}]
             (if ~interned
               (intern# ~m-sym)
               (~(symbol (str name ".")) ~@fields)))))

       (defmethod print-method ~name [x# ~(with-meta 'w {:tag `Writer})]
         (.write ~'w ~(str "#fhir/" name))
         (print-method (into {} (remove (comp nil? val)) x#) ~'w)))))
