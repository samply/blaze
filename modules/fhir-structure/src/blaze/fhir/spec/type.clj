(ns blaze.fhir.spec.type
  (:refer-clojure
    :exclude
    [boolean boolean? decimal? integer? long meta string? time type uri? uuid?])
  (:require
    [blaze.fhir.spec.impl.intern :as intern]
    [blaze.fhir.spec.type.json :as json]
    [blaze.fhir.spec.type.macros :as macros
     :refer [def-complex-type def-primitive-type defextended]]
    [blaze.fhir.spec.type.protocols :as p]
    [blaze.fhir.spec.type.system :as system]
    [clojure.alpha.spec :as s2]
    [clojure.data.xml :as xml]
    [clojure.data.xml.name :as xml-name]
    [clojure.data.xml.node :as xml-node]
    [clojure.string :as str])
  (:import
    [blaze.fhir.spec.type.system
     DateTimeYear DateTimeYearMonth DateTimeYearMonthDay]
    [clojure.lang IPersistentMap Keyword]
    [com.fasterxml.jackson.core JsonGenerator]
    [com.fasterxml.jackson.databind.module SimpleModule]
    [com.fasterxml.jackson.databind.ser.std StdSerializer]
    [com.google.common.hash PrimitiveSink]
    [java.io Writer]
    [java.time
     Instant LocalDate LocalDateTime LocalTime OffsetDateTime Year YearMonth
     ZoneOffset]
    [java.time.format DateTimeFormatter DateTimeParseException]
    [java.util Comparator List Map Map$Entry UUID]
    [jsonista.jackson
     KeywordKeyDeserializer PersistentHashMapDeserializer
     PersistentVectorDeserializer]))


(xml-name/alias-uri 'f "http://hl7.org/fhir")


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn type [x]
  (p/-type x))


(defn value
  "Returns the possible value of the primitive value `x` as FHIRPath system
  type."
  [x]
  (p/-value x))


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
  (-value [_])
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

(extend-protocol p/FhirType
  Boolean
  (-type [_] :fhir/boolean)
  (-interned [_] true)
  (-value [b] b)
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
      (if (map? x)
        (let [{:keys [id extension value]} x]
          (cond
            (and (nil? extension) (nil? id))
            value

            (and (p/-interned extension) (nil? id))
            (intern {:extension extension :value value})

            :else
            (->ExtendedBoolean id extension value)))
        x))))


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

(extend-protocol p/FhirType
  Integer
  (-type [_] :fhir/integer)
  (-interned [_] false)
  (-value [i] i)
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
             int))


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



;; ---- long ---------------------------------------------------------------

(extend-protocol p/FhirType
  Long
  (-type [_] :fhir/long)
  (-interned [_] false)
  (-value [l] l)
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
             identity))


