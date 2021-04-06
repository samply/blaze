(ns blaze.fhir.spec.type
  (:require
    [blaze.fhir.spec.type.macros :refer [defcomplextype]]
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
    [clojure.lang Keyword]
    [com.google.common.hash PrimitiveSink]
    [java.io Writer]
    [java.time
     Instant LocalDate LocalDateTime LocalTime OffsetDateTime Year YearMonth
     ZoneOffset]
    [java.time.format DateTimeFormatter DateTimeParseException]
    [java.util List Map UUID]
    [com.fasterxml.jackson.core JsonGenerator]
    [com.fasterxml.jackson.databind.module SimpleModule]
    [com.fasterxml.jackson.databind JsonSerializer SerializerProvider]
    [com.fasterxml.jackson.databind.ser.std StdSerializer]
    [com.fasterxml.jackson.datatype.jsr310.ser
     LocalDateSerializer OffsetDateTimeSerializer YearSerializer
     YearMonthSerializer])
  (:refer-clojure :exclude [decimal? string? type uri? uuid?]))


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



;; ---- nil -------------------------------------------------------------------

;; Be sure all methods can be called on nil.
(extend-protocol p/FhirType
  nil
  (-type [_])
  (-value [_])
  (-to-xml [_])
  (-hash-into [_ _]))



;; ---- Object -------------------------------------------------------------------

;; Other instances have no type.
(extend-protocol p/FhirType
  Object
  (-type [_]))



;; ---- boolean ---------------------------------------------------------------

(extend-protocol p/FhirType
  Boolean
  (-type [_] :fhir/boolean)
  (-value [b] b)
  (-to-xml [b] (xml-node/element nil {:value (str b)}))
  (-hash-into [b sink]
    (.putByte ^PrimitiveSink sink (byte 0))                 ; :fhir/boolean
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into b sink)))


(defn xml->Boolean
  {:arglists '([element])}
  [{{:keys [_id _extension value]} :attrs}]
  (Boolean/valueOf ^String value))



;; ---- integer ---------------------------------------------------------------

(extend-protocol p/FhirType
  Integer
  (-type [_] :fhir/integer)
  (-value [i] i)
  (-to-xml [i] (xml-node/element nil {:value (str i)}))
  (-hash-into [i sink]
    (.putByte ^PrimitiveSink sink (byte 1))                 ; :fhir/integer
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into i sink)))


(defn xml->Integer
  {:arglists '([element])}
  [{{:keys [_id _extension value]} :attrs}]
  (Integer/valueOf ^String value))



;; ---- long ---------------------------------------------------------------

(extend-protocol p/FhirType
  Long
  (-type [_] :fhir/long)
  (-value [i] i)
  (-to-xml [i] (xml-node/element nil {:value (str i)}))
  (-hash-into [i sink]
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :fhir/long
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into i sink)))


(defn xml->Long
  {:arglists '([element])}
  [{{:keys [_id _extension value]} :attrs}]
  (Long/valueOf ^String value))



;; ---- string ----------------------------------------------------------------

(extend-protocol p/FhirType
  String
  (-type [_] :fhir/string)
  (-value [s] s)
  (-to-xml [s] (xml-node/element nil {:value (str s)}))
  (-hash-into [s sink]
    (.putByte ^PrimitiveSink sink (byte 3))                 ; :fhir/string
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into s sink)))


(defn xml->String
  {:arglists '([element])}
  [{{:keys [_id _extension value]} :attrs}]
  value)


(defn string? [x]
  (identical? :fhir/string (p/-type x)))



;; ---- decimal ---------------------------------------------------------------

(extend-protocol p/FhirType
  BigDecimal
  (-type [_] :fhir/decimal)
  (-value [d] d)
  (-to-xml [d] (xml-node/element nil {:value (str d)}))
  (-hash-into [d sink]
    (.putByte ^PrimitiveSink sink (byte 4))                 ; :fhir/decimal
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into d sink)))


