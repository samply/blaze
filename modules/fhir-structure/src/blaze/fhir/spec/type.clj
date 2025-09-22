(ns blaze.fhir.spec.type
  "Functions for primitive and complex types."
  (:refer-clojure
   :exclude
   [boolean boolean? decimal? integer? long meta str string? time type uri? uuid?])
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.byte-string]
   [blaze.fhir.spec.impl.intern :as intern]
   [blaze.fhir.spec.type.json :as json]
   [blaze.fhir.spec.type.macros :refer [def-complex-type defextended]]
   [blaze.fhir.spec.type.protocols :as p]
   [blaze.fhir.spec.type.string-util :as su]
   [blaze.fhir.spec.type.system :as system]
   [blaze.util :refer [str]]
   [clojure.alpha.spec :as s2]
   [clojure.data.xml :as xml]
   [clojure.data.xml.name :as xml-name]
   [clojure.data.xml.node :as xml-node]
   [clojure.string :as str])
  (:import
   [blaze.fhir.spec.type Address Annotation Attachment Base64Binary Canonical Code CodeableConcept Coding DateTime Decimal Extension Id Identifier Markdown Oid Period PositiveInt Reference UnsignedInt Uri Url Uuid]
   [blaze.fhir.spec.type.system Date]
   [clojure.lang ILookup IPersistentMap Keyword]
   [com.fasterxml.jackson.core JsonGenerator]
   [com.google.common.hash PrimitiveSink]
   [java.io Writer]
   [java.time
    DateTimeException Instant LocalDateTime LocalTime OffsetDateTime ZoneOffset]
   [java.time.format DateTimeFormatter]
   [java.util Comparator List Map$Entry]))

(xml-name/alias-uri 'f "http://hl7.org/fhir")

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn type
  "Returns the FHIR type if `x` if it has some."
  [x]
  (p/-type x))

(defn value
  "Returns the possible value of the primitive value `x` as FHIRPath system
  type."
  [x]
  (p/-value x))

(defn assoc-id
  "Associates `id` to `x`."
  [x id]
  (p/-assoc-id x id))

(defn assoc-extension
  "Associates `extension` to `x`."
  [x extension]
  (p/-assoc-extension x extension))

(defn assoc-value
  "Associates `value` to `x`."
  [x value]
  (p/-assoc-value x value))

(defn to-xml [x]
  (p/-to-xml x))

(defn hash-into [x sink]
  (p/-hash-into x sink))

(defn references
  "Returns a collection of local references which are tuples of FHIR resource
  type name and FHIR resource id."
  [x]
  (p/-references x))

(defn- create-fn [intern create parse-fn]
  (fn [x]
    (if (map? x)
      (let [{:keys [id extension value]} x]
        (cond
          (and (nil? value) (p/-interned extension) (nil? id))
          (intern {:extension extension})

          (and (nil? extension) (nil? id))
          (parse-fn value)

          :else
          (create id extension (some-> value parse-fn))))
      (parse-fn x))))

(defn- system-to-xml [x]
  (xml-node/element nil {:value (system/-to-string x)}))

(defn- into! [to from]
  (reduce conj! to from))

;; ---- nil -------------------------------------------------------------------

;; Be sure all methods can be called on nil.
(extend-protocol p/FhirType
  nil
  (-type [_])
  (-interned [_] true)
  (-assoc-id [_ _])
  (-assoc-extension [_ _])
  (-value [_])
  (-assoc-value [_ _])
  (-has-primary-content [_] false)
  (-serialize-json [_ _ _])
  (-has-secondary-content [_] false)
  (-serialize-json-secondary [_ _ _])
  (-to-xml [_])
  (-hash-into [_ _])
  (-references [_] []))

;; ---- Object ----------------------------------------------------------------

;; Other instances have no type.
(extend-protocol p/FhirType
  Object
  (-type [_])
  (-interned [_] false)
  (-references [_]))

;; ---- Macros ----------------------------------------------------------------

(defn write-extended-attributes [^JsonGenerator generator id extension]
  (.writeStartObject generator)
  (when id
    (.writeFieldName generator "id")
    (p/-serialize-json id generator))
  (when extension
    (.writeFieldName generator "extension")
    (p/-serialize-json extension generator))
  (.writeEndObject generator))

