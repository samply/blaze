(ns blaze.fhir.spec.type
  (:require
    [blaze.fhir.spec.type.protocols :as p]
    [blaze.fhir.spec.type.system :as system]
    [clojure.alpha.spec :as s2]
    [clojure.data.xml :as xml]
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
    [java.util List Map UUID])
  (:refer-clojure :exclude [decimal? string? type uri?]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn primitive? [x]
  (when-let [type (p/-type x)]
    (Character/isLowerCase ^char (first (name type)))))


(defn type [x]
  (p/-type x))


(defn value
  "Returns the possible value of the primitive value `x` as FHIRPath system
  type."
  [x]
  (p/-value x))


(defn to-json [x]
  (p/-to-json x))


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
  (-to-json [_])
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
  (-to-json [b] b)
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
  (-to-json [i] i)
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
  (-to-json [i] i)
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
  (-to-json [s] s)
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
  (-to-json [d] d)
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
  (-to-json [_] value)
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
  (-to-json [_] value)
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
  (-to-json [_] value)
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
  (-to-json [_] value)
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


(defn xml->Base64Binary
  {:arglists '([element])}
  [{{:keys [_id value]} :attrs _extensions :content}]
  (->Base64Binary value))


(defn base64Binary? [x]
  (instance? Base64Binary x))



;; ---- instant ---------------------------------------------------------------

;; Implementation of a FHIR instant with a variable ZoneOffset.
(deftype OffsetInstant [^OffsetDateTime value]
  p/FhirType
  (-type [_] :fhir/instant)
  (-value [_] value)
  (-to-json [_] (p/-to-json value))
  (-to-xml [_] (xml-node/element nil {:value (p/-to-json value)}))
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


(extend-protocol p/FhirType
  Instant
  (-type [_] :fhir/instant)
  (-value [instant] (.atOffset instant ZoneOffset/UTC))
  (-to-json [instant] (str instant))
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
  (-to-json [date] (str date))
  (-to-xml [date] (xml-node/element nil {:value (str date)}))
  (-hash-into [date sink]
    (.putByte ^PrimitiveSink sink (byte 10))                ; :fhir/date
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into date sink))
  YearMonth
  (-type [_] :fhir/date)
  (-value [date] date)
  (-to-json [date] (str date))
  (-to-xml [date] (xml-node/element nil {:value (str date)}))
  (-hash-into [date sink]
    (.putByte ^PrimitiveSink sink (byte 10))                ; :fhir/date
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into date sink))
  LocalDate
  (-type [_] :fhir/date)
  (-value [date] date)
  (-to-json [date] (str date))
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
  (-to-json [year] (str year))
  (-to-xml [year] (xml-node/element nil {:value (str year)}))
  (-hash-into [year sink]
    (.putByte ^PrimitiveSink sink (byte 11))                ; :fhir/dateTime
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into year sink))
  DateTimeYearMonth
  (-type [_] :fhir/dateTime)
  (-value [year-month] year-month)
  (-to-json [year-month] (str year-month))
  (-to-xml [year-month] (xml-node/element nil {:value (str year-month)}))
  (-hash-into [year-month sink]
    (.putByte ^PrimitiveSink sink (byte 11))                ; :fhir/dateTime
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into year-month sink))
  DateTimeYearMonthDay
  (-type [_] :fhir/dateTime)
  (-value [year-month-day] year-month-day)
  (-to-json [year-month-day] (str year-month-day))
  (-to-xml [year-month-day] (xml-node/element nil {:value (str year-month-day)}))
  (-hash-into [year-month-day sink]
    (.putByte ^PrimitiveSink sink (byte 11))                ; :fhir/dateTime
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into year-month-day sink)))


(extend-protocol p/FhirType
  OffsetDateTime
  (-type [_] :fhir/dateTime)
  (-value [date-time] date-time)
  (-to-json [date-time] (.format DateTimeFormatter/ISO_DATE_TIME date-time))
  (-to-xml [date-time] (xml-node/element nil {:value (p/-to-json date-time)}))
  (-hash-into [date-time sink]
    (.putByte ^PrimitiveSink sink (byte 11))                ; :fhir/dateTime
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into date-time sink))
  LocalDateTime
  (-type [_] :fhir/dateTime)
  (-value [date-time] date-time)
  (-to-json [date-time] (.format DateTimeFormatter/ISO_LOCAL_DATE_TIME date-time))
  (-to-xml [date-time] (xml-node/element nil {:value (p/-to-json date-time)}))
  (-hash-into [date-time sink]
    (.putByte ^PrimitiveSink sink (byte 11))                ; :fhir/dateTime
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into date-time sink)))