(defn xml->Decimal
  {:arglists '([element])}
  [{{:keys [_id _extension value]} :attrs}]
  (BigDecimal. ^String value))


(defn decimal? [x]
  (identical? :fhir/decimal (p/-type x)))



;; ---- uri -------------------------------------------------------------------

(deftype Uri [value]
  p/FhirType
  (-type [_] :fhir/uri)
  (-value [_] value)
  (-to-xml [_] (xml-node/element nil {:value value}))
  (-hash-into [_ sink]
    (.putByte ^PrimitiveSink sink (byte 5))                 ; :fhir/uri
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into value sink))

  Object
  (equals [_ x]
    (and (instance? Uri x) (= value (.value ^Uri x))))
  (hashCode [_]
    (.hashCode value))
  (toString [_]
    (str value)))


(def ^:private ^JsonSerializer uri-serializer
  (proxy [StdSerializer] [Uri]
    (serialize [^Uri uri ^JsonGenerator gen _]
      (.writeString gen ^String (.value uri)))))


(defmethod print-method Uri [^Uri uri ^Writer w]
  (.write w "#fhir/uri\"")
  (.write w (str uri))
  (.write w "\""))


(defn xml->Uri
  {:arglists '([element])}
  [{{:keys [_id _extension value]} :attrs}]
  (->Uri value))


(defn uri? [x]
  (instance? Uri x))



;; ---- url -------------------------------------------------------------------

(deftype Url [value]
  p/FhirType
  (-type [_] :fhir/url)
  (-value [_] value)
  (-to-xml [_] (xml-node/element nil {:value value}))
  (-hash-into [_ sink]
    (.putByte ^PrimitiveSink sink (byte 6))                 ; :fhir/url
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into value sink))

  Object
  (equals [_ x]
    (and (instance? Url x) (= value (.value ^Url x))))
  (hashCode [_]
    (.hashCode value))
  (toString [_]
    (str value)))


(def ^:private url-serializer
  (proxy [StdSerializer] [Url]
    (serialize [^Url url ^JsonGenerator gen _]
      (.writeString gen ^String (.value url)))))


(defmethod print-method Url [^Url url ^Writer w]
  (.write w "#fhir/url\"")
  (.write w (str url))
  (.write w "\""))


(defn xml->Url
  {:arglists '([element])}
  [{{:keys [_id value]} :attrs _extensions :content}]
  (->Url value))


(defn url? [x]
  (instance? Url x))



;; ---- canonical -------------------------------------------------------------

(deftype Canonical [value]
  p/FhirType
  (-type [_] :fhir/canonical)
  (-value [_] value)
  (-to-xml [_] (xml-node/element nil {:value value}))
  (-hash-into [_ sink]
    (.putByte ^PrimitiveSink sink (byte 7))                 ; :fhir/canonical
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into value sink))

  Object
  (equals [_ x]
    (and (instance? Canonical x) (= value (.value ^Canonical x))))
  (hashCode [_]
    (.hashCode value))
  (toString [_]
    (str value)))


(def ^:private ^JsonSerializer canonical-serializer
  (proxy [StdSerializer] [Canonical]
    (serialize [^Canonical canonical ^JsonGenerator gen _]
      (.writeString gen ^String (.value canonical)))))


(defmethod print-method Canonical [^Canonical canonical ^Writer w]
  (.write w "#fhir/canonical\"")
  (.write w (str canonical))
  (.write w "\""))


(defn xml->Canonical
  {:arglists '([element])}
  [{{:keys [_id value]} :attrs _extensions :content}]
  (->Canonical value))


(defn canonical? [x]
  (instance? Canonical x))



;; ---- base64Binary ----------------------------------------------------------

(deftype Base64Binary [value]
  p/FhirType
  (-type [_] :fhir/base64Binary)
  (-value [_] value)
  (-to-xml [_] (xml-node/element nil {:value value}))
  (-hash-into [_ sink]
    (.putByte ^PrimitiveSink sink (byte 8))                 ; :fhir/base64Binary
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into value sink))

  Object
  (equals [_ x]
    (and (instance? Base64Binary x) (= value (.value ^Base64Binary x))))
  (hashCode [_]
    (.hashCode value))
  (toString [_]
    (str value)))


(def ^:private base64-binary-serializer
  (proxy [StdSerializer] [Base64Binary]
    (serialize [^Base64Binary base64-binary ^JsonGenerator gen _]
      (.writeString gen ^String (.value base64-binary)))))


