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
   [blaze.fhir.spec.type.macros :as macros
    :refer [def-complex-type def-primitive-type defextended]]
   [blaze.fhir.spec.type.protocols :as p]
   [blaze.fhir.spec.type.system :as system]
   [blaze.util :refer [str]]
   [clojure.alpha.spec :as s2]
   [clojure.data.xml :as xml]
   [clojure.data.xml.name :as xml-name]
   [clojure.data.xml.node :as xml-node]
   [clojure.string :as str])
  (:import
   [blaze.fhir.spec.type.system Date]
   [clojure.lang ILookup IPersistentMap Keyword]
   [com.fasterxml.jackson.core JsonGenerator]
   [com.google.common.hash PrimitiveSink]
   [java.io Writer]
   [java.time
    DateTimeException Instant LocalDate LocalDateTime LocalTime OffsetDateTime ZoneOffset]
   [java.time.format DateTimeFormatter]
   [java.util Comparator List Map$Entry UUID]))

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
  (-references [_]))

;; ---- Object -------------------------------------------------------------------

;; Other instances have no type.
(extend-protocol p/FhirType
  Object
  (-type [_])
  (-interned [_] false)
  (-references [_]))

;; ---- boolean ---------------------------------------------------------------

(declare boolean)

(extend-protocol p/FhirType
  Boolean
  (-type [_] :fhir/boolean)
  (-interned [_] true)
  (-assoc-id [b id] (boolean {:id id :value b}))
  (-assoc-extension [b extension] (boolean {:extension extension :value b}))
  (-value [b] b)
  (-assoc-value [_ value] (boolean value))
  (-has-primary-content [_] true)
  (-serialize-json [b generator]
    (.writeBoolean ^JsonGenerator generator b))
  (-has-secondary-content [_] false)
  (-serialize-json-secondary [_ generator]
    (.writeNull ^JsonGenerator generator))
  (-to-xml [b]
    (xml-node/element nil {:value (str b)}))
  (-hash-into [b sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 0))                                   ; :fhir/boolean
      (.putByte (byte 2)))                                  ; :value
    (system/-hash-into b sink))
  (-references [_]))

(defextended ExtendedBoolean [id extension ^Boolean value]
  :fhir-type :fhir/boolean :hash-num 0 :interned true)

(def ^{:arglists '([x])} boolean
  (let [intern (intern/intern-value map->ExtendedBoolean)]
    (fn [x]
      (cond
        (map? x)
        (let [{:keys [id extension value]} x]
          (cond
            (and (nil? extension) (nil? id))
            value

            (and (p/-interned extension) (nil? id))
            (intern {:extension extension :value value})

            :else
            (->ExtendedBoolean id extension value)))
        (clojure.core/boolean? x) x
        :else ::s2/invalid))))

(defn xml->Boolean
  {:arglists '([element])}
  [{{:keys [id value]} :attrs content :content}]
  (let [extension (seq content)
        value (some-> ^String value (Boolean/valueOf))]
    (if (or id extension)
      (boolean {:id id :extension extension :value value})
      (boolean value))))

(defn boolean? [x]
  (identical? :fhir/boolean (type x)))

;; ---- integer ---------------------------------------------------------------

(declare integer)

(extend-protocol p/FhirType
  Integer
  (-type [_] :fhir/integer)
  (-interned [_] false)
  (-assoc-id [i id] (integer {:id id :value i}))
  (-assoc-extension [i extension] (integer {:extension extension :value i}))
  (-value [i] i)
  (-assoc-value [_ value] (integer value))
  (-has-primary-content [_] true)
  (-serialize-json [i generator]
    (.writeNumber ^JsonGenerator generator i))
  (-has-secondary-content [_] false)
  (-serialize-json-secondary [_ generator]
    (.writeNull ^JsonGenerator generator))
  (-to-xml [i]
    (xml-node/element nil {:value (str i)}))
  (-hash-into [i sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 1))                                   ; :fhir/integer
      (.putByte (byte 2)))                                  ; :value
    (system/-hash-into i sink))
  (-references [_]))

(defextended ExtendedInteger [id extension ^Integer value]
  :fhir-type :fhir/integer :hash-num 1)

