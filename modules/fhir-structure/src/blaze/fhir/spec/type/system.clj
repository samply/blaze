(ns blaze.fhir.spec.type.system
  "System types:
   * Boolean
   * String
   * Integer
   * Long
   * Decimal
   * Date
   * DateTime
   * Time
   * Quantity"
  (:import
    [com.google.common.hash PrimitiveSink]
    [java.nio.charset StandardCharsets]
    [java.time LocalDate LocalDateTime LocalTime OffsetDateTime Year YearMonth]
    [java.time.temporal Temporal TemporalUnit TemporalAccessor TemporalField])
  (:refer-clojure :exclude [type]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defprotocol SystemType
  (-type [_])
  (-hash-into [_ sink])
  (-equals [_ x]))


(defn value?
  "Returns true if `x` is a value of one of the system types."
  [x]
  (= "system" (some-> (-type x) namespace)))


(defn type
  "Returns the type of `x` as keyword with the namespace `system` or nil if `x`
  is no system value."
  [x]
  (-type x))


(defn date? [x]
  (identical? :system/date (-type x)))


(defn date-time? [x]
  (identical? :system/date-time (-type x)))


(defn equals
  "Implements equals between two system types according to
  http://hl7.org/fhirpath/#equals."
  [a b]
  (-equals a b))


(deftype DateTimeYear [year]
  SystemType
  (-type [_] :system/date-time)
  (-hash-into [_ sink]
    (.putByte ^PrimitiveSink sink (byte 6))
    (.putInt ^PrimitiveSink sink (.getValue ^Year year)))
  (-equals [_ x]
    (cond
      (instance? DateTimeYear x) (.equals year (.year ^DateTimeYear x))
      (instance? Year x) (.equals year x)))
  Temporal
  (^boolean isSupported [_ ^TemporalUnit unit]
    (.isSupported ^Year year unit))
  (until [_ endExclusive unit]
    (.until ^Year year endExclusive unit))
  TemporalAccessor
  (^boolean isSupported [_ ^TemporalField field]
    (.isSupported ^Year year field))
  (^long getLong [_ ^TemporalField field]
    (.getLong ^Year year field))
  Object
  (equals [_ x]
    (and (instance? DateTimeYear x) (.equals year (.year ^DateTimeYear x))))
  (hashCode [_]
    (.hashCode year))
  (toString [_]
    (.toString year)))


(defn ->DateTimeYear [year]
  (DateTimeYear. (Year/of year)))


(deftype DateTimeYearMonth [yearMonth]
  SystemType
  (-type [_] :system/date-time)
  (-hash-into [_ sink]
    (.putByte ^PrimitiveSink sink (byte 6))
    (.putInt ^PrimitiveSink sink (.getYear ^YearMonth yearMonth))
    (.putInt ^PrimitiveSink sink (.getMonthValue ^YearMonth yearMonth)))
  (-equals [_ x]
    (cond
      (instance? DateTimeYearMonth x)
      (.equals yearMonth (.yearMonth ^DateTimeYearMonth x))
      (instance? YearMonth x) (.equals yearMonth x)))
  Temporal
  (^boolean isSupported [_ ^TemporalUnit unit]
    (.isSupported ^YearMonth yearMonth unit))
  (until [_ endExclusive unit]
    (.until ^YearMonth yearMonth endExclusive unit))
  TemporalAccessor
  (^boolean isSupported [_ ^TemporalField field]
    (.isSupported ^YearMonth yearMonth field))
  (^long getLong [_ ^TemporalField field]
    (.getLong ^YearMonth yearMonth field))
  Object
  (equals [_ x]
    (and (instance? DateTimeYearMonth x)
         (.equals yearMonth (.yearMonth ^DateTimeYearMonth x))))
  (hashCode [_]
    (.hashCode yearMonth))
  (toString [_]
    (.toString yearMonth)))


(defn ->DateTimeYearMonth [year month]
  (DateTimeYearMonth. (YearMonth/of ^int year ^int month)))


(deftype DateTimeYearMonthDay [date]
  SystemType
  (-type [_] :system/date-time)
  (-hash-into [_ sink]
    (.putByte ^PrimitiveSink sink (byte 6))
    (.putInt ^PrimitiveSink sink (.getYear ^LocalDate date))
    (.putInt ^PrimitiveSink sink (.getMonthValue ^LocalDate date))
    (.putInt ^PrimitiveSink sink (.getDayOfMonth ^LocalDate date)))
  (-equals [_ x]
    (cond
      (instance? DateTimeYearMonthDay x)
      (.equals date (.date ^DateTimeYearMonthDay x))
      (instance? LocalDate x) (.equals date x)))
  Temporal
  (^boolean isSupported [_ ^TemporalUnit unit]
    (.isSupported ^LocalDate date unit))
  (until [_ endExclusive unit]
    (.until ^LocalDate date endExclusive unit))
  TemporalAccessor
  (^boolean isSupported [_ ^TemporalField field]
    (.isSupported ^LocalDate date field))
  (^long getLong [_ ^TemporalField field]
    (.getLong ^LocalDate date field))
  Object
  (equals [_ x]
    (and (instance? DateTimeYearMonthDay x)
         (.equals date (.date ^DateTimeYearMonthDay x))))
  (hashCode [_]
    (.hashCode date))
  (toString [_]
    (.toString date)))


(defn ->DateTimeYearMonthDay [year month day]
  (DateTimeYearMonthDay. (LocalDate/of ^int year ^int month ^int day)))


(defn parse-date-time [s]
  (condp < (count s)
    10 (if (re-find #"(Z|[+-]\d{2}:)" s)
         (OffsetDateTime/parse s)
         (LocalDateTime/parse s))
    7 (DateTimeYearMonthDay. (LocalDate/parse s))
    4 (DateTimeYearMonth. (YearMonth/parse s))
    (DateTimeYear. (Year/parse s))))


(extend-protocol SystemType
  Boolean
  (-type [_] :system/boolean)
  (-hash-into [b sink]
    (.putByte ^PrimitiveSink sink (byte 0))
    (.putBoolean ^PrimitiveSink sink b))
  (-equals [a b] (.equals a b))
  String
  (-type [_] :system/string)
  (-hash-into [s sink]
    (.putByte ^PrimitiveSink sink (byte 1))
    (.putString ^PrimitiveSink sink s StandardCharsets/UTF_8))
  (-equals [a b] (.equals a b))
  Integer
  (-type [_] :system/integer)
  (-hash-into [i sink]
    (.putByte ^PrimitiveSink sink (byte 2))
    (.putInt ^PrimitiveSink sink i))
  Long
  (-type [_] :system/long)
  (-hash-into [l sink]
    (.putByte ^PrimitiveSink sink (byte 3))
    (.putInt ^PrimitiveSink sink l))
  BigDecimal
  (-type [_] :system/decimal)
  (-hash-into [d sink]
    (.putByte ^PrimitiveSink sink (byte 4))
    (.putString ^PrimitiveSink sink (str d) StandardCharsets/UTF_8))
  Year
  (-type [_] :system/date)
  (-hash-into [date sink]
    (.putByte ^PrimitiveSink sink (byte 5))
    (.putInt ^PrimitiveSink sink (.getValue date)))
  (-equals [d x]
    (cond
      (instance? Year x) (.equals d x)
      (instance? DateTimeYear x) (.equals d (.year ^DateTimeYear x))))
  YearMonth
  (-type [_] :system/date)
  (-hash-into [date sink]
    (.putByte ^PrimitiveSink sink (byte 5))
    (.putInt ^PrimitiveSink sink (.getYear date))
    (.putInt ^PrimitiveSink sink (.getMonthValue date)))
  (-equals [d x]
    (cond
      (instance? YearMonth x) (.equals d x)
      (instance? DateTimeYearMonth x)
      (.equals d (.yearMonth ^DateTimeYearMonth x))))
  LocalDate
  (-type [_] :system/date)
  (-hash-into [date sink]
    (.putByte ^PrimitiveSink sink (byte 5))
    (.putInt ^PrimitiveSink sink (.getYear date))
    (.putInt ^PrimitiveSink sink (.getMonthValue date))
    (.putInt ^PrimitiveSink sink (.getDayOfMonth date)))
  (-equals [d x]
    (cond
      (instance? LocalDate x) (.equals d x)
      (instance? DateTimeYearMonthDay x)
      (.equals d (.date ^DateTimeYearMonthDay x))))
  LocalDateTime
  (-type [_] :system/date-time)
  (-hash-into [dateTime sink]
    (.putByte ^PrimitiveSink sink (byte 6))
    (.putInt ^PrimitiveSink sink (.getYear dateTime))
    (.putInt ^PrimitiveSink sink (.getMonthValue dateTime))
    (.putInt ^PrimitiveSink sink (.getDayOfMonth dateTime))
    (.putInt ^PrimitiveSink sink (.getHour dateTime))
    (.putInt ^PrimitiveSink sink (.getMinute dateTime))
    (.putInt ^PrimitiveSink sink (.getSecond dateTime))
    (.putInt ^PrimitiveSink sink (.getNano dateTime)))
  OffsetDateTime
  (-type [_] :system/date-time)
  (-hash-into [dateTime sink]
    (.putByte ^PrimitiveSink sink (byte 6))
    (.putInt ^PrimitiveSink sink (.getYear dateTime))
    (.putInt ^PrimitiveSink sink (.getMonthValue dateTime))
    (.putInt ^PrimitiveSink sink (.getDayOfMonth dateTime))
    (.putInt ^PrimitiveSink sink (.getHour dateTime))
    (.putInt ^PrimitiveSink sink (.getMinute dateTime))
    (.putInt ^PrimitiveSink sink (.getSecond dateTime))
    (.putInt ^PrimitiveSink sink (.getNano dateTime))
    (.putInt ^PrimitiveSink sink (.getTotalSeconds (.getOffset dateTime))))
  LocalTime
  (-hash-into [time sink]
    (.putByte ^PrimitiveSink sink (byte 7))
    (.putInt ^PrimitiveSink sink (.getHour time))
    (.putInt ^PrimitiveSink sink (.getMinute time))
    (.putInt ^PrimitiveSink sink (.getSecond time))
    (.putInt ^PrimitiveSink sink (.getNano time)))
  Object
  (-type [_])
  (-hash-into [_ _])
  (-equals [_ _] false)
  nil
  (-type [_])
  (-hash-into [_ _])
  (-equals [_ _] false))