(defn xml->Base64Binary
  {:arglists '([element])}
  [{{:keys [_id value]} :attrs _extensions :content}]
  (->Base64Binary value))


(defn base64Binary? [x]
  (instance? Base64Binary x))



;; ---- instant ---------------------------------------------------------------


(defn- format-offset-date-time [date-time]
  (.format DateTimeFormatter/ISO_DATE_TIME date-time))


;; Implementation of a FHIR instant with a variable ZoneOffset.
(deftype OffsetInstant [^OffsetDateTime value]
  p/FhirType
  (-type [_] :fhir/instant)
  (-value [_] value)
  (-to-xml [_] (xml-node/element nil {:value (format-offset-date-time value)}))
  (-hash-into [_ sink]
    (.putByte ^PrimitiveSink sink (byte 9))                 ; :fhir/instant
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into value sink))

  Object
  (equals [_ x]
    (and (instance? OffsetInstant x) (= value (.value ^OffsetInstant x))))
  (hashCode [_]
    (.hashCode value))
  (toString [_]
    (str value)))


(def ^:private offset-instant-serializer
  (proxy [StdSerializer] [OffsetInstant]
    (serialize [^OffsetInstant offset-instant gen provider]
      (.serialize OffsetDateTimeSerializer/INSTANCE (.value offset-instant) gen provider))))


(extend-protocol p/FhirType
  Instant
  (-type [_] :fhir/instant)
  (-value [instant] (.atOffset instant ZoneOffset/UTC))
  (-to-xml [instant] (xml-node/element nil {:value (str instant)}))
  (-hash-into [instant sink]
    (.putByte ^PrimitiveSink sink (byte 9))                 ; :fhir/instant
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into (p/-value instant) sink)))


(defn ->Instant [s]
  (cond
    (str/ends-with? s "Z") (Instant/parse s)
    (str/ends-with? s "+00:00") (Instant/parse (str (subs s 0 (- (count s) 6)) "Z"))
    :else (OffsetInstant. (OffsetDateTime/parse s))))


(defn xml->Instant
  {:arglists '([element])}
  [{{:keys [_id value]} :attrs _extensions :content}]
  (->Instant value))


(defn instant? [x]
  (identical? :fhir/instant (p/-type x)))



;; -- date --------------------------------------------------------------------

(extend-protocol p/FhirType
  Year
  (-type [_] :fhir/date)
  (-value [date] date)
  (-to-xml [date] (xml-node/element nil {:value (str date)}))
  (-hash-into [date sink]
    (.putByte ^PrimitiveSink sink (byte 10))                ; :fhir/date
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into date sink))

  YearMonth
  (-type [_] :fhir/date)
  (-value [date] date)
  (-to-xml [date] (xml-node/element nil {:value (str date)}))
  (-hash-into [date sink]
    (.putByte ^PrimitiveSink sink (byte 10))                ; :fhir/date
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into date sink))

  LocalDate
  (-type [_] :fhir/date)
  (-value [date] date)
  (-to-xml [date] (xml-node/element nil {:value (str date)}))
  (-hash-into [date sink]
    (.putByte ^PrimitiveSink sink (byte 10))                ; :fhir/date
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into date sink)))


(defn ->Date [value]
  (try
    (system/parse-date* value)
    (catch DateTimeParseException _
      ;; in case of leap year errors not covered by regex
      ::s2/invalid)))


(defn xml->Date
  {:arglists '([element])}
  [{{:keys [_id value]} :attrs _extensions :content}]
  (->Date value))


(defn date? [x]
  (identical? :fhir/date (p/-type x)))


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
  (-value [year] year)
  (-to-xml [year] (xml-node/element nil {:value (str year)}))
  (-hash-into [year sink]
    (.putByte ^PrimitiveSink sink (byte 11))                ; :fhir/dateTime
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into year sink))

  DateTimeYearMonth
  (-type [_] :fhir/dateTime)
  (-value [year-month] year-month)
  (-to-xml [year-month] (xml-node/element nil {:value (str year-month)}))
  (-hash-into [year-month sink]
    (.putByte ^PrimitiveSink sink (byte 11))                ; :fhir/dateTime
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into year-month sink))

  DateTimeYearMonthDay
  (-type [_] :fhir/dateTime)
  (-value [year-month-day] year-month-day)
  (-to-xml [year-month-day] (xml-node/element nil {:value (str year-month-day)}))
  (-hash-into [year-month-day sink]
    (.putByte ^PrimitiveSink sink (byte 11))                ; :fhir/dateTime
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into year-month-day sink)))