(def ^{:arglists '([x])} integer
  (create-fn (intern/intern-value map->ExtendedInteger) ->ExtendedInteger
             #(if (clojure.core/int? %) (clojure.core/int %) ::s2/invalid)))

(defn xml->Integer
  {:arglists '([element])}
  [{{:keys [id value]} :attrs content :content}]
  (let [extension (seq content)
        value (some-> ^String value (Integer/valueOf))]
    (if (or id extension)
      (integer {:id id :extension extension :value value})
      (integer value))))

(defn integer? [x]
  (identical? :fhir/integer (type x)))

;; ---- integer64 ---------------------------------------------------------------

(declare integer64)

(extend-protocol p/FhirType
  Long
  (-type [_] :fhir/integer64)
  (-interned [_] false)
  (-assoc-id [l id] (integer64 {:id id :value l}))
  (-assoc-extension [l extension] (integer64 {:extension extension :value l}))
  (-value [l] l)
  (-assoc-value [_ value] (integer64 value))
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
      (.putByte (byte 2))                                   ; :fhir/integer64
      (.putByte (byte 2)))                                  ; :value
    (system/-hash-into l sink))
  (-references [_]))

(defextended ExtendedInteger64 [id extension ^Long value]
  :fhir-type :fhir/integer64 :hash-num 2)

(def ^{:arglists '([x])} integer64
  (create-fn (intern/intern-value map->ExtendedInteger64) ->ExtendedInteger64
             #(if (clojure.core/int? %) (clojure.core/long %) ::s2/invalid)))

(defn xml->Integer64
  {:arglists '([element])}
  [{{:keys [id value]} :attrs content :content}]
  (let [extension (seq content)
        value (some-> ^String value (Long/valueOf))]
    (if (or id extension)
      (integer64 {:id id :extension extension :value value})
      (integer64 value))))

(defn integer64? [x]
  (identical? :fhir/integer64 (type x)))

;; ---- string ----------------------------------------------------------------

(declare string)

(extend-protocol p/FhirType
  String
  (-type [_] :fhir/string)
  (-interned [_] false)
  (-assoc-id [s id] (string {:id id :value s}))
  (-assoc-extension [s extension] (string {:extension extension :value s}))
  (-value [s] s)
  (-assoc-value [_ value] value)
  (-has-primary-content [_] true)
  (-serialize-json [s generator]
    (.writeString ^JsonGenerator generator s))
  (-has-secondary-content [_] false)
  (-serialize-json-secondary [_ generator]
    (.writeNull ^JsonGenerator generator))
  (-to-xml [s]
    (xml-node/element nil {:value (str s)}))
  (-hash-into [s sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 3))                                   ; :fhir/string
      (.putByte (byte 2)))                                  ; :value
    (system/-hash-into s sink))
  (-references [_]))

(defextended ExtendedString [id extension value]
  :fhir-type :fhir/string :hash-num 3)

(def ^{:arglists '([x])} string
  (create-fn (intern/intern-value map->ExtendedString) ->ExtendedString
             identity))

(defn xml->String
  {:arglists '([element])}
  [{{:keys [id value]} :attrs content :content}]
  (let [extension (seq content)]
    (if (or id extension)
      (string {:id id :extension extension :value value})
      (string value))))

(def ^{:arglists '([x])} intern-string
  (let [intern (intern/intern-value identity)
        intern-extended (intern/intern-value map->ExtendedString)]
    (fn [x]
      (if (map? x)
        (let [{:keys [id extension value]} x]
          (if (and (p/-interned extension) (nil? id))
            (intern-extended {:extension extension :value value})
            (->ExtendedString id extension value)))
        (intern x)))))

(defn xml->InternedString
  {:arglists '([element])}
  [{{:keys [id value]} :attrs content :content}]
  (let [extension (seq content)]
    (if (or id extension)
      (intern-string {:id id :extension extension :value value})
      (intern-string value))))

(defn string? [x]
  (identical? :fhir/string (type x)))

;; ---- decimal ---------------------------------------------------------------

(declare decimal)

(extend-protocol p/FhirType
  BigDecimal
  (-type [_] :fhir/decimal)
  (-interned [_] false)
  (-assoc-id [d id] (decimal {:id id :value d}))
  (-assoc-extension [d extension] (decimal {:extension extension :value d}))
  (-value [d] d)
  (-assoc-value [_ value] (decimal value))
  (-has-primary-content [_] true)
  (-serialize-json [d generator]
    (.writeNumber ^JsonGenerator generator d))
  (-has-secondary-content [_] false)
  (-serialize-json-secondary [_ generator]
    (.writeNull ^JsonGenerator generator))
  (-to-xml [d]
    (xml-node/element nil {:value (str d)}))
  (-hash-into [d sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 4))                                   ; :fhir/decimal
      (.putByte (byte 2)))                                  ; :value
    (system/-hash-into d sink))
  (-references [_]))

(defextended ExtendedDecimal [id extension ^BigDecimal value]
  :fhir-type :fhir/decimal :hash-num 4 :value-constructor bigdec)

(def ^{:arglists '([x])} decimal
  (create-fn (intern/intern-value map->ExtendedDecimal) ->ExtendedDecimal
             #(cond
                (int? %) (BigDecimal/valueOf (clojure.core/long %))
                (clojure.core/decimal? %) %
                :else ::s2/invalid)))

(defn xml->Decimal
  {:arglists '([element])}
  [{{:keys [id value]} :attrs content :content}]
  (let [extension (seq content)
        value (some-> ^String value (BigDecimal.))]
    (if (or id extension)
      (decimal {:id id :extension extension :value value})
      (decimal value))))

(defn decimal? [x]
  (identical? :fhir/decimal (type x)))

;; ---- uri -------------------------------------------------------------------

(declare uri?)
(declare uri)
(declare create-uri)
(declare map->ExtendedUri)
(declare xml->Uri)

(def-primitive-type Uri [^String value] :hash-num 5 :interned true)

;; ---- url -------------------------------------------------------------------

(declare url?)
(declare url)
(declare map->ExtendedUrl)
(declare xml->Url)

(def-primitive-type Url [value] :hash-num 6)

;; ---- canonical -------------------------------------------------------------

(declare canonical?)
(declare canonical)
(declare create-canonical)
(declare map->ExtendedCanonical)
(declare xml->Canonical)

(def-primitive-type Canonical [^String value] :hash-num 7 :interned true)

;; ---- base64Binary ----------------------------------------------------------

(declare base64Binary?)
(declare base64Binary)
(declare map->ExtendedBase64Binary)
(declare xml->Base64Binary)

(def-primitive-type Base64Binary [value] :hash-num 8)

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
              value (cond-> value (string? value) parse-instant-value)]
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
              value (cond-> value (string? value) system/parse-date)]
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
        (string? x) (parse-date x)
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

(declare dateTime)
(declare create-date-time)
(declare map->ExtendedDateTime)

(deftype DateTimeYear [^int year]
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
      (.putByte (byte 11))                                  ; :fhir/dateTime
      (.putByte (byte 2)))                                  ; :value
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

(defmethod print-method DateTimeYear [^DateTimeYear date-time ^Writer w]
  (.write w "#fhir/dateTime\"")
  (.write w (str date-time))
  (.write w "\""))

(deftype DateTimeYearMonth [^int year ^int month]
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
      (.putByte (byte 11))                                  ; :fhir/dateTime
      (.putByte (byte 2)))                                  ; :value
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

(defmethod print-method DateTimeYearMonth [^DateTimeYearMonth date-time ^Writer w]
  (.write w "#fhir/dateTime\"")
  (.write w (str date-time))
  (.write w "\""))

(deftype DateTimeDate [^int year ^int month ^int day]
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
      (.putByte (byte 11))                                  ; :fhir/dateTime
      (.putByte (byte 2)))                                  ; :value
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

(defmethod print-method DateTimeDate [^DateTimeDate date-time ^Writer w]
  (.write w "#fhir/dateTime\"")
  (.write w (str date-time))
  (.write w "\""))

(extend-protocol p/FhirType
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
      (.putByte (byte 11))                                  ; :fhir/dateTime
      (.putByte (byte 2)))                                  ; :value
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
      (.putByte (byte 11))                                  ; :fhir/dateTime
      (.putByte (byte 2)))                                  ; :value
    (system/-hash-into date-time sink))
  (-references [_]))

(defextended ExtendedDateTime [id extension value]
  :fhir-type :fhir/dateTime :hash-num 11)

(defn create-date-time [system-date-time]
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

(defn- parse-date-time [value]
  (try
    (create-date-time (system/parse-date-time* value))
    (catch DateTimeException _
      ::s2/invalid)))

(def ^{:arglists '([x])} dateTime
  (let [intern (intern/intern-value map->ExtendedDateTime)]
    (fn [x]
      (cond
        (map? x)
        (let [{:keys [id extension value]} x
              value (cond-> value (string? value) system/parse-date-time)]
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
        (string? x) (parse-date-time x)
        :else ::s2/invalid))))

(defn xml->DateTime
  "Creates a primitive dateTime value from XML `element`."
  {:arglists '([element])}
  [{{:keys [id value]} :attrs content :content}]
  (let [extension (seq content)]
    (if (or id extension)
      (if value
        (if-ok [value (system/parse-date-time value)]
          (dateTime {:id id :extension extension :value value})
          (fn [_] ::s2/invalid))
        (dateTime {:id id :extension extension}))
      (dateTime value))))

(defn dateTime? [x]
  (identical? :fhir/dateTime (type x)))

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
              value (cond-> value (string? value) system/parse-time)]
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
        (string? x) (parse-time x)
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

(declare code?)
(declare code)
(declare create-code)
(declare map->ExtendedCode)
(declare xml->Code)

(def-primitive-type Code [^String value] :hash-num 13 :interned true)

;; ---- oid -------------------------------------------------------------------

(declare oid?)
(declare oid)
(declare map->ExtendedOid)

(def-primitive-type Oid [value] :hash-num 14)

;; ---- id --------------------------------------------------------------------

(declare id?)
(declare id)
(declare map->ExtendedId)

(def-primitive-type Id [value] :hash-num 15)

;; ---- markdown --------------------------------------------------------------

(declare markdown?)
(declare markdown)
(declare map->ExtendedMarkdown)
(declare xml->Markdown)

(def-primitive-type Markdown [value] :hash-num 16)

;; ---- unsignedInt -----------------------------------------------------------

(declare unsignedInt?)
(declare unsignedInt)
(declare map->ExtendedUnsignedInt)
(declare xml->UnsignedInt)

(def-primitive-type UnsignedInt [^Integer value] :hash-num 17)

;; ---- positiveInt -----------------------------------------------------------

(declare positiveInt?)
(declare positiveInt)
(declare map->ExtendedPositiveInt)
(declare xml->PositiveInt)

(def-primitive-type PositiveInt [^Integer value] :hash-num 18)

;; ---- uuid ------------------------------------------------------------------

(declare uuid)

(extend-protocol p/FhirType
  UUID
  (-type [_] :fhir/uuid)
  (-interned [_] false)
  (-assoc-id [value id] (uuid {:id id :value value}))
  (-assoc-extension [value extension] (uuid {:extension extension :value value}))
  (-value [uuid] (str "urn:uuid:" uuid))
  (-assoc-value [_ value] (uuid value))
  (-has-primary-content [_] true)
  (-serialize-json [uuid generator]
    (.writeString ^JsonGenerator generator (str "urn:uuid:" uuid)))
  (-has-secondary-content [_] false)
  (-serialize-json-secondary [_ generator]
    (.writeNull ^JsonGenerator generator))
  (-to-xml [uuid]
    (xml-node/element nil {:value (str "urn:uuid:" uuid)}))
  (-hash-into [uuid sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 19))                                  ; :fhir/uuid
      (.putByte (byte 2))                                   ; :value
      (.putLong (.getMostSignificantBits uuid))
      (.putLong (.getLeastSignificantBits uuid))))
  (-references [_]))

(defextended ExtendedUuid [id extension ^UUID value]
  :fhir-type :fhir/uuid :hash-num 19 :value-constructor uuid :value-form (str "urn:uuid:" value))

(def ^{:arglists '([x])} uuid
  (create-fn (intern/intern-value map->ExtendedUuid) ->ExtendedUuid
             #(if (clojure.core/uuid? %) % (parse-uuid (subs % 9)))))

(defn xml->Uuid
  {:arglists '([element])}
  [{{:keys [id value]} :attrs content :content}]
  (let [extension (seq content)]
    (if (or id extension)
      (uuid {:id id :extension extension :value value})
      (uuid value))))

(defn uuid? [x]
  (identical? :fhir/uuid (type x)))

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

(declare attachment)

(def-complex-type Attachment
  [^String id extension ^:primitive contentType ^:primitive language
   ^:primitive ^:primitive data ^:primitive url ^:primitive size
   ^:primitive hash ^:primitive title ^:primitive creation]
  :hash-num 46)

(declare extension)

(def-complex-type Extension
  [^String id extension ^String url ^:polymorph ^:primitive
   ^{:types [base64Binary boolean canonical code date dateTime decimal id
             instant integer integer64 markdown oid positiveInt string time
             unsignedInt uri url uuid Address Age Annotation Attachment
             CodeableConcept CodeableReference Coding ContactPoint Count
             Distance Duration HumanName Identifier Money Period Quantity Range
             Ratio Reference SampledData Signature Timing ContactDetail
             Contributor DataRequirement Expression ParameterDefinition
             RelatedArtifact TriggerDefinition UsageContext Dosage Meta]}
   value]
  :hash-num 39
  :interned (and (nil? id) (p/-interned extension) (p/-interned value)))

(declare coding)

(def-complex-type Coding
  [^String id extension ^:primitive system ^:primitive-string version
   ^:primitive code ^:primitive-string display ^:primitive userSelected]
  :hash-num 38
  :interned (and (nil? id) (p/-interned extension)))

(declare codeable-concept)

(def-complex-type CodeableConcept
  [^String id extension coding ^:primitive-string text]
  :hash-num 39
  :interned (and (nil? id) (p/-interned extension)))

(declare codeable-reference)

(def-complex-type CodeableReference
  [^String id extension concept reference]
  :hash-num 46)

(declare quantity)

(def-complex-type Quantity
  [^String id extension ^:primitive value ^:primitive comparator
   ^:primitive-string unit ^:primitive system ^:primitive code]
  :hash-num 40
  :interned (and (nil? id) (p/-interned extension) (nil? value)))

(declare ratio)

(def-complex-type Ratio [^String id extension numerator denominator]
  :hash-num 48)

(declare period)

(def-complex-type Period [^String id extension ^:primitive start ^:primitive end]
  :hash-num 41)

(declare identifier)

(def-complex-type Identifier
  [^String id extension ^:primitive use type ^:primitive system
   ^:primitive-string value period assigner]
  :hash-num 42)

(declare human-name)

(def-complex-type HumanName
  [^String id extension ^:primitive use ^:primitive-string text
   ^:primitive-string family ^:primitive-list given ^:primitive-list prefix
   ^:primitive-list suffix period]
  :hash-num 46)

(declare address)

(def-complex-type Address
  [^String id extension ^:primitive use ^:primitive type ^:primitive-string text
   ^:primitive-list line ^:primitive-string city ^:primitive-string district
   ^:primitive-string state ^:primitive-string postalCode
   ^:primitive-string country period]
  :hash-num 47)

(defn- valid-ref? [[type id]]
  (and (.matches (re-matcher #"[A-Z]([A-Za-z0-9_]){0,254}" type))
       (some->> id (re-matcher #"[A-Za-z0-9\-\.]{1,64}") .matches)))

(defn- reference-reference [ref]
  (let [ref (str/split ref #"/" 2)]
    (when (valid-ref? ref)
      [ref])))

(declare reference)

(def-complex-type Reference
  [^String id extension ^:primitive-string reference ^:primitive type identifier
   ^:primitive-string display]
  :hash-num 43
  :references
  (-> (transient (or (some-> reference value reference-reference) []))
      (macros/into! (p/-references extension))
      (macros/into! (p/-references type))
      (macros/into! (p/-references identifier))
      (macros/into! (p/-references display))
      (persistent!)))

(declare meta)

(def-complex-type Meta
  [^String id extension ^:primitive versionId ^:primitive lastUpdated
   ^:primitive source ^:primitive-list profile security tag]
  :hash-num 44)

(declare bundle-entry-search)

(def-complex-type BundleEntrySearch
  [^String id extension ^:primitive mode ^:primitive score]
  :fhir-type :fhir.Bundle.entry/search
  :hash-num 45
  :interned (and (nil? id) (p/-interned extension) (nil? score)))