(defn xml->Long
  {:arglists '([element])}
  [{{:keys [id value]} :attrs content :content}]
  (let [extension (seq content)
        value (some-> ^String value (Long/valueOf))]
    (if (or id extension)
      (long {:id id :extension extension :value value})
      (long value))))


(defn long? [x]
  (identical? :fhir/long (type x)))



;; ---- string ----------------------------------------------------------------

(extend-protocol p/FhirType
  String
  (-type [_] :fhir/string)
  (-interned [_] false)
  (-value [s] s)
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

(extend-protocol p/FhirType
  BigDecimal
  (-type [_] :fhir/decimal)
  (-interned [_] false)
  (-value [d] d)
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
  :fhir-type :fhir/decimal :hash-num 4)


(def ^{:arglists '([x])} decimal
  (create-fn (intern/intern-value map->ExtendedDecimal) ->ExtendedDecimal
             #(if (int? %) (BigDecimal/valueOf (clojure.core/long %)) %)))


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
(declare xml->Uri)


(def-primitive-type Uri [^String value] :hash-num 5 :interned true)



;; ---- url -------------------------------------------------------------------


(declare url?)
(declare url)
(declare xml->Url)


(def-primitive-type Url [value] :hash-num 6)



;; ---- canonical -------------------------------------------------------------


(declare canonical?)
(declare canonical)
(declare xml->Canonical)


(def-primitive-type Canonical [^String value] :hash-num 7 :interned true)



;; ---- base64Binary ----------------------------------------------------------


(declare base64Binary?)
(declare base64Binary)
(declare xml->Base64Binary)


(def-primitive-type Base64Binary [value] :hash-num 8)



;; ---- instant ---------------------------------------------------------------

(defmethod print-method Instant [^Instant instant ^Writer w]
  (doto w
    (.write "#java/instant\"")
    (.write (.toString instant))
    (.write "\"")))


;; Implementation of a FHIR instant with a variable ZoneOffset.
(deftype OffsetInstant [value]
  p/FhirType
  (-type [_] :fhir/instant)
  (-interned [_] false)
  (-value [_] value)
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
  (-value [instant] (.atOffset instant ZoneOffset/UTC))
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


(defextended ExtendedInstant [id extension ^Instant value]
  :fhir-type :fhir/instant :hash-num 9 :value-form (.atOffset value ZoneOffset/UTC))


(defn- parse-instant-value [value]
  (cond
    (str/ends-with? value "Z") (Instant/parse value)
    (str/ends-with? value "+00:00") (Instant/parse (str (subs value 0 (- (count value) 6)) "Z"))
    :else (OffsetDateTime/parse value)))


(def ^{:arglists '([x])} instant
  (let [intern (intern/intern-value map->ExtendedInstant)]
    (fn [x]
      (if (map? x)
        (let [{:keys [id extension value]} x
              value (some-> value parse-instant-value)]
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

(extend-protocol p/FhirType
  Year
  (-type [_] :fhir/date)
  (-interned [_] false)
  (-value [date] date)
  (-has-primary-content [_] true)
  (-serialize-json [date generator]
    (.writeString ^JsonGenerator generator (str date)))
  (-has-secondary-content [_] false)
  (-serialize-json-secondary [_ generator]
    (.writeNull ^JsonGenerator generator))
  (-to-xml [date]
    (xml-node/element nil {:value (str date)}))
  (-hash-into [date sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 10))                                  ; :fhir/date
      (.putByte (byte 2)))                                  ; :value
    (system/-hash-into date sink))
  (-references [_])

  YearMonth
  (-type [_] :fhir/date)
  (-interned [_] false)
  (-value [date] date)
  (-has-primary-content [_] true)
  (-serialize-json [date generator]
    (.writeString ^JsonGenerator generator (str date)))
  (-has-secondary-content [_] false)
  (-serialize-json-secondary [_ generator]
    (.writeNull ^JsonGenerator generator))
  (-to-xml [date]
    (xml-node/element nil {:value (str date)}))
  (-hash-into [date sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 10))                                  ; :fhir/date
      (.putByte (byte 2)))                                  ; :value
    (system/-hash-into date sink))
  (-references [_])

  LocalDate
  (-type [_] :fhir/date)
  (-interned [_] false)
  (-value [date] date)
  (-has-primary-content [_] true)
  (-serialize-json [date generator]
    (.writeString ^JsonGenerator generator (str date)))
  (-has-secondary-content [_] false)
  (-serialize-json-secondary [_ generator]
    (.writeNull ^JsonGenerator generator))
  (-to-xml [date]
    (xml-node/element nil {:value (str date)}))
  (-hash-into [date sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 10))                                  ; :fhir/date
      (.putByte (byte 2)))                                  ; :value
    (system/-hash-into date sink))
  (-references [_]))


(defextended ExtendedDate [id extension value]
  :fhir-type :fhir/date :hash-num 10)


(defn- parse-date-value [value]
  (try
    (system/parse-date* value)
    (catch DateTimeParseException _
      ;; in case of leap year errors not covered by regex
      ::s2/invalid)))


(def ^{:arglists '([x])} date
  (let [intern (intern/intern-value map->ExtendedDate)]
    (fn [x]
      (if (map? x)
        (let [{:keys [id extension value]} x
              value (some-> value parse-date-value)]
          (cond
            (s2/invalid? value)
            ::s2/invalid

            (and (nil? value) (p/-interned extension) (nil? id))
            (intern {:extension extension})

            (and (nil? extension) (nil? id))
            value

            :else
            (ExtendedDate. id extension value)))
        (parse-date-value x)))))


(defn xml->Date
  "Creates a primitive date value from XML `element`."
  {:arglists '([element])}
  [{{:keys [id value]} :attrs content :content}]
  (let [extension (seq content)]
    (if (or id extension)
      (date {:id id :extension extension :value value})
      (date value))))


(defn date? [x]
  (identical? :fhir/date (type x)))


(defprotocol ConvertToDateTime
  (-to-date-time [x]))


(extend-protocol ConvertToDateTime
  LocalDate
  (-to-date-time [date]
    (LocalDateTime/of date LocalTime/MIDNIGHT)))



;; -- dateTime ----------------------------------------------------------------

(extend-protocol p/FhirType
  DateTimeYear
  (-type [_] :fhir/dateTime)
  (-interned [_] false)
  (-value [year] year)
  (-has-primary-content [_] true)
  (-serialize-json [year generator]
    (.writeString ^JsonGenerator generator (str year)))
  (-has-secondary-content [_] false)
  (-serialize-json-secondary [_ generator]
    (.writeNull ^JsonGenerator generator))
  (-to-xml [year]
    (xml-node/element nil {:value (str year)}))
  (-hash-into [year sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 11))                                  ; :fhir/dateTime
      (.putByte (byte 2)))                                  ; :value
    (system/-hash-into year sink))
  (-references [_])

  DateTimeYearMonth
  (-type [_] :fhir/dateTime)
  (-interned [_] false)
  (-value [year-month] year-month)
  (-has-primary-content [_] true)
  (-serialize-json [year-month generator]
    (.writeString ^JsonGenerator generator (str year-month)))
  (-has-secondary-content [_] false)
  (-serialize-json-secondary [_ generator]
    (.writeNull ^JsonGenerator generator))
  (-to-xml [year-month]
    (xml-node/element nil {:value (str year-month)}))
  (-hash-into [year-month sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 11))                                  ; :fhir/dateTime
      (.putByte (byte 2)))                                  ; :value
    (system/-hash-into year-month sink))
  (-references [_])

  DateTimeYearMonthDay
  (-type [_] :fhir/dateTime)
  (-interned [_] false)
  (-value [year-month-day] year-month-day)
  (-has-primary-content [_] true)
  (-serialize-json [year-month-day generator]
    (.writeString ^JsonGenerator generator (str year-month-day)))
  (-has-secondary-content [_] false)
  (-serialize-json-secondary [_ generator]
    (.writeNull ^JsonGenerator generator))
  (-to-xml [year-month-day]
    (xml-node/element nil {:value (str year-month-day)}))
  (-hash-into [year-month-day sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 11))                                  ; :fhir/dateTime
      (.putByte (byte 2)))                                  ; :value
    (system/-hash-into year-month-day sink))
  (-references [_]))


(extend-protocol p/FhirType
  OffsetDateTime
  (-type [_] :fhir/dateTime)
  (-interned [_] false)
  (-value [date-time] date-time)
  (-has-primary-content [_] true)
  (-serialize-json [date-time generator]
    (.writeString ^JsonGenerator generator ^String (system/-to-string date-time)))
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
  (-value [date-time] date-time)
  (-has-primary-content [_] true)
  (-serialize-json [date-time generator]
    (.writeString ^JsonGenerator generator ^String (system/-to-string date-time)))
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


(defn- parse-date-time-value [value]
  (try
    (system/parse-date-time* value)
    (catch DateTimeParseException _
      ;; in case of leap year errors not covered by regex
      ::s2/invalid)))


(def ^{:arglists '([x])} dateTime
  (let [intern (intern/intern-value map->ExtendedDateTime)]
    (fn [x]
      (if (map? x)
        (let [{:keys [id extension value]} x
              value (some-> value parse-date-time-value)]
          (cond
            (s2/invalid? value)
            ::s2/invalid

            (and (nil? value) (p/-interned extension) (nil? id))
            (intern {:extension extension})

            (and (nil? extension) (nil? id))
            value

            :else
            (ExtendedDateTime. id extension value)))
        (parse-date-time-value x)))))


(defn xml->DateTime
  "Creates a primitive dateTime value from XML `element`."
  {:arglists '([element])}
  [{{:keys [id value]} :attrs content :content}]
  (let [extension (seq content)]
    (if (or id extension)
      (dateTime {:id id :extension extension :value value})
      (dateTime value))))


(defn dateTime? [x]
  (identical? :fhir/dateTime (type x)))



;; ---- time ------------------------------------------------------------------

(extend-protocol p/FhirType
  LocalTime
  (-type [_] :fhir/time)
  (-interned [_] false)
  (-value [time] time)
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


(def ^{:arglists '([x])} time
  (let [intern (intern/intern-value map->ExtendedTime)]
    (fn [x]
      (if (map? x)
        (let [{:keys [id extension value]} x
              value (some-> value LocalTime/parse)]
          (cond
            (s2/invalid? value)
            ::s2/invalid

            (and (nil? value) (p/-interned extension) (nil? id))
            (intern {:extension extension})

            (and (nil? extension) (nil? id))
            value

            :else
            (ExtendedTime. id extension value)))
        (LocalTime/parse x)))))


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
(declare xml->Code)


(def-primitive-type Code [^String value] :hash-num 13 :interned true)



;; ---- oid -------------------------------------------------------------------

(declare oid?)
(declare oid)


(def-primitive-type Oid [value] :hash-num 14)



;; ---- id --------------------------------------------------------------------


(declare id?)
(declare id)


(def-primitive-type Id [value] :hash-num 15)



;; ---- markdown --------------------------------------------------------------


(declare markdown?)
(declare markdown)


(def-primitive-type Markdown [value] :hash-num 16)



;; ---- unsignedInt -----------------------------------------------------------

(declare unsignedInt?)
(declare unsignedInt)
(declare xml->UnsignedInt)


(def-primitive-type UnsignedInt [^Integer value] :hash-num 17)



;; ---- positiveInt -----------------------------------------------------------

(declare positiveInt?)
(declare positiveInt)
(declare xml->PositiveInt)


(def-primitive-type PositiveInt [^Integer value] :hash-num 18)



;; ---- uuid ------------------------------------------------------------------

(extend-protocol p/FhirType
  UUID
  (-type [_] :fhir/uuid)
  (-interned [_] false)
  (-value [uuid] (str "urn:uuid:" uuid))
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
  :fhir-type :fhir/uuid :hash-num 19 :value-form (str "urn:uuid:" value))


(def ^{:arglists '([x])} uuid
  (create-fn (intern/intern-value map->ExtendedUuid) ->ExtendedUuid
             #(parse-uuid (subs % 9))))


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

(deftype Xhtml [value]
  p/FhirType
  (-type [_] :fhir/xhtml)
  (-interned [_] false)
  (-value [_] value)
  (-has-primary-content [_] true)
  (-serialize-json [_ generator]
    (.writeString ^JsonGenerator generator ^String value))
  (-has-secondary-content [_] false)
  (-serialize-json-secondary [_ generator]
    (.writeNull ^JsonGenerator generator))
  (-to-xml [_] (update (xml/parse-str value) :attrs assoc :xmlns "http://www.w3.org/1999/xhtml"))
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
    (reduce #(p/-serialize-json %2 generator) nil xs)
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
  (-value [_])
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


(def-complex-type Extension [^String id extension ^String url ^:polymorph ^:primitive value]
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


(declare quantity)


(def-complex-type Quantity
  [^String id extension ^:primitive value ^:primitive comparator
   ^:primitive-string unit ^:primitive system ^:primitive code]
  :hash-num 40
  :interned (and (nil? id) (p/-interned extension) (nil? value)))


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



;; ---- Jackson Databind Module -----------------------------------------------

(def ^:private object-serializer
  (proxy [StdSerializer] [Object]
    (serialize [obj generator _]
      (p/-serialize-json obj generator))))


(def fhir-module
  (doto (SimpleModule. "FHIR")
    (.addDeserializer List (PersistentVectorDeserializer.))
    (.addDeserializer Map (PersistentHashMapDeserializer.))
    (.addKeyDeserializer Object (KeywordKeyDeserializer.))
    (.addSerializer Object object-serializer)))



;; ---- print -----------------------------------------------------------------

(defmethod print-dup Year [^Year year ^Writer w]
  (.write w "#=(java.time.Year/of ")
  (.write w (str (.getValue year)))
  (.write w ")"))


(defmethod print-dup Instant [^Instant instant ^Writer w]
  (.write w "#=(java.time.Instant/ofEpochSecond ")
  (.write w (str (.getEpochSecond instant)))
  (.write w " ")
  (.write w (str (.getNano instant)))
  (.write w ")"))


(defmethod print-dup YearMonth [^YearMonth yearMonth ^Writer w]
  (.write w "#=(java.time.YearMonth/of ")
  (.write w (str (.getYear yearMonth)))
  (.write w " ")
  (.write w (str (.getMonthValue yearMonth)))
  (.write w ")"))


(defmethod print-dup LocalDate [^LocalDate date ^Writer w]
  (.write w "#=(java.time.LocalDate/of ")
  (.write w (str (.getYear date)))
  (.write w " ")
  (.write w (str (.getMonthValue date)))
  (.write w " ")
  (.write w (str (.getDayOfMonth date)))
  (.write w ")"))


(defmethod print-dup LocalDateTime [^LocalDateTime dateTime ^Writer w]
  (.write w "#=(java.time.LocalDateTime/of ")
  (.write w (str (.getYear dateTime)))
  (.write w " ")
  (.write w (str (.getMonthValue dateTime)))
  (.write w " ")
  (.write w (str (.getDayOfMonth dateTime)))
  (.write w " ")
  (.write w (str (.getHour dateTime)))
  (.write w " ")
  (.write w (str (.getMinute dateTime)))
  (.write w " ")
  (.write w (str (.getSecond dateTime)))
  (.write w " ")
  (.write w (str (.getNano dateTime)))
  (.write w ")"))


(defmethod print-dup OffsetDateTime [^OffsetDateTime dateTime ^Writer w]
  (.write w "#=(java.time.OffsetDateTime/of ")
  (.write w (str (.getYear dateTime)))
  (.write w " ")
  (.write w (str (.getMonthValue dateTime)))
  (.write w " ")
  (.write w (str (.getDayOfMonth dateTime)))
  (.write w " ")
  (.write w (str (.getHour dateTime)))
  (.write w " ")
  (.write w (str (.getMinute dateTime)))
  (.write w " ")
  (.write w (str (.getSecond dateTime)))
  (.write w " ")
  (.write w (str (.getNano dateTime)))
  (.write w ",#=(java.time.ZoneOffset/of \"")
  (.write w (.getId (.getOffset dateTime)))
  (.write w "\"))"))


(defmethod print-dup LocalTime [^LocalTime time ^Writer w]
  (.write w "#=(java.time.LocalTime/of ")
  (.write w (str (.getHour time)))
  (.write w " ")
  (.write w (str (.getMinute time)))
  (.write w " ")
  (.write w (str (.getSecond time)))
  (.write w " ")
  (.write w (str (.getNano time)))
  (.write w ")"))