(defrecord ExtendedDateTime [id extensions value]
  p/FhirType
  (-type [_] :fhir/dateTime)
  (-value [_] value)
  (-to-json [_] (str value))
  (-to-xml [_] (xml-node/element* id {:value (str value)} extensions))
  (-hash-into [_ sink]
    (.putByte ^PrimitiveSink sink (byte 11))                ; :fhir/dateTime
    (when id
      (.putByte ^PrimitiveSink sink (byte 0))               ; :id
      (system/-hash-into id sink))
    (when extensions
      (.putByte ^PrimitiveSink sink (byte 1))               ; :extensions
      (p/-hash-into extensions sink))
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into value sink)))


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

(extend-protocol p/FhirType
  LocalTime
  (-type [_] :fhir/time)
  (-value [time] time)
  (-to-json [time] (.format DateTimeFormatter/ISO_LOCAL_TIME time))
  (-to-xml [time] (xml-node/element nil {:value (p/-to-json time)}))
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
  (-to-json [_] value)
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


(defmethod print-method Code [code ^Writer w]
  (.write w "#fhir/code\"")
  (.write w ^String (p/-value code))
  (.write w "\""))


(defrecord ExtendedCode [id extensions value]
  p/FhirType
  (-type [_] :fhir/code)
  (-value [_] value)
  (-to-json [_] (str value))
  (-to-xml [_] (xml-node/element* nil {:value value} extensions))
  (-hash-into [_ sink]
    (.putByte ^PrimitiveSink sink (byte 13))                ; :fhir/code
    (when id
      (.putByte ^PrimitiveSink sink (byte 0))               ; :id
      (system/-hash-into id sink))
    (when extensions
      (.putByte ^PrimitiveSink sink (byte 1))               ; :extensions
      (p/-hash-into extensions sink))
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (system/-hash-into value sink)))


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
  (-to-json [_] value)
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
  (-to-json [_] value)
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
  (-to-json [_] value)
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
  (-to-json [_] value)
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


(defmethod print-method UnsignedInt [unsignedInt ^Writer w]
  (.write w "#fhir/unsignedInt ")
  (.write w (str unsignedInt)))


(defrecord ExtendedUnsignedInt [id extensions ^int value]
  p/FhirType
  (-type [_] :fhir/unsignedInt)
  (-value [_] value)
  (-to-json [_] value)
  (-to-xml [_] (xml-node/element* nil {:value (Integer/toString value)} extensions))
  (-hash-into [_ sink]
    (.putByte ^PrimitiveSink sink (byte 17))                ; :fhir/unsignedInt
    (when id
      (.putByte ^PrimitiveSink sink (byte 0))               ; :id
      (system/-hash-into id sink))
    (when extensions
      (.putByte ^PrimitiveSink sink (byte 1))               ; :extensions
      (p/-hash-into extensions sink))
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
  (-to-json [_] value)
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


(defmethod print-method PositiveInt [positiveInt ^Writer w]
  (.write w "#fhir/positiveInt ")
  (.write w (str positiveInt)))


(defrecord ExtendedPositiveInt [id extensions ^int value]
  p/FhirType
  (-type [_] :fhir/positiveInt)
  (-value [_] value)
  (-to-json [_] value)
  (-to-xml [_] (xml-node/element* nil {:value (Integer/toString value)} extensions))
  (-hash-into [_ sink]
    (.putByte ^PrimitiveSink sink (byte 18))                ; :fhir/positiveInt
    (when id
      (.putByte ^PrimitiveSink sink (byte 0))               ; :id
      (system/-hash-into id sink))
    (when extensions
      (.putByte ^PrimitiveSink sink (byte 1))               ; :extensions
      (p/-hash-into extensions sink))
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
  (-to-json [uuid] (str "urn:uuid:" uuid))
  (-to-xml [uuid] (xml-node/element nil {:value (str "urn:uuid:" uuid)}))
  (-hash-into [uuid sink]
    (.putByte ^PrimitiveSink sink (byte 19))                ; :fhir/uuid
    (.putByte ^PrimitiveSink sink (byte 2))                 ; :value
    (.putLong ^PrimitiveSink sink (.getMostSignificantBits uuid))
    (.putLong ^PrimitiveSink sink (.getLeastSignificantBits uuid))))


(defn ->Uuid [s]
  (UUID/fromString (subs s 9)))


(defn xml->Uuid
  {:arglists '([element])}
  [{{:keys [_id value]} :attrs _extensions :content}]
  (->Uuid value))



;; ---- xhtml -----------------------------------------------------------------

(deftype Xhtml [value]
  p/FhirType
  (-type [_] :fhir/xhtml)
  (-value [_] value)
  (-to-json [_] value)
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