(defmacro def-extend-protocol-primitive [name & {:as opts}]
  (let [constructor (symbol (str name) "new")]
    `(extend-protocol p/FhirType
       ~name
       (~'-type [~'v]
         (.fhirType ~'v))
       (~'-interned [~'v]
         (and (nil? (.id ~'v)) (p/-interned (.extension ~'v))
              ~@(when-not (:value-internable opts) [`(nil? (.value ~'v))])))
       (~'-value [~'v]
         (.value ~'v))
       (~'-assoc-id [~'v ~'id]
         (~constructor ~'id (.extension ~'v) (.value ~'v)))
       (~'-assoc-extension [~'v ~'extension]
         (~constructor (.id ~'v) ~'extension (.value ~'v)))
       (~'-assoc-value [~'v ~'value]
         (~constructor (.id ~'v) (.extension ~'v) ~'value))
       (~'-has-primary-content [~'v]
         (.hasValue ~'v))
       (~'-serialize-json [~'v ~'generator]
         (.serializeJsonPrimitiveValue ~'v ~'generator))
       (~'-has-secondary-content [~'v]
         (.isExtended ~'v))
       (~'-serialize-json-secondary [~'v ~'generator]
         (.serializeJsonPrimitiveExtension ~'v ~'generator))
       (~'-to-xml [~'v]
         (xml-node/element*
          nil
          (cond-> {}
            (some? (.id ~'v))
            (assoc :id (.id ~'v))
            (some? (.value ~'v))
            (assoc :value (system/-to-string (.value ~'v))))
          (.extension ~'v)))
       (~'-hash-into [~'v ~'sink]
         (.hashInto ~'v ~'sink))
       (~'-references [~'v]
         (p/-references (.extension ~'v))))))

(defmacro print-data-element
  {:arglists '([prev-names* name])}
  [& args]
  (let [prev-names (butlast args)
        name (last args)
        accessor (fn [name] `(~(symbol (str "." name)) ~'v))]
    `(when ~(accessor name)
       ~@(when (seq prev-names)
           [`(when (or ~@(map accessor prev-names))
               (.write ~'w " "))])
       (.write ~'w ~(str ":" name " "))
       (print-method ~(accessor name) ~'w))))

(defmacro print-type [name & data-element-names]
  `(do (.write ~'w ~(format "#fhir/%s{" name))
       ~@(map
          (fn [names] `(print-data-element ~@names))
          (map #(take % data-element-names) (range 1 (inc (count data-element-names)))))
       (.write ~'w "}")))

(defmacro def-print-method-primitive [name & data-element-names]
  (let [class-sym (symbol (str "blaze.fhir.spec.type." (su/capital (str name))))]
    `(defmethod print-method ~class-sym
       [~(with-meta 'v {:tag class-sym}) ~(with-meta 'w {:tag 'Writer})]
       (if (or (.id ~'v) (.extension ~'v))
         (print-type ~name ~@data-element-names)
         (do (.write ~'w ~(format "#fhir/%s " name))
             (print-method (.value ~'v) ~'w))))))

;; ---- boolean ---------------------------------------------------------------

(defn boolean? [x]
  (instance? blaze.fhir.spec.type.Boolean x))

(defn- map->Boolean [{:keys [id extension value]}]
  (blaze.fhir.spec.type.Boolean. id extension value))

(def ^{:arglists '([x])} boolean
  (let [intern-extended (intern/intern-value map->Boolean)]
    (fn [x]
      (cond
        (map? x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension))
            (intern-extended x)
            (blaze.fhir.spec.type.Boolean. id extension value)))

        (true? x) blaze.fhir.spec.type.Boolean/TRUE
        (false? x) blaze.fhir.spec.type.Boolean/FALSE

        :else (ba/incorrect (format "Invalid boolean value `%s`." x))))))

(def-extend-protocol-primitive blaze.fhir.spec.type.Boolean
  :value-internable true)

(def-print-method-primitive boolean "id" "extension" "value")

(defmethod print-dup blaze.fhir.spec.type.Boolean [^blaze.fhir.spec.type.Boolean e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Boolean. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- integer ---------------------------------------------------------------

(defn integer? [x]
  (instance? blaze.fhir.spec.type.Integer x))

(defn- map->Integer [{:keys [id extension value]}]
  (blaze.fhir.spec.type.Integer. id extension (some-> value int)))

(def ^{:arglists '([x])} integer
  (let [intern-extended (intern/intern-value map->Integer)]
    (fn [x]
      (cond
        (map? x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension) (nil? value))
            (intern-extended x)
            (blaze.fhir.spec.type.Integer. id extension (some-> value int))))

        (int? x) (blaze.fhir.spec.type.Integer. nil nil (int x))

        :else (ba/incorrect (format "Invalid integer value `%s`." x))))))

(def-extend-protocol-primitive blaze.fhir.spec.type.Integer)

(def-print-method-primitive integer "id" "extension" "value")

(defmethod print-dup blaze.fhir.spec.type.Integer [^blaze.fhir.spec.type.Integer e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Integer. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- long ---------------------------------------------------------------

(declare long)

(extend-protocol p/FhirType
  Long
  (-type [_] :fhir/long)
  (-interned [_] false)
  (-assoc-id [l id] (long {:id id :value l}))
  (-assoc-extension [l extension] (long {:extension extension :value l}))
  (-value [l] l)
  (-assoc-value [_ value] (long value))
  (-has-primary-content [_] true)
  (-serialize-json [l generator]
    (.writeNumber ^JsonGenerator generator (unchecked-long l)))
  (-has-secondary-content [_] false)
  (-serialize-json-secondary [_ generator]
    (.writeNull ^JsonGenerator generator))
  (-to-xml [l]
    (xml-node/element nil {:value (str l)}))
  (-hash-into [l sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 2))                                   ; :fhir/long
      (.putByte (byte 2)))                                  ; :value
    (system/-hash-into l sink))
  (-references [_]))

(defextended ExtendedLong [id extension ^Long value]
  :fhir-type :fhir/long :hash-num 2)

(def ^{:arglists '([x])} long
  (create-fn (intern/intern-value map->ExtendedLong) ->ExtendedLong
             #(if (clojure.core/int? %) (clojure.core/long %) ::s2/invalid)))

(defn long? [x]
  (identical? :fhir/long (type x)))

;; ---- string ----------------------------------------------------------------

(defn string? [x]
  (instance? blaze.fhir.spec.type.String x))

(defn- map->String [{:keys [id extension value]}]
  (blaze.fhir.spec.type.String. id extension value))

(def string
  (let [intern-extended (intern/intern-value map->String)]
    (fn [data]
      (if (clojure.core/string? data)
        (blaze.fhir.spec.type.String. nil nil data)
        (let [{:keys [id extension value]} data]
          (if (and (nil? id) (p/-interned extension) (nil? value))
            (intern-extended data)
            (blaze.fhir.spec.type.String. id extension value)))))))

(def-extend-protocol-primitive blaze.fhir.spec.type.String)

(def-print-method-primitive string "id" "extension" "value")

(defmethod print-dup blaze.fhir.spec.type.String [^blaze.fhir.spec.type.String s ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.String. ")
  (print-dup (.id s) w)
  (.write w " ")
  (print-dup (.extension s) w)
  (.write w " ")
  (print-dup (.value s) w)
  (.write w ")"))

;; ---- decimal ---------------------------------------------------------------

(defn decimal? [x]
  (instance? Decimal x))

(defn- map->Decimal [{:keys [id extension value]}]
  (Decimal. id extension value))

(def ^{:arglists '([x])} decimal
  (let [intern-extended (intern/intern-value map->Decimal)]
    (fn [x]
      (cond
        (map? x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension) (nil? value))
            (intern-extended x)
            (Decimal. id extension value)))

        (clojure.core/decimal? x) (Decimal. nil nil x)

        :else (ba/incorrect (format "Invalid decimal value `%s`." x))))))

(def-extend-protocol-primitive Decimal)

(def-print-method-primitive decimal "id" "extension" "value")

(defmethod print-dup Decimal [^Decimal e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Decimal. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- uri -------------------------------------------------------------------

(defn uri? [x]
  (instance? Uri x))

(defn- map->Uri [{:keys [id extension value]}]
  (Uri. id extension value))

(def ^{:arglists '([x])} uri
  (let [intern (intern/intern-value #(Uri. nil nil %))
        intern-extended (intern/intern-value map->Uri)]
    (fn [x]
      (if (clojure.core/string? x)
        (intern x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension))
            (intern-extended x)
            (Uri. id extension value)))))))

(def-extend-protocol-primitive Uri
  :value-internable true)

(def-print-method-primitive uri "id" "extension" "value")

(defmethod print-dup Uri [^Uri e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Uri. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- url -------------------------------------------------------------------

(defn url? [x]
  (instance? Url x))

(defn- map->Url [{:keys [id extension value]}]
  (Url. id extension value))

(def ^{:arglists '([x])} url
  (let [intern-extended (intern/intern-value map->Url)]
    (fn [x]
      (if (clojure.core/string? x)
        (Url. nil nil x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension) (nil? value))
            (intern-extended x)
            (Url. id extension value)))))))

(def-extend-protocol-primitive Url)

(def-print-method-primitive url "id" "extension" "value")

(defmethod print-dup Url [^Url e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Url. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- canonical -------------------------------------------------------------

(defn canonical? [x]
  (instance? Canonical x))

(defn- map->Canonical [{:keys [id extension value]}]
  (Canonical. id extension value))

(def ^{:arglists '([x])} canonical
  (let [intern (intern/intern-value #(Canonical. nil nil %))
        intern-extended (intern/intern-value map->Canonical)]
    (fn [x]
      (if (clojure.core/string? x)
        (intern x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension))
            (intern-extended x)
            (Canonical. id extension value)))))))

(def-extend-protocol-primitive Canonical
  :value-internable true)

(def-print-method-primitive canonical "id" "extension" "value")

(defmethod print-dup Canonical [^Canonical e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Canonical. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- base64Binary ----------------------------------------------------------

(defn base64Binary? [x]
  (instance? Base64Binary x))

(defn- map->Base64Binary [{:keys [id extension value]}]
  (Base64Binary. id extension value))

(def ^{:arglists '([x])} base64Binary
  (let [intern-extended (intern/intern-value map->Base64Binary)]
    (fn [x]
      (if (clojure.core/string? x)
        (Base64Binary. nil nil x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension) (nil? value))
            (intern-extended x)
            (Base64Binary. id extension value)))))))

(def-extend-protocol-primitive Base64Binary)

(def-print-method-primitive base64Binary "id" "extension" "value")

(defmethod print-dup Base64Binary [^Base64Binary e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Base64Binary. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- instant ---------------------------------------------------------------

(declare instant)

(defmethod print-method Instant [^Instant instant ^Writer w]
  (doto w
    (.write "#java/instant\"")
    (.write (.toString instant))
    (.write "\"")))

(defmethod print-dup Instant [^Instant instant ^Writer w]
  (.write w "#=(java.time.Instant/ofEpochSecond ")
  (.write w (str (.getEpochSecond instant)))
  (.write w " ")
  (.write w (str (.getNano instant)))
  (.write w ")"))

;; Implementation of a FHIR instant with a variable ZoneOffset.
(deftype OffsetInstant [value]
  p/FhirType
  (-type [_] :fhir/instant)
  (-interned [_] false)
  (-assoc-id [_ id] (instant {:id id :value value}))
  (-assoc-extension [_ extension] (instant {:extension extension :value value}))
  (-value [_] value)
  (-assoc-value [_ val] (instant val))
  (-has-primary-content [_] true)
  (-serialize-json [_ generator]
    (.writeString ^JsonGenerator generator ^String (system/-to-string value)))
  (-has-secondary-content [_] false)
  (-serialize-json-secondary [_ generator]
    (.writeNull ^JsonGenerator generator))
  (-to-xml [_]
    (system-to-xml value))
  (-hash-into [_ sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 9))                                   ; :fhir/instant
      (.putByte (byte 2)))                                  ; :value
    (system/-hash-into value sink))
  (-references [_])
  Object
  (equals [this x]
    (or (identical? this x)
        (and (instance? OffsetInstant x)
             (.equals value (.value ^OffsetInstant x)))))
  (hashCode [_]
    (.hashCode value))
  (toString [_]
    (str value)))

(defmethod print-method OffsetInstant [^OffsetInstant instant ^Writer w]
  (doto w
    (.write "#fhir/instant\"")
    (.write ^String (system/-to-string (.-value instant)))
    (.write "\"")))

(defextended ExtendedOffsetInstant [id extension value]
  :fhir-type :fhir/instant :hash-num 9)

(extend-protocol p/FhirType
  Instant
  (-type [_] :fhir/instant)
  (-interned [_] false)
  (-assoc-id [i id] (instant {:id id :value i}))
  (-assoc-extension [i extension] (instant {:extension extension :value i}))
  (-value [instant] (.atOffset instant ZoneOffset/UTC))
  (-assoc-value [_ value] (instant value))
  (-has-primary-content [_] true)
  (-serialize-json [instant generator]
    (.writeString ^JsonGenerator generator (.format DateTimeFormatter/ISO_INSTANT instant)))
  (-has-secondary-content [_] false)
  (-serialize-json-secondary [_ generator]
    (.writeNull ^JsonGenerator generator))
  (-to-xml [instant]
    (xml-node/element nil {:value (str instant)}))
  (-hash-into [instant sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 9))                                   ; :fhir/instant
      (.putByte (byte 2)))                                  ; :value
    (system/-hash-into (value instant) sink))
  (-references [_]))

(defn- at-utc [instant]
  (.atOffset ^Instant instant ZoneOffset/UTC))

(defextended ExtendedInstant [id extension ^Instant value]
  :fhir-type :fhir/instant :hash-num 9 :value-form (some-> value at-utc))

(defn- parse-instant-value [value]
  (try
    (cond
      (str/ends-with? value "Z") (Instant/parse value)
      (str/ends-with? value "+00:00") (Instant/parse (str (subs value 0 (- (count value) 6)) "Z"))
      :else (OffsetDateTime/parse value))
    (catch DateTimeException _
      ::s2/invalid)))

(def ^{:arglists '([x])} instant
  (let [intern (intern/intern-value map->ExtendedInstant)]
    (fn [x]
      (cond
        (map? x)
        (let [{:keys [id extension value]} x
              value (cond-> value (clojure.core/string? value) parse-instant-value)]
          (cond
            (and (nil? value) (p/-interned extension) (nil? id))
            (intern {:extension extension})

            (and (nil? extension) (nil? id))
            (if (instance? OffsetDateTime value)
              (OffsetInstant. value)
              value)

            :else
            (if (instance? OffsetDateTime value)
              (ExtendedOffsetInstant. id extension value)
              (ExtendedInstant. id extension value))))

        (instance? OffsetDateTime x)
        (if (= ZoneOffset/UTC (.getOffset ^OffsetDateTime x))
          (.toInstant ^OffsetDateTime x)
          (OffsetInstant. x))

        :else
        (let [value (parse-instant-value x)]
          (if (instance? OffsetDateTime value)
            (OffsetInstant. value)
            value))))))

(defn xml->Instant
  {:arglists '([element])}
  [{{:keys [id value]} :attrs content :content}]
  (let [extension (seq content)]
    (if (or id extension)
      (instant {:id id :extension extension :value value})
      (instant value))))

(defn instant? [x]
  (identical? :fhir/instant (type x)))

;; -- date --------------------------------------------------------------------

(declare date)
(declare create-date)
(declare map->ExtendedDate)

(deftype DateYear [^int year]
  p/FhirType
  (-type [_] :fhir/date)
  (-interned [_] false)
  (-assoc-id [d id] (date {:id id :value (p/-value d)}))
  (-assoc-extension [d extension]
    (date {:extension extension :value (p/-value d)}))
  (-value [_] (system/date year))
  (-assoc-value [_ value] (create-date value))
  (-has-primary-content [_] true)
  (-serialize-json [date generator]
    (.writeString ^JsonGenerator generator (str (p/-value date))))
  (-has-secondary-content [_] false)
  (-serialize-json-secondary [_ generator]
    (.writeNull ^JsonGenerator generator))
  (-to-xml [date]
    (xml-node/element nil {:value (str (p/-value date))}))
  (-hash-into [date sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 10))                                  ; :fhir/date
      (.putByte (byte 2)))                                  ; :value
    (system/-hash-into (p/-value date) sink))
  (-references [_])
  ILookup
  (valAt [date key]
    (.valAt date key nil))
  (valAt [date key not-found]
    (if (identical? :value key)
      (p/-value date)
      not-found))
  Object
  (equals [date x]
    (or (identical? date x)
        (and (instance? DateYear x)
             (= year (.year ^DateYear x)))))
  (hashCode [_]
    year)
  (toString [date]
    (str (p/-value date))))

(defmethod print-method DateYear [^DateYear date ^Writer w]
  (.write w "#fhir/date\"")
  (.write w (str date))
  (.write w "\""))

(deftype DateYearMonth [^int year ^int month]
  p/FhirType
  (-type [_] :fhir/date)
  (-interned [_] false)
  (-assoc-id [d id] (date {:id id :value (p/-value d)}))
  (-assoc-extension [d extension]
    (date {:extension extension :value (p/-value d)}))
  (-value [_] (system/date year month))
  (-assoc-value [_ value] (create-date value))
  (-has-primary-content [_] true)
  (-serialize-json [date generator]
    (.writeString ^JsonGenerator generator (str (p/-value date))))
  (-has-secondary-content [_] false)
  (-serialize-json-secondary [_ generator]
    (.writeNull ^JsonGenerator generator))
  (-to-xml [date]
    (xml-node/element nil {:value (str (p/-value date))}))
  (-hash-into [date sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 10))                                  ; :fhir/date
      (.putByte (byte 2)))                                  ; :value
    (system/-hash-into (p/-value date) sink))
  (-references [_])
  ILookup
  (valAt [date key]
    (.valAt date key nil))
  (valAt [date key not-found]
    (if (identical? :value key)
      (p/-value date)
      not-found))
  Object
  (equals [date x]
    (or (identical? date x)
        (and (instance? DateYearMonth x)
             (.equals ^Object (p/-value date) (p/-value x)))))
  (hashCode [date]
    (.hashCode ^Object (p/-value date)))
  (toString [date]
    (str (p/-value date))))

(defmethod print-method DateYearMonth [^DateYearMonth date ^Writer w]
  (.write w "#fhir/date\"")
  (.write w (str date))
  (.write w "\""))

(deftype DateDate [^int year ^int month ^int day]
  p/FhirType
  (-type [_] :fhir/date)
  (-interned [_] false)
  (-assoc-id [d id] (date {:id id :value (p/-value d)}))
  (-assoc-extension [d extension]
    (date {:extension extension :value (p/-value d)}))
  (-value [_] (system/date year month day))
  (-assoc-value [_ value] (create-date value))
  (-has-primary-content [_] true)
  (-serialize-json [date generator]
    (.writeString ^JsonGenerator generator (str (p/-value date))))
  (-has-secondary-content [_] false)
  (-serialize-json-secondary [_ generator]
    (.writeNull ^JsonGenerator generator))
  (-to-xml [date]
    (xml-node/element nil {:value (str (p/-value date))}))
  (-hash-into [date sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 10))                                  ; :fhir/date
      (.putByte (byte 2)))                                  ; :value
    (system/-hash-into (p/-value date) sink))
  (-references [_])
  ILookup
  (valAt [date key]
    (.valAt date key nil))
  (valAt [date key not-found]
    (if (identical? :value key)
      (p/-value date)
      not-found))
  Object
  (equals [date x]
    (or (identical? date x)
        (and (instance? DateDate x)
             (.equals ^Object (p/-value date) (p/-value x)))))
  (hashCode [date]
    (.hashCode ^Object (p/-value date)))
  (toString [date]
    (str (p/-value date))))

(defmethod print-method DateDate [^DateDate date ^Writer w]
  (.write w "#fhir/date\"")
  (.write w (str date))
  (.write w "\""))

(defextended ExtendedDate [id extension value]
  :fhir-type :fhir/date :hash-num 10)

(defn create-date [system-date]
  (condp = (class system-date)
    blaze.fhir.spec.type.system.DateYear
    (DateYear.
     (.year ^blaze.fhir.spec.type.system.DateYear system-date))
    blaze.fhir.spec.type.system.DateYearMonth
    (DateYearMonth.
     (.year ^blaze.fhir.spec.type.system.DateYearMonth system-date)
     (.month ^blaze.fhir.spec.type.system.DateYearMonth system-date))
    blaze.fhir.spec.type.system.DateDate
    (DateDate.
     (.year ^blaze.fhir.spec.type.system.DateDate system-date)
     (.month ^blaze.fhir.spec.type.system.DateDate system-date)
     (.day ^blaze.fhir.spec.type.system.DateDate system-date))))

(defn- parse-date [s]
  (try
    (create-date (Date/parse s))
    (catch DateTimeException _
      ::s2/invalid)))

(def ^{:arglists '([x])} date
  (let [intern (intern/intern-value map->ExtendedDate)]
    (fn [x]
      (cond
        (map? x)
        (let [{:keys [id extension value]} x
              value (cond-> value (clojure.core/string? value) system/parse-date)]
          (cond
            (ba/anomaly? value)
            ::s2/invalid

            (and (nil? value) (p/-interned extension) (nil? id))
            (intern {:extension extension})

            (and (nil? extension) (nil? id))
            (create-date value)

            :else
            (ExtendedDate. id extension value)))
        (system/date? x) (create-date x)
        (clojure.core/string? x) (parse-date x)
        :else ::s2/invalid))))

(defn xml->Date
  "Creates a primitive date value from XML `element`."
  {:arglists '([element])}
  [{{:keys [id value]} :attrs content :content}]
  (let [extension (seq content)]
    (if (or id extension)
      (if value
        (if-ok [value (system/parse-date value)]
          (date {:id id :extension extension :value value})
          (fn [_] ::s2/invalid))
        (date {:id id :extension extension}))
      (date value))))

(defn date? [x]
  (identical? :fhir/date (type x)))

;; -- dateTime ----------------------------------------------------------------

;(declare dateTime)
;(declare create-date-time)
;(declare map->ExtendedDateTime)

#_(deftype DateTimeYear [^int year]
    p/FhirType
    (-type [_] :fhir/dateTime)
    (-interned [_] false)
    (-assoc-id [d id] (dateTime {:id id :value (p/-value d)}))
    (-assoc-extension [d extension]
      (dateTime {:extension extension :value (p/-value d)}))
    (-value [_] (system/date-time year))
    (-assoc-value [_ value] (create-date-time value))
    (-has-primary-content [_] true)
    (-serialize-json [date-time generator]
      (.writeString ^JsonGenerator generator (str (p/-value date-time))))
    (-has-secondary-content [_] false)
    (-serialize-json-secondary [_ generator]
      (.writeNull ^JsonGenerator generator))
    (-to-xml [date-time]
      (xml-node/element nil {:value (str (p/-value date-time))}))
    (-hash-into [date-time sink]
      (doto ^PrimitiveSink sink
        (.putByte (byte 11))                                ; :fhir/dateTime
        (.putByte (byte 2)))                                ; :value
      (system/-hash-into (p/-value date-time) sink))
    (-references [_])
    ILookup
    (valAt [date-time key]
      (.valAt date-time key nil))
    (valAt [date-time key not-found]
      (if (identical? :value key)
        (p/-value date-time)
        not-found))
    Object
    (equals [date-time x]
      (or (identical? date-time x)
          (and (instance? DateTimeYear x)
               (= year (.year ^DateTimeYear x)))))
    (hashCode [_]
      year)
    (toString [date-time]
      (str (p/-value date-time))))

#_(defmethod print-method DateTimeYear [^DateTimeYear date-time ^Writer w]
    (.write w "#fhir/dateTime\"")
    (.write w (str date-time))
    (.write w "\""))

#_(deftype DateTimeYearMonth [^int year ^int month]
    p/FhirType
    (-type [_] :fhir/dateTime)
    (-interned [_] false)
    (-assoc-id [d id] (dateTime {:id id :value (p/-value d)}))
    (-assoc-extension [d extension]
      (dateTime {:extension extension :value (p/-value d)}))
    (-value [_] (system/date-time year month))
    (-assoc-value [_ value] (create-date-time value))
    (-has-primary-content [_] true)
    (-serialize-json [date-time generator]
      (.writeString ^JsonGenerator generator (str (p/-value date-time))))
    (-has-secondary-content [_] false)
    (-serialize-json-secondary [_ generator]
      (.writeNull ^JsonGenerator generator))
    (-to-xml [date-time]
      (xml-node/element nil {:value (str (p/-value date-time))}))
    (-hash-into [date-time sink]
      (doto ^PrimitiveSink sink
        (.putByte (byte 11))                                ; :fhir/dateTime
        (.putByte (byte 2)))                                ; :value
      (system/-hash-into (p/-value date-time) sink))
    (-references [_])
    ILookup
    (valAt [date-time key]
      (.valAt date-time key nil))
    (valAt [date-time key not-found]
      (if (identical? :value key)
        (p/-value date-time)
        not-found))
    Object
    (equals [date-time x]
      (or (identical? date-time x)
          (and (instance? DateTimeYearMonth x)
               (.equals ^Object (p/-value date-time) (p/-value x)))))
    (hashCode [date-time]
      (.hashCode ^Object (p/-value date-time)))
    (toString [date]
      (str (p/-value date))))

#_(defmethod print-method DateTimeYearMonth [^DateTimeYearMonth date-time ^Writer w]
    (.write w "#fhir/dateTime\"")
    (.write w (str date-time))
    (.write w "\""))

#_(deftype DateTimeDate [^int year ^int month ^int day]
    p/FhirType
    (-type [_] :fhir/dateTime)
    (-interned [_] false)
    (-assoc-id [d id] (dateTime {:id id :value (p/-value d)}))
    (-assoc-extension [d extension]
      (dateTime {:extension extension :value (p/-value d)}))
    (-value [_] (system/date-time year month day))
    (-assoc-value [_ value] (create-date-time value))
    (-has-primary-content [_] true)
    (-serialize-json [date-time generator]
      (.writeString ^JsonGenerator generator (str (p/-value date-time))))
    (-has-secondary-content [_] false)
    (-serialize-json-secondary [_ generator]
      (.writeNull ^JsonGenerator generator))
    (-to-xml [date-time]
      (xml-node/element nil {:value (str (p/-value date-time))}))
    (-hash-into [date-time sink]
      (doto ^PrimitiveSink sink
        (.putByte (byte 11))                                ; :fhir/dateTime
        (.putByte (byte 2)))                                ; :value
      (system/-hash-into (p/-value date-time) sink))
    (-references [_])
    ILookup
    (valAt [date-time key]
      (.valAt date-time key nil))
    (valAt [date-time key not-found]
      (if (identical? :value key)
        (p/-value date-time)
        not-found))
    Object
    (equals [date-time x]
      (or (identical? date-time x)
          (and (instance? DateTimeDate x)
               (.equals ^Object (p/-value date-time) (p/-value x)))))
    (hashCode [date-time]
      (.hashCode ^Object (p/-value date-time)))
    (toString [date-time]
      (str (p/-value date-time))))

#_(defmethod print-method DateTimeDate [^DateTimeDate date-time ^Writer w]
    (.write w "#fhir/dateTime\"")
    (.write w (str date-time))
    (.write w "\""))

#_(extend-protocol p/FhirType
    OffsetDateTime
    (-type [_] :fhir/dateTime)
    (-interned [_] false)
    (-assoc-id [d id] (dateTime {:id id :value d}))
    (-assoc-extension [d extension]
      (dateTime {:extension extension :value d}))
    (-value [date-time] date-time)
    (-assoc-value [_ value] (create-date-time value))
    (-has-primary-content [_] true)
    (-serialize-json [date-time generator]
      (.writeString ^JsonGenerator generator (.format DateTimeFormatter/ISO_DATE_TIME date-time)))
    (-has-secondary-content [_] false)
    (-serialize-json-secondary [_ generator]
      (.writeNull ^JsonGenerator generator))
    (-to-xml [date-time]
      (system-to-xml date-time))
    (-hash-into [date-time sink]
      (doto ^PrimitiveSink sink
        (.putByte (byte 11))                                ; :fhir/dateTime
        (.putByte (byte 2)))                                ; :value
      (system/-hash-into date-time sink))
    (-references [_])

    LocalDateTime
    (-type [_] :fhir/dateTime)
    (-interned [_] false)
    (-assoc-id [d id] (dateTime {:id id :value d}))
    (-assoc-extension [d extension]
      (dateTime {:extension extension :value d}))
    (-value [date-time] date-time)
    (-assoc-value [_ value] (create-date-time value))
    (-has-primary-content [_] true)
    (-serialize-json [date-time generator]
      (.writeString ^JsonGenerator generator (.format DateTimeFormatter/ISO_LOCAL_DATE_TIME date-time)))
    (-has-secondary-content [_] false)
    (-serialize-json-secondary [_ generator]
      (.writeNull ^JsonGenerator generator))
    (-to-xml [date-time]
      (system-to-xml date-time))
    (-hash-into [date-time sink]
      (doto ^PrimitiveSink sink
        (.putByte (byte 11))                                ; :fhir/dateTime
        (.putByte (byte 2)))                                ; :value
      (system/-hash-into date-time sink))
    (-references [_]))

#_(defextended ExtendedDateTime [id extension value]
    :fhir-type :fhir/dateTime :hash-num 11)

#_(defn create-date-time [system-date-time]
    (condp = (class system-date-time)
      blaze.fhir.spec.type.system.DateTimeYear
      (DateTimeYear.
       (.year ^blaze.fhir.spec.type.system.DateTimeYear system-date-time))
      blaze.fhir.spec.type.system.DateTimeYearMonth
      (DateTimeYearMonth.
       (.year ^blaze.fhir.spec.type.system.DateTimeYearMonth system-date-time)
       (.month ^blaze.fhir.spec.type.system.DateTimeYearMonth system-date-time))
      blaze.fhir.spec.type.system.DateTimeDate
      (DateTimeDate.
       (.year ^blaze.fhir.spec.type.system.DateTimeDate system-date-time)
       (.month ^blaze.fhir.spec.type.system.DateTimeDate system-date-time)
       (.day ^blaze.fhir.spec.type.system.DateTimeDate system-date-time))
      system-date-time))

#_(def ^{:arglists '([x])} dateTime
    (let [intern (intern/intern-value map->ExtendedDateTime)]
      (fn [x]
        (cond
          (map? x)
          (let [{:keys [id extension value]} x
                value (cond-> value (clojure.core/string? value) system/parse-date-time)]
            (cond
              (ba/anomaly? value)
              ::s2/invalid

              (and (nil? value) (p/-interned extension) (nil? id))
              (intern {:extension extension})

              (and (nil? extension) (nil? id))
              (create-date-time value)

              :else
              (ExtendedDateTime. id extension value)))
          (system/date-time? x) (create-date-time x)
          (clojure.core/string? x) (parse-date-time x)
          :else ::s2/invalid))))

(defn dateTime? [x]
  (instance? DateTime x))

(defn- map->DateTime [{:keys [id extension value]}]
  (DateTime. id extension value))

(defn create-date-time [system-date-time]
  (DateTime. nil nil system-date-time))

(defn- parse-date-time [value]
  (try
    (create-date-time (system/parse-date-time* value))
    (catch DateTimeException _
      ::s2/invalid)))

(def ^{:arglists '([x])} dateTime
  (let [intern-extended (intern/intern-value map->DateTime)]
    (fn [x]
      (cond
        (map? x)
        (let [{:keys [id extension value]} x]
          (cond
            (ba/anomaly? value)
            ::s2/invalid

            (and (nil? value) (p/-interned extension) (nil? id))
            (intern-extended {:extension extension})

            :else
            (DateTime. id extension (cond-> value (clojure.core/string? value)
                                                  system/parse-date-time))))
        (system/date-time? x) (create-date-time x)
        (clojure.core/string? x) (parse-date-time x)
        :else ::s2/invalid))))

(defn xml->DateTime
  "Creates a primitive dateTime value from XML `element`."
  {:arglists '([element])}
  [{{:keys [id value]} :attrs content :content}]
  (let [extension (vec content)]
    (if (or id (seq extension))
      (if value
        (if-ok [value (system/parse-date-time value)]
          (dateTime {:id id :extension extension :value value})
          (fn [_] ::s2/invalid))
        (dateTime {:id id :extension extension}))
      (dateTime value))))

(def-extend-protocol-primitive DateTime)

(def-print-method-primitive dateTime "id" "extension" "value")

(defmethod print-dup DateTime [^DateTime e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.DateTime. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- time ------------------------------------------------------------------

(declare time)

(extend-protocol p/FhirType
  LocalTime
  (-type [_] :fhir/time)
  (-interned [_] false)
  (-assoc-id [t id] (time {:id id :value t}))
  (-assoc-extension [t extension]
    (time {:extension extension :value t}))
  (-value [time] time)
  (-assoc-value [_ value] value)
  (-has-primary-content [_] true)
  (-serialize-json [time generator]
    (.writeString ^JsonGenerator generator ^String (system/-to-string time)))
  (-has-secondary-content [_] false)
  (-serialize-json-secondary [_ generator]
    (.writeNull ^JsonGenerator generator))
  (-to-xml [time]
    (system-to-xml time))
  (-hash-into [time sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 12))                                  ; :fhir/time
      (.putByte (byte 2)))                                  ; :value
    (system/-hash-into time sink))
  (-references [_]))

(defextended ExtendedTime [id extension value]
  :fhir-type :fhir/time :hash-num 12)

(defn- parse-time [s]
  (try
    (LocalTime/parse s)
    (catch DateTimeException _
      ::s2/invalid)))

(def ^{:arglists '([x])} time
  (let [intern (intern/intern-value map->ExtendedTime)]
    (fn [x]
      (cond
        (map? x)
        (let [{:keys [id extension value]} x
              value (cond-> value (clojure.core/string? value) system/parse-time)]
          (cond
            (ba/anomaly? value)
            ::s2/invalid

            (and (nil? value) (p/-interned extension) (nil? id))
            (intern {:extension extension})

            (and (nil? extension) (nil? id))
            value

            :else
            (ExtendedTime. id extension value)))
        (system/time? x) x
        (clojure.core/string? x) (parse-time x)
        :else ::s2/invalid))))

(defn xml->Time
  "Creates a primitive time value from XML `element`."
  {:arglists '([element])}
  [{{:keys [id value]} :attrs content :content}]
  (let [extension (seq content)]
    (if (or id extension)
      (time {:id id :extension extension :value value})
      (time value))))

(defn time? [x]
  (identical? :fhir/time (type x)))

;; ---- code ------------------------------------------------------------------

(defn code? [x]
  (instance? Code x))

(defn- map->Code [{:keys [id extension value]}]
  (Code. id extension value))

(def code
  (let [intern (intern/intern-value #(Code. nil nil %))
        intern-extended (intern/intern-value map->Code)]
    (fn [data]
      (if (clojure.core/string? data)
        (intern data)
        (let [{:keys [id extension value]} data]
          (if (and (nil? id) (p/-interned extension))
            (intern-extended data)
            (Code. id extension value)))))))

(def-extend-protocol-primitive Code
  :value-internable true)

(def-print-method-primitive code "id" "extension" "value")

(defmethod print-dup Code [^Code e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Code. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- oid -------------------------------------------------------------------

(defn oid? [x]
  (instance? Oid x))

(defn- map->Oid [{:keys [id extension value]}]
  (Oid. id extension value))

(def ^{:arglists '([x])} oid
  (let [intern-extended (intern/intern-value map->Oid)]
    (fn [x]
      (if (clojure.core/string? x)
        (Oid. nil nil x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension) (nil? value))
            (intern-extended x)
            (Oid. id extension value)))))))

(def-extend-protocol-primitive Oid)

(def-print-method-primitive oid "id" "extension" "value")

(defmethod print-dup Oid [^Oid e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Oid. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- id --------------------------------------------------------------------

(defn id? [x]
  (instance? Id x))

(defn- map->Id [{:keys [id extension value]}]
  (Id. id extension value))

(def ^{:arglists '([x])} id
  (let [intern-extended (intern/intern-value map->Id)]
    (fn [x]
      (if (clojure.core/string? x)
        (Id. nil nil x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension) (nil? value))
            (intern-extended x)
            (Id. id extension value)))))))

(def-extend-protocol-primitive Id)

(def-print-method-primitive id "id" "extension" "value")

(defmethod print-dup Id [^Id e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Id. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- markdown --------------------------------------------------------------

(defn markdown? [x]
  (instance? Markdown x))

(defn- map->Markdown [{:keys [id extension value]}]
  (Markdown. id extension value))

(def ^{:arglists '([x])} markdown
  (let [intern-extended (intern/intern-value map->Markdown)]
    (fn [x]
      (if (map? x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension) (nil? value))
            (intern-extended x)
            (Markdown. id extension value)))
        (Markdown. nil nil x)))))

(def-extend-protocol-primitive Markdown)

(def-print-method-primitive markdown "id" "extension" "value")

(defmethod print-dup Markdown [^Markdown e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Markdown. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- unsignedInt -----------------------------------------------------------

(defn unsignedInt? [x]
  (instance? UnsignedInt x))

(defn- map->UnsignedInt [{:keys [id extension value]}]
  (UnsignedInt. id extension (some-> value int)))

(def ^{:arglists '([x])} unsignedInt
  (let [intern-extended (intern/intern-value map->UnsignedInt)]
    (fn [x]
      (cond
        (map? x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension) (nil? value))
            (intern-extended x)
            (UnsignedInt. id extension (some-> value int))))

        (int? x) (UnsignedInt. nil nil (int x))

        :else (ba/incorrect (format "Invalid unsignedInt value `%s`." x))))))

(def-extend-protocol-primitive UnsignedInt)

(def-print-method-primitive unsignedInt "id" "extension" "value")

(defmethod print-dup UnsignedInt [^UnsignedInt e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.UnsignedInt. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- positiveInt -----------------------------------------------------------

(defn positiveInt? [x]
  (instance? PositiveInt x))

(defn- map->PositiveInt [{:keys [id extension value]}]
  (PositiveInt. id extension (some-> value int)))

(def ^{:arglists '([x])} positiveInt
  (let [intern-extended (intern/intern-value map->PositiveInt)]
    (fn [x]
      (cond
        (map? x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension) (nil? value))
            (intern-extended x)
            (PositiveInt. id extension (some-> value int))))

        (int? x) (PositiveInt. nil nil (int x))

        :else (ba/incorrect (format "Invalid positiveInt value `%s`." x))))))

(def-extend-protocol-primitive PositiveInt)

(def-print-method-primitive positiveInt "id" "extension" "value")

(defmethod print-dup PositiveInt [^PositiveInt e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.PositiveInt. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- uuid ------------------------------------------------------------------

(defn uuid? [x]
  (instance? Uuid x))

(defn- map->Uuid [{:keys [id extension value]}]
  (Uuid. id extension value))

(def ^{:arglists '([x])} uuid
  (let [intern-extended (intern/intern-value map->Uuid)]
    (fn [x]
      (if (map? x)
        (let [{:keys [id extension value]} x]
          (if (and (nil? id) (p/-interned extension) (nil? value))
            (intern-extended x)
            (Uuid. id extension value)))
        (Uuid. nil nil x)))))

(def-extend-protocol-primitive Uuid)

(def-print-method-primitive uuid "id" "extension" "value")

(defmethod print-dup Uuid [^Uuid e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Uuid. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- xhtml -----------------------------------------------------------------

(defn- wrap-div [s]
  (str "<div xmlns=\"http://www.w3.org/1999/xhtml\">" s "</div>"))

(defn- parse-xhtml* [s]
  (let [xml (xml/parse-str s)]
    ;; simply emit the xml in order to parse eager and see all exceptions
    (xml/emit-str xml)
    xml))

(defn- parse-xhtml [s]
  (try
    (parse-xhtml* s)
    (catch Exception _
      (-> s
          (str/replace "<" "&lt;")
          (str/replace ">" "&gt;")
          (wrap-div)
          (parse-xhtml*)))))

(declare ->Xhtml)

(deftype Xhtml [value]
  p/FhirType
  (-type [_] :fhir/xhtml)
  (-interned [_] false)
  (-value [_] value)
  (-assoc-value [_ val] (->Xhtml val))
  (-has-primary-content [_] true)
  (-serialize-json [_ generator]
    (.writeString ^JsonGenerator generator ^String value))
  (-has-secondary-content [_] false)
  (-serialize-json-secondary [_ generator]
    (.writeNull ^JsonGenerator generator))
  (-to-xml [_] (update (parse-xhtml value) :attrs assoc :xmlns "http://www.w3.org/1999/xhtml"))
  (-hash-into [_ sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 20))                                  ; :fhir/xhtml
      (.putByte (byte 2)))                                  ; :value
    (system/-hash-into value sink))
  (-references [_])
  Object
  (equals [_ x]
    (and (instance? Xhtml x) (= value (.value ^Xhtml x))))
  (hashCode [_]
    (.hashCode value))
  (toString [_]
    (str value)))

(defmethod print-method Xhtml [xhtml ^Writer w]
  (.write w "#fhir/xhtml\"")
  (.write w ^String (value xhtml))
  (.write w "\""))

(def ^:const xml-preamble-length
  (count "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))

(defn xml->Xhtml
  "Creates a xhtml from XML `element`."
  [element]
  (->Xhtml (subs (xml/emit-str element) xml-preamble-length)))

(defn xhtml? [x]
  (instance? Xhtml x))

;; ---- Complex Types --------------------------------------------------------

(extend-protocol p/FhirType
  List
  (-type [_])
  (-interned [xs]
    (reduce #(if (p/-interned %2) %1 (reduced false)) true xs))
  (-value [_])
  (-has-primary-content [xs]
    (reduce #(when (p/-has-primary-content %2) (reduced true)) nil xs))
  (-serialize-json [xs generator]
    (.writeStartArray ^JsonGenerator generator)
    (run! #(p/-serialize-json % generator) xs)
    (.writeEndArray ^JsonGenerator generator))
  (-has-secondary-content [xs]
    (reduce #(when (p/-has-secondary-content %2) (reduced true)) nil xs))
  (-serialize-json-secondary [xs generator]
    (.writeStartArray ^JsonGenerator generator)
    (reduce #(p/-serialize-json-secondary %2 generator) nil xs)
    (.writeEndArray ^JsonGenerator generator))
  (-hash-into [xs sink]
    (.putByte ^PrimitiveSink sink (byte 36))
    (reduce #(p/-hash-into %2 sink) nil xs))
  (-references [xs]
    (reduce #(into %1 (p/-references %2)) [] xs))
  Keyword
  (-type [_])
  (-value [_])
  (-hash-into [k sink]
    (.putInt ^PrimitiveSink sink (.hasheq k)))
  (-references [_])
  IPersistentMap
  (-type [m]
    (.valAt m :fhir/type))
  (-interned [_] false)
  (-assoc-id [m id] (assoc m :id id))
  (-assoc-extension [m extension] (assoc m :extension extension))
  (-value [_])
  (-assoc-value [m value] (assoc m :value value))
  (-has-primary-content [_] true)
  (-serialize-json [m generator]
    (.writeStartObject ^JsonGenerator generator)
    (run!
     (fn [^Map$Entry e]
       (let [^Keyword key (.getKey e)]
         (when-not (identical? :fhir/type key)
           (when-some [v (.getValue e)]
             (json/write-field generator (json/field-name (.getName key)) v)))))
     m)
    (.writeEndObject ^JsonGenerator generator))
  (-has-secondary-content [_] false)
  (-serialize-json-secondary [m _]
    (throw (ex-info "A complex type/resource has no secondary content." m)))
  (-hash-into [m sink]
    (.putByte ^PrimitiveSink sink (byte 37))
    (run!
     (fn [^Map$Entry e]
       (p/-hash-into (.getKey e) sink)
       (p/-hash-into (.getValue e) sink))
     (sort
      (reify Comparator
        (compare [_ e1 e2]
          (.compareTo ^Keyword (.getKey ^Map$Entry e1) (.getKey ^Map$Entry e2))))
      m)))
  (-references [m]
   ;; Bundle entries have no references, because Bundles itself are stored "as-is"
    (when-not (identical? :fhir.Bundle/entry (p/-type m))
      (transduce (mapcat p/-references) conj [] (vals m)))))

(defmacro def-print-method-complex [name & data-element-names]
  `(defmethod print-method ~(symbol name)
     [~(with-meta 'v {:tag (symbol name)}) ~(with-meta 'w {:tag 'Writer})]
     (print-type ~name ~@data-element-names)))

;; ---- Attachment ------------------------------------------------------------

(defn- map->Attachment [{:keys [id extension contentType language data url size hash title creation]}]
  (Attachment. id extension contentType language data url size hash title creation))

(def ^{:arglists '([x])} attachment
  (let [intern (intern/intern-value map->Attachment)]
    (fn [{:keys [id extension contentType language data url size hash title creation] :as x}]
      (if (and (nil? id) (p/-interned extension) (p/-interned contentType)
               (p/-interned language) (p/-interned data) (p/-interned url)
               (p/-interned size) (p/-interned hash) (p/-interned title)
               (p/-interned creation))
        (intern x)
        (Attachment. id extension contentType language data url size hash title creation)))))

(extend-protocol p/FhirType
  Attachment
  (-type [e]
    (.fhirType e))
  (-interned [e]
    (and (nil? (.id e)) (p/-interned (.extension e))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Attachment" "id" "extension" "contentType" "language"
                          "data" "url" "size" "hash" "title" "creation")

(defmethod print-dup Attachment [^Attachment e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Attachment. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.contentType e) w)
  (.write w " ")
  (print-dup (.language e) w)
  (.write w " ")
  (print-dup (.data e) w)
  (.write w " ")
  (print-dup (.url e) w)
  (.write w " ")
  (print-dup (.size e) w)
  (.write w " ")
  (print-dup (.hash e) w)
  (.write w " ")
  (print-dup (.title e) w)
  (.write w " ")
  (print-dup (.creation e) w)
  (.write w ")"))

;; ---- Extension --------------------------------------------------------

(defn- map->Extension [{:keys [id extension url value]}]
  (Extension. id extension url value))

(def ^{:arglists '([x])} extension
  (let [intern (intern/intern-value map->Extension)]
    (fn [{:keys [id extension url value] :as x}]
      (if (and (nil? id) (p/-interned extension) (p/-interned value))
        (intern x)
        (Extension. id extension url value)))))

(extend-protocol p/FhirType
  Extension
  (-type [e]
    (.fhirType e))
  (-interned [e]
    (and (nil? (.id e)) (p/-interned (.extension e)) (p/-interned (.value e))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (-> (transient [])
        (into! (p/-references (.extension e)))
        (into! (p/-references (.value e)))
        (persistent!))))

(def-print-method-complex "Extension" "id" "extension" "url")

(defmethod print-dup Extension [^Extension e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Extension. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.url e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w ")"))

;; ---- Coding ----------------------------------------------------------------

(defn- map->Coding [{:keys [id extension system version code display userSelected]}]
  (Coding. id extension system version code display userSelected))

(def coding
  (let [intern (intern/intern-value map->Coding)]
    (fn [{:keys [id extension system version code display userSelected] :as x}]
      (if (and (nil? id) (p/-interned extension))
        (intern x)
        (Coding. id extension system version code display userSelected)))))

(extend-protocol p/FhirType
  Coding
  (-type [e]
    (.fhirType e))
  (-interned [e]
    (and (nil? (.id e)) (p/-interned (.extension e))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Coding" "id" "extension" "system" "version" "code"
                          "display" "userSelected")

(defmethod print-dup Coding [^Coding e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Coding. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.system e) w)
  (.write w " ")
  (print-dup (.version e) w)
  (.write w " ")
  (print-dup (.code e) w)
  (.write w " ")
  (print-dup (.display e) w)
  (.write w " ")
  (print-dup (.userSelected e) w)
  (.write w ")"))

;; ---- CodeableConcept -------------------------------------------------------

(defn- map->CodeableConcept [{:keys [id extension coding text]}]
  (CodeableConcept. id extension coding text))

(def ^{:arglists '([x])} codeable-concept
  (let [intern (intern/intern-value map->CodeableConcept)]
    (fn [{:keys [id extension coding text] :as x}]
      (if (and (nil? id) (p/-interned extension))
        (intern x)
        (CodeableConcept. id extension coding text)))))

(extend-protocol p/FhirType
  CodeableConcept
  (-type [e]
    (.fhirType e))
  (-interned [e]
    (and (nil? (.id e)) (p/-interned (.extension e))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "CodeableConcept" "id" "extension" "coding" "text")

(defmethod print-dup CodeableConcept [^CodeableConcept e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.CodeableConcept. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.coding e) w)
  (.write w " ")
  (print-dup (.text e) w)
  (.write w ")"))

;; ---- Quantity --------------------------------------------------------------

(declare quantity)

(def-complex-type Quantity
  [^String id extension ^:primitive value ^:primitive comparator
   ^:primitive-string unit ^:primitive system ^:primitive code]
  :hash-num 40
  :interned (and (nil? id) (p/-interned extension) (nil? value)))

(declare ratio)

(def-complex-type Ratio [^String id extension numerator denominator]
  :hash-num 48)

;; ---- Period ----------------------------------------------------------------

(defn- map->Period [{:keys [id extension start end]}]
  (Period. id extension start end))

(def ^{:arglists '([x])} period
  (let [intern (intern/intern-value map->Period)]
    (fn [{:keys [id extension start end] :as x}]
      (if (and (nil? id) (p/-interned extension) (p/-interned start)
               (p/-interned end))
        (intern x)
        (Period. id extension start end)))))

(extend-protocol p/FhirType
  Period
  (-type [e]
    (.fhirType e))
  (-interned [e]
    (and (nil? (.id e)) (p/-interned (.extension e)) (p/-interned (.start e))
         (p/-interned (.end e))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Period" "id" "extension" "start" "end")

(defmethod print-dup Period [^Period e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Period. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.start e) w)
  (.write w " ")
  (print-dup (.end e) w)
  (.write w ")"))

;; ---- Identifier ------------------------------------------------------------

(defn- map->Identifier [{:keys [id extension use type system value period assigner]}]
  (Identifier. id extension use type system value period assigner))

(def ^{:arglists '([x])} identifier
  (let [intern (intern/intern-value map->Identifier)]
    (fn [{:keys [id extension use type system value period assigner] :as x}]
      (if (and (nil? id) (p/-interned extension) (p/-interned use)
               (p/-interned type) (p/-interned value) (p/-interned period)
               (p/-interned assigner))
        (intern x)
        (Identifier. id extension use type system value period assigner)))))

(extend-protocol p/FhirType
  Identifier
  (-type [e]
    (.fhirType e))
  (-interned [e]
    (and (nil? (.id e)) (p/-interned (.extension e)) (p/-interned (.use e))
         (p/-interned (.type e)) (p/-interned (.value e)) (p/-interned (.period e))
         (p/-interned (.assigner e))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Identifier" "id" "extension" "use" "type" "system"
                          "value" "period" "assigner")

(defmethod print-dup Identifier [^Identifier e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Identifier. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.use e) w)
  (.write w " ")
  (print-dup (.type e) w)
  (.write w " ")
  (print-dup (.system e) w)
  (.write w " ")
  (print-dup (.value e) w)
  (.write w " ")
  (print-dup (.period e) w)
  (.write w " ")
  (print-dup (.assigner e) w)
  (.write w ")"))

;; ---- HumanName -------------------------------------------------------------

(declare human-name)

(def-complex-type HumanName
  [^String id extension ^:primitive use ^:primitive-string text
   ^:primitive-string family ^:primitive-list given ^:primitive-list prefix
   ^:primitive-list suffix period]
  :hash-num 46)

;; ---- Address ---------------------------------------------------------------

(defn- map->Address
  [{:keys [id extension use type text line city district state postalCode
           country period]}]
  (Address. id extension use type text line city district state postalCode
            country period))

(def ^{:arglists '([x])} address
  (let [intern (intern/intern-value map->Address)]
    (fn [{:keys [id extension use type text line city district state postalCode
                 country period] :as x}]
      (if (and (nil? id) (p/-interned extension) (p/-interned use)
               (p/-interned type) (p/-interned text) (p/-interned line)
               (p/-interned city) (p/-interned district) (p/-interned state)
               (p/-interned postalCode) (p/-interned country) (p/-interned period))
        (intern x)
        (Address. id extension use type text line city district state postalCode
                  country period)))))

(extend-protocol p/FhirType
  Address
  (-type [e]
    (.fhirType e))
  (-interned [e]
    (and (nil? (.id e)) (p/-interned (.extension e)) (p/-interned (.use e))
         (p/-interned (.type e)) (p/-interned (.text e)) (p/-interned (.line e))
         (p/-interned (.city e)) (p/-interned (.district e))
         (p/-interned (.state e)) (p/-interned (.postalCode e))
         (p/-interned (.country e)) (p/-interned (.period e))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Address" "id" "extension" "use" "type" "text" "line"
                          "city" "district" "state" "postalCode" "country" "period")

(defmethod print-dup Address [^Address e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Address. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.use e) w)
  (.write w " ")
  (print-dup (.type e) w)
  (.write w " ")
  (print-dup (.text e) w)
  (.write w " ")
  (print-dup (.line e) w)
  (.write w " ")
  (print-dup (.city e) w)
  (.write w " ")
  (print-dup (.district e) w)
  (.write w " ")
  (print-dup (.state e) w)
  (.write w " ")
  (print-dup (.postalCode e) w)
  (.write w " ")
  (print-dup (.country e) w)
  (.write w " ")
  (print-dup (.period e) w)
  (.write w " ")
  (.write w ")"))

;; ---- Reference --------------------------------------------------------

(defn- valid-ref? [[type id]]
  (and (.matches (re-matcher #"[A-Z]([A-Za-z0-9_]){0,254}" type))
       (some->> id (re-matcher #"[A-Za-z0-9\-\.]{1,64}") .matches)))

(defn- reference-reference [ref]
  (let [ref (str/split ref #"/" 2)]
    (when (valid-ref? ref)
      [ref])))

(defn- map->Reference [{:keys [id extension reference type identifier display]}]
  (Reference. id extension reference type identifier display))

(def ^{:arglists '([x])} reference
  (let [intern (intern/intern-value map->Reference)]
    (fn [{:keys [id extension reference type identifier display] :as data}]
      (if (and (nil? id) (p/-interned extension) (p/-interned reference)
               (p/-interned type) (p/-interned identifier) (p/-interned display))
        (intern data)
        (Reference. id extension reference type identifier display)))))

(extend-protocol p/FhirType
  Reference
  (-type [r]
    (.fhirType r))
  (-interned [r]
    (and (nil? (.id r)) (p/-interned (.extension r))
         (p/-interned (.reference r)) (p/-interned (.type r))
         (p/-interned (.identifier r)) (p/-interned (.display r))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [r]
    (-> (transient (or (some-> (.reference r) :value reference-reference) []))
        (into! (p/-references (.extension r)))
        (into! (p/-references (.type r)))
        (into! (p/-references (.identifier r)))
        (into! (p/-references (.display r)))
        (persistent!))))

(def-print-method-complex "Reference" "id" "extension" "reference" "type"
                          "identifier" "display")

(defmethod print-dup Reference [^Reference e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Reference. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.reference e) w)
  (.write w " ")
  (print-dup (.type e) w)
  (.write w " ")
  (print-dup (.identifier e) w)
  (.write w " ")
  (print-dup (.display e) w)
  (.write w ")"))

;; ---- Meta ------------------------------------------------------------------

(declare meta)

(def-complex-type Meta
  [^String id extension ^:primitive versionId ^:primitive lastUpdated
   ^:primitive source ^:primitive-list profile security tag]
  :hash-num 44)

;; ---- BundleEntrySearch -----------------------------------------------------

(declare bundle-entry-search)

(def-complex-type BundleEntrySearch
  [^String id extension ^:primitive mode ^:primitive score]
  :fhir-type :fhir.Bundle.entry/search
  :hash-num 45
  :interned (and (nil? id) (p/-interned extension) (nil? score)))

;; ---- Annotation ------------------------------------------------------------

(defn- map->Annotation [{:keys [id extension author time text]}]
  (Annotation. id extension author time text))

(def ^{:arglists '([x])} annotation
  (let [intern (intern/intern-value map->Annotation)]
    (fn [{:keys [id extension author time text] :as data}]
      (if (and (nil? id) (p/-interned extension) (p/-interned author)
               (p/-interned time) (p/-interned text))
        (intern data)
        (Annotation. id extension author time text)))))

(extend-protocol p/FhirType
  Annotation
  (-type [e]
    (.fhirType e))
  (-interned [e]
    (and (nil? (.id e)) (p/-interned (.extension e)) (p/-interned (.author e))
         (p/-interned (.time e)) (p/-interned (.text e))))
  (-has-primary-content [_] true)
  (-serialize-json [e generator]
    (.serializeAsJsonValue e generator))
  (-has-secondary-content [_] false)
  (-hash-into [e sink]
    (.hashInto e sink))
  (-references [e]
    (p/-references (.extension e))))

(def-print-method-complex "Annotation" "id" "extension" "author" "time" "text")

(defmethod print-dup Annotation [^Annotation e ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.Annotation. ")
  (print-dup (.id e) w)
  (.write w " ")
  (print-dup (.extension e) w)
  (.write w " ")
  (print-dup (.author e) w)
  (.write w " ")
  (print-dup (.time e) w)
  (.write w " ")
  (print-dup (.text e) w)
  (.write w " ")
  (.write w ")"))