(def ^:private date-time-year-serializer
  (proxy [StdSerializer] [DateTimeYear]
    (serialize [^DateTimeYear date-time-year gen provider]
      (.serialize YearSerializer/INSTANCE (.year date-time-year) gen provider))))


(def ^:private date-time-year-month-serializer
  (proxy [StdSerializer] [DateTimeYearMonth]
    (serialize [^DateTimeYearMonth date-time-year-month gen provider]
      (.serialize YearMonthSerializer/INSTANCE (.year_month date-time-year-month)
                  gen provider))))


(def ^:private date-time-year-month-day-serializer
  (proxy [StdSerializer] [DateTimeYearMonthDay]
    (serialize [^DateTimeYearMonthDay date-time-year-month gen provider]
      (.serialize LocalDateSerializer/INSTANCE (.date date-time-year-month)
                  gen provider))))


(defn- format-local-date-time [date-time]
  (.format DateTimeFormatter/ISO_LOCAL_DATE_TIME date-time))


(extend-protocol p/FhirType
  OffsetDateTime
  (-type [_] :fhir/dateTime)
  (-value [date-time] date-time)
  (-to-xml [date-time]
    (xml-node/element nil {:value (format-offset-date-time date-time)}))
  (-hash-into [date-time sink]
    (.putByte ^PrimitiveSink sink (byte 11))                ; :fhir/dateTime
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into date-time sink))

  LocalDateTime
  (-type [_] :fhir/dateTime)
  (-value [date-time] date-time)
  (-to-xml [date-time]
    (xml-node/element nil {:value (format-local-date-time date-time)}))
  (-hash-into [date-time sink]
    (.putByte ^PrimitiveSink sink (byte 11))                ; :fhir/dateTime
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into date-time sink)))


(defrecord ExtendedDateTime [id extension value]
  p/FhirType
  (-type [_] :fhir/dateTime)
  (-value [_] value)
  (-to-xml [_] (xml-node/element* id {:value (str value)} extension))
  (-hash-into [_ sink]
    (.putByte ^PrimitiveSink sink (byte 11))                ; :fhir/dateTime
    (when id
      (.putByte ^PrimitiveSink sink (byte 0))               ; :id
      (system/-hash-into id sink))
    (when extension
      (.putByte ^PrimitiveSink sink (byte 1))               ; :extension
      (p/-hash-into extension sink))
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into value sink)))


(def ^:private extended-date-time-serializer
  (proxy [StdSerializer] [ExtendedDateTime]
    (serialize [^ExtendedDateTime extended-date-time gen ^SerializerProvider provider]
      (let [value (.value extended-date-time)]
        (.serialize (.findValueSerializer provider (class value)) value gen provider)))))


(defn ->DateTime
  "Creates a primitive dateTime value."
  ([value]
   (try
     (system/parse-date-time* value)
     (catch DateTimeParseException _
       ;; in case of leap year errors not covered by regex
       ::s2/invalid)))
  ([id extensions value]
   (let [date-time (->DateTime value)]
     (if (s2/invalid? date-time)
       ::s2/invalid
       (->ExtendedDateTime id extensions date-time)))))


(defn xml->DateTime
  "Creates a primitive dateTime value from XML `element`."
  {:arglists '([element])}
  [{{:keys [id value]} :attrs extensions :content}]
  (if (or id (seq extensions))
    (->DateTime id extensions value)
    (->DateTime value)))


(defn date-time? [x]
  (identical? :fhir/dateTime (p/-type x)))



;; ---- time ------------------------------------------------------------------

(defn- format-time [time]
  (.format DateTimeFormatter/ISO_LOCAL_TIME time))


(extend-protocol p/FhirType
  LocalTime
  (-type [_] :fhir/time)
  (-value [time] time)
  (-to-xml [time] (xml-node/element nil {:value (format-time time)}))
  (-hash-into [time sink]
    (.putByte ^PrimitiveSink sink (byte 12))                ; :fhir/time
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into time sink)))


(defn ->Time [s]
  (LocalTime/parse s))


(defn xml->Time
  {:arglists '([element])}
  [{{:keys [_id value]} :attrs _extensions :content}]
  (->Time value))


(defn time? [x]
  (identical? :fhir/time (p/-type x)))



;; ---- code ------------------------------------------------------------------

(deftype Code [value]
  p/FhirType
  (-type [_] :fhir/code)
  (-value [_] value)
  (-to-xml [_] (xml-node/element nil {:value value}))
  (-hash-into [_ sink]
    (.putByte ^PrimitiveSink sink (byte 13))                ; :fhir/code
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into value sink))
  Object
  (equals [_ x]
    (and (instance? Code x) (= value (.value ^Code x))))
  (hashCode [_]
    (.hashCode value))
  (toString [_]
    value))


(def ^:private ^JsonSerializer code-serializer
  (proxy [StdSerializer] [Code]
    (serialize [^Code code ^JsonGenerator gen _]
      (.writeString gen ^String (.value code)))))


(defmethod print-method Code [code ^Writer w]
  (.write w "#fhir/code\"")
  (.write w ^String (p/-value code))
  (.write w "\""))


(defrecord ExtendedCode [id extension value]
  p/FhirType
  (-type [_] :fhir/code)
  (-value [_] value)
  (-to-xml [_] (xml-node/element* nil {:value value} extension))
  (-hash-into [_ sink]
    (.putByte ^PrimitiveSink sink (byte 13))                ; :fhir/code
    (when id
      (.putByte ^PrimitiveSink sink (byte 0))               ; :id
      (system/-hash-into id sink))
    (when extension
      (.putByte ^PrimitiveSink sink (byte 1))               ; :extension
      (p/-hash-into extension sink))
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into value sink)))


(def ^:private extended-code-serializer
  (proxy [StdSerializer] [ExtendedCode]
    (serialize [^ExtendedCode extended-code ^JsonGenerator gen _]
      (.writeString gen ^String (.value extended-code)))))


(defn xml->Code
  "Creates a primitive code value from XML `element`."
  {:arglists '([element])}
  [{{:keys [id value]} :attrs extensions :content}]
  (if (or id (seq extensions))
    (->ExtendedCode id extensions value)
    (->Code value)))


(defn code? [x]
  (identical? :fhir/code (p/-type x)))



;; ---- oid -------------------------------------------------------------------

(deftype Oid [value]
  p/FhirType
  (-type [_] :fhir/oid)
  (-value [_] value)
  (-to-xml [_] (xml-node/element nil {:value value}))
  (-hash-into [_ sink]
    (.putByte ^PrimitiveSink sink (byte 14))                ; :fhir/oid
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into value sink))
  Object
  (equals [_ x]
    (and (instance? Oid x) (= value (.value ^Oid x))))
  (hashCode [_]
    (.hashCode value))
  (toString [_]
    value))


(def ^:private oid-serializer
  (proxy [StdSerializer] [Oid]
    (serialize [^Oid oid ^JsonGenerator gen _]
      (.writeString gen ^String (.value oid)))))


(defmethod print-method Oid [id ^Writer w]
  (.write w "#fhir/oid\"")
  (.write w ^String (p/-value id))
  (.write w "\""))


(defn xml->Oid
  {:arglists '([element])}
  [{{:keys [_id value]} :attrs _extensions :content}]
  (->Oid value))


(defn oid? [x]
  (identical? :fhir/oid (p/-type x)))



;; ---- id --------------------------------------------------------------------

(deftype Id [value]
  p/FhirType
  (-type [_] :fhir/id)
  (-value [_] value)
  (-to-xml [_] (xml-node/element nil {:value value}))
  (-hash-into [_ sink]
    (.putByte ^PrimitiveSink sink (byte 15))                ; :fhir/id
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into value sink))
  Object
  (equals [_ x]
    (and (instance? Id x) (= value (.value ^Id x))))
  (hashCode [_]
    (.hashCode value))
  (toString [_]
    value))


(def ^:private ^JsonSerializer id-serializer
  (proxy [StdSerializer] [Id]
    (serialize [^Id id ^JsonGenerator gen _]
      (.writeString gen ^String (.value id)))))


(defmethod print-method Id [id ^Writer w]
  (.write w "#fhir/id\"")
  (.write w ^String (p/-value id))
  (.write w "\""))


(defn xml->Id
  {:arglists '([element])}
  [{{:keys [_id value]} :attrs _extensions :content}]
  (->Id value))


(defn id? [x]
  (identical? :fhir/id (p/-type x)))



;; ---- markdown --------------------------------------------------------------

(deftype Markdown [value]
  p/FhirType
  (-type [_] :fhir/markdown)
  (-value [_] value)
  (-to-xml [_] (xml-node/element nil {:value value}))
  (-hash-into [_ sink]
    (.putByte ^PrimitiveSink sink (byte 16))                ; :fhir/markdown
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into value sink))
  Object
  (equals [_ x]
    (and (instance? Markdown x) (= value (.value ^Markdown x))))
  (hashCode [_]
    (.hashCode value))
  (toString [_]
    (str value)))


(def ^:private markdown-serializer
  (proxy [StdSerializer] [Markdown]
    (serialize [^Markdown markdown ^JsonGenerator gen _]
      (.writeString gen ^String (.value markdown)))))


(defmethod print-method Markdown [markdown ^Writer w]
  (.write w "#fhir/markdown\"")
  (.write w ^String (p/-value markdown))
  (.write w "\""))


(defn xml->Markdown
  {:arglists '([element])}
  [{{:keys [_id value]} :attrs _extensions :content}]
  (->Markdown value))


(defn markdown? [x]
  (identical? :fhir/markdown (p/-type x)))



;; ---- unsignedInt -----------------------------------------------------------

(deftype UnsignedInt [^int value]
  p/FhirType
  (-type [_] :fhir/unsignedInt)
  (-value [_] value)
  (-to-xml [_] (xml-node/element nil {:value (Integer/toString value)}))
  (-hash-into [_ sink]
    (.putByte ^PrimitiveSink sink (byte 17))                ; :fhir/unsignedInt
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into value sink))
  Object
  (equals [_ x]
    (and (instance? UnsignedInt x) (= value (.value ^UnsignedInt x))))
  (hashCode [_]
    value)
  (toString [_]
    (Integer/toString value)))


(def ^:private unsigned-int-serializer
  (proxy [StdSerializer] [UnsignedInt]
    (serialize [^UnsignedInt unsigned-int ^JsonGenerator gen _]
      (.writeNumber gen ^int (.value unsigned-int)))))


(defmethod print-method UnsignedInt [unsigned-int ^Writer w]
  (.write w "#fhir/unsignedInt ")
  (.write w (str unsigned-int)))


(defrecord ExtendedUnsignedInt [id extension ^int value]
  p/FhirType
  (-type [_] :fhir/unsignedInt)
  (-value [_] value)
  (-to-xml [_] (xml-node/element* nil {:value (Integer/toString value)} extension))
  (-hash-into [_ sink]
    (.putByte ^PrimitiveSink sink (byte 17))                ; :fhir/unsignedInt
    (when id
      (.putByte ^PrimitiveSink sink (byte 0))               ; :id
      (system/-hash-into id sink))
    (when extension
      (.putByte ^PrimitiveSink sink (byte 1))               ; :extension
      (p/-hash-into extension sink))
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into value sink)))


(defn xml->UnsignedInt
  {:arglists '([element])}
  [{{:keys [id value]} :attrs extensions :content}]
  (if (or id (seq extensions))
    (->ExtendedUnsignedInt id extensions (Integer/parseInt value))
    (->UnsignedInt (Integer/parseInt value))))


(defn unsignedInt? [x]
  (identical? :fhir/unsignedInt (p/-type x)))



;; ---- positiveInt -----------------------------------------------------------

(deftype PositiveInt [^int value]
  p/FhirType
  (-type [_] :fhir/positiveInt)
  (-value [_] value)
  (-to-xml [_] (xml-node/element nil {:value (Integer/toString value)}))
  (-hash-into [_ sink]
    (.putByte ^PrimitiveSink sink (byte 18))                ; :fhir/positiveInt
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into value sink))
  Object
  (equals [_ x]
    (and (instance? PositiveInt x) (= value (.value ^PositiveInt x))))
  (hashCode [_]
    value)
  (toString [_]
    (Integer/toString value)))


(def ^:private positive-int-serializer
  (proxy [StdSerializer] [PositiveInt]
    (serialize [^PositiveInt positive-int ^JsonGenerator gen _]
      (.writeNumber gen ^int (.value positive-int)))))


(defmethod print-method PositiveInt [positive-int ^Writer w]
  (.write w "#fhir/positiveInt ")
  (.write w (str positive-int)))


(defrecord ExtendedPositiveInt [id extension ^int value]
  p/FhirType
  (-type [_] :fhir/positiveInt)
  (-value [_] value)
  (-to-xml [_] (xml-node/element* nil {:value (Integer/toString value)} extension))
  (-hash-into [_ sink]
    (.putByte ^PrimitiveSink sink (byte 18))                ; :fhir/positiveInt
    (when id
      (.putByte ^PrimitiveSink sink (byte 0))               ; :id
      (system/-hash-into id sink))
    (when extension
      (.putByte ^PrimitiveSink sink (byte 1))               ; :extension
      (p/-hash-into extension sink))
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into value sink)))


(defn xml->PositiveInt
  {:arglists '([element])}
  [{{:keys [id value]} :attrs extensions :content}]
  (if (or id (seq extensions))
    (->ExtendedPositiveInt id extensions (Integer/parseInt value))
    (->PositiveInt (Integer/parseInt value))))


(defn positiveInt? [x]
  (identical? :fhir/positiveInt (p/-type x)))



;; ---- uuid ------------------------------------------------------------------

(extend-protocol p/FhirType
  UUID
  (-type [_] :fhir/uuid)
  (-value [uuid] (str "urn:uuid:" uuid))
  (-to-xml [uuid] (xml-node/element nil {:value (str "urn:uuid:" uuid)}))
  (-hash-into [uuid sink]
    (.putByte ^PrimitiveSink sink (byte 19))                ; :fhir/uuid
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (.putLong ^PrimitiveSink sink (.getMostSignificantBits uuid))
    (.putLong ^PrimitiveSink sink (.getLeastSignificantBits uuid))))


(def ^:private uuid-serializer
  (proxy [StdSerializer] [UUID]
    (serialize [^UUID uuid ^JsonGenerator gen _]
      (.writeString gen (str "urn:uuid:" uuid)))))


(defn ->Uuid [s]
  (UUID/fromString (subs s 9)))


(defn xml->Uuid
  {:arglists '([element])}
  [{{:keys [_id value]} :attrs _extensions :content}]
  (->Uuid value))


(defn uuid? [x]
  (clojure.core/uuid? x))



;; ---- xhtml -----------------------------------------------------------------

(deftype Xhtml [value]
  p/FhirType
  (-type [_] :fhir/xhtml)
  (-value [_] value)
  (-to-xml [_] (update (xml/parse-str value) :attrs assoc :xmlns "http://www.w3.org/1999/xhtml"))
  (-hash-into [_ sink]
    (.putByte ^PrimitiveSink sink (byte 20))                ; :fhir/xhtml
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into value sink))
  Object
  (equals [_ x]
    (and (instance? Xhtml x) (= value (.value ^Xhtml x))))
  (hashCode [_]
    (.hashCode value))
  (toString [_]
    (str value)))


(def ^:private xhtml-serializer
  (proxy [StdSerializer] [Xhtml]
    (serialize [^Xhtml xhtml ^JsonGenerator gen _]
      (.writeString gen ^String (.value xhtml)))))


(defmethod print-method Xhtml [xhtml ^Writer w]
  (.write w "#fhir/xhtml\"")
  (.write w ^String (p/-value xhtml))
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
  (-value [_])
  (-hash-into [xs sink]
    (.putByte ^PrimitiveSink sink (byte 36))
    (reduce (fn [_ x] (p/-hash-into x sink)) nil xs))
  Keyword
  (-type [_])
  (-value [_])
  (-hash-into [k sink]
    (.putInt ^PrimitiveSink sink (.hasheq k)))
  Map
  (-type [m]
    (:fhir/type m))
  (-value [_])
  (-hash-into [m sink]
    (.putByte ^PrimitiveSink sink (byte 37))
    (reduce
      (fn [_ k]
        (p/-hash-into k sink)
        (p/-hash-into (k m) sink))
      nil
      (sort (keys m)))))


(declare extension-serializer)


(defcomplextype Extension [id extension url value]
  :hash-num 39
  :field-serializers
  {id :string
   extension ^{:cardinality :many} extension-serializer
   url :string
   value :dynamic-type})


(defcomplextype Coding [id extension system version code display]
  :hash-num 38
  :field-serializers
  {id :string
   extension ^{:cardinality :many} extension-serializer
   system uri-serializer
   version :string
   code code-serializer
   display :string})


(declare coding-serializer)


(defcomplextype CodeableConcept [id extension coding text]
  :hash-num 39
  :field-serializers
  {id :string
   extension ^{:cardinality :many} extension-serializer
   coding ^{:cardinality :many} coding-serializer
   text :string})


(declare codeable-concept-serializer)


(defcomplextype Quantity [id extension value comparator unit system code]
  :hash-num 40
  :field-serializers
  {id :string
   extension ^{:cardinality :many} extension-serializer
   value :decimal
   comparator code-serializer
   unit :string
   system uri-serializer
   code code-serializer})


(declare quantity-serializer)


(defcomplextype Period [id extension start end]
  :hash-num 41
  :field-serializers
  {id :string
   extension ^{:cardinality :many} extension-serializer
   start :dynamic
   end :dynamic})


(declare period-serializer)


(declare reference-serializer)


(defcomplextype Identifier [id extension use type system value period assigner]
  :hash-num 42
  :field-serializers
  {id :string
   extension ^{:cardinality :many} extension-serializer
   use code-serializer
   type codeable-concept-serializer
   system uri-serializer
   value :string
   period period-serializer
   assigner reference-serializer})


(declare identifier-serializer)


(defcomplextype Reference [id extension reference type identifier display]
  :hash-num 43
  :field-serializers
  {id :string
   extension ^{:cardinality :many} extension-serializer
   reference :string
   type uri-serializer
   identifier identifier-serializer
   display :string})


(defcomplextype Meta [id extension versionId lastUpdated source profile security tag]
  :hash-num 44
  :field-serializers
  {id :string
   extension ^{:cardinality :many} extension-serializer
   versionId id-serializer
   lastUpdated :dynamic
   source uri-serializer
   profile ^{:cardinality :many} canonical-serializer
   security ^{:cardinality :many} coding-serializer
   tag ^{:cardinality :many} coding-serializer})


(declare meta-serializer)


(defcomplextype BundleEntrySearch [id extension mode score]
  :fhir-type :fhir.Bundle.entry/search
  :hash-num 45
  :field-serializers
  {id :string
   extension ^{:cardinality :many} extension-serializer
   mode code-serializer
   score :decimal})


(declare bundle-entry-search-serializer)



;; ---- Jackson Databind Module -----------------------------------------------

(def fhir-module
  (doto (SimpleModule. "FHIR")
    (.addSerializer Uri uri-serializer)
    (.addSerializer Url url-serializer)
    (.addSerializer Canonical canonical-serializer)
    (.addSerializer Base64Binary base64-binary-serializer)
    (.addSerializer OffsetInstant offset-instant-serializer)
    (.addSerializer DateTimeYear date-time-year-serializer)
    (.addSerializer DateTimeYearMonth date-time-year-month-serializer)
    (.addSerializer DateTimeYearMonthDay date-time-year-month-day-serializer)
    (.addSerializer ExtendedDateTime extended-date-time-serializer)
    (.addSerializer Code code-serializer)
    (.addSerializer ExtendedCode extended-code-serializer)
    (.addSerializer Oid oid-serializer)
    (.addSerializer Id id-serializer)
    (.addSerializer Markdown markdown-serializer)
    (.addSerializer UnsignedInt unsigned-int-serializer)
    (.addSerializer PositiveInt positive-int-serializer)
    (.addSerializer UUID uuid-serializer)
    (.addSerializer Xhtml xhtml-serializer)
    (.addSerializer Extension extension-serializer)
    (.addSerializer Coding coding-serializer)
    (.addSerializer CodeableConcept codeable-concept-serializer)
    (.addSerializer Quantity quantity-serializer)
    (.addSerializer Period period-serializer)
    (.addSerializer Identifier identifier-serializer)
    (.addSerializer Reference reference-serializer)
    (.addSerializer Meta meta-serializer)
    (.addSerializer BundleEntrySearch bundle-entry-search-serializer)))



;; ---- print -----------------------------------------------------------------

(defmethod print-dup (Class/forName "[B") [^bytes year ^Writer w]
  (.write w "#=(byte-array [")
  (.write w ^String (str/join " " (map int (vec year))))
  (.write w "])"))


(defmethod print-dup Year [^Year year ^Writer w]
  (.write w "#=(java.time.Year/of ")
  (.write w (str (.getValue year)))
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
