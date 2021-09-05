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
  (:refer-clojure :exclude [boolean? decimal? integer? string? type])
  (:require
    [blaze.anomaly :as ba]
    [blaze.anomaly-spec]
    [cognitect.anomalies :as anom]
    [java-time.core :as time-core])
  (:import
    [com.google.common.hash PrimitiveSink]
    [java.nio.charset StandardCharsets]
    [java.time LocalDate LocalDateTime LocalTime OffsetDateTime Year YearMonth]
    [java.time.temporal Temporal TemporalUnit TemporalAccessor TemporalField]
    [java.time.format DateTimeParseException]))


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


(defn equals
  "Implements equals between two system types according to
  http://hl7.org/fhirpath/#equals."
  [a b]
  (-equals a b))



;; ---- System.Boolean --------------------------------------------------------

(extend-protocol SystemType
  Boolean
  (-type [_]
    :system/boolean)
  (-hash-into [b sink]
    (.putByte ^PrimitiveSink sink (byte 0))
    (.putBoolean ^PrimitiveSink sink b))
  (-equals [b x]
    (some->> x (.equals b))))


(defn boolean? [x]
  (identical? :system/boolean (-type x)))



;; ---- System.Integer --------------------------------------------------------

(extend-protocol SystemType
  Integer
  (-type [_]
    :system/integer)
  (-hash-into [i sink]
    (.putByte ^PrimitiveSink sink (byte 2))
    (.putInt ^PrimitiveSink sink i))
  (-equals [i x]
    (some->> x (.equals i))))


(defn integer? [x]
  (identical? :system/integer (-type x)))



;; ---- System.Long -----------------------------------------------------------

(extend-protocol SystemType
  Long
  (-type [_]
    :system/long)
  (-hash-into [l sink]
    (.putByte ^PrimitiveSink sink (byte 3))
    (.putInt ^PrimitiveSink sink l))
  (-equals [l x]
    (some->> x (.equals l))))


(defn long? [x]
  (identical? :system/long (-type x)))



;; ---- System.String ---------------------------------------------------------

(extend-protocol SystemType
  String
  (-type [_]
    :system/string)
  (-hash-into [s sink]
    (.putByte ^PrimitiveSink sink (byte 1))
    (.putString ^PrimitiveSink sink s StandardCharsets/UTF_8))
  (-equals [s x]
    (some->> x (.equals s))))


(defn string? [x]
  (identical? :system/string (-type x)))



;; ---- System.Decimal --------------------------------------------------------

(extend-protocol SystemType
  BigDecimal
  (-type [_]
    :system/decimal)
  (-hash-into [d sink]
    (.putByte ^PrimitiveSink sink (byte 4))
    (.putString ^PrimitiveSink sink (str d) StandardCharsets/UTF_8))
  (-equals [d x]
    (some->> x (.equals d))))


(defn decimal? [x]
  (identical? :system/decimal (-type x)))


(defn- decimal-string?
  "Returns true if `s` is a valid string representation of a decimal value."
  [s]
  (.matches (re-matcher #"-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?" s)))


(defn parse-decimal [s]
  (if (decimal-string? s)
    (BigDecimal. ^String s)
    (ba/incorrect (format "Invalid decimal value `%s`." s))))



;; ---- System.Date -----------------------------------------------------------

(defn date? [x]
  (identical? :system/date (-type x)))


(defn date
  ([year]
   (Year/of year))
  ([year month]
   (YearMonth/of ^int year ^int month))
  ([year month day]
   (LocalDate/of ^int year ^int month ^int day)))


(defn parse-date* [s]
  (case (count s)
    10 (LocalDate/parse s)
    7 (YearMonth/parse s)
    (Year/parse s)))


(defn- date-string? [s]
  (.matches (re-matcher #"([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1]))?)?" s)))


(defn parse-date
  "Parses `s` into a System.Date.

  Returns an anomaly if `s` isn't a valid System.Date."
  [s]
  (if (date-string? s)
    (ba/try-one DateTimeParseException ::anom/incorrect (parse-date* s))
    (ba/incorrect (format "Invalid date-time value `%s`." s))))



;; ---- System.DateTime -------------------------------------------------------

(defn date-time? [x]
  (identical? :system/date-time (-type x)))


(deftype DateTimeYear [year]
  SystemType
  (-type [_]
    :system/date-time)
  (-hash-into [_ sink]
    (.putByte ^PrimitiveSink sink (byte 6))
    (.putInt ^PrimitiveSink sink (.getValue ^Year year)))
  (-equals [_ x]
    (cond
      (instance? DateTimeYear x) (.equals year (.-year ^DateTimeYear x))
      (instance? Year x) (.equals year x)))

  time-core/Ordered
  (single-before? [_ x]
    (and (instance? DateTimeYear x) (.isBefore ^Year year (.-year ^DateTimeYear x))))
  (single-after? [_ x]
    (and (instance? DateTimeYear x) (.isAfter ^Year year (.-year ^DateTimeYear x))))

  Temporal
  (^boolean isSupported [_ ^TemporalUnit unit]
    (.isSupported ^Year year unit))
  (plus [_ amount-to-add]
    (DateTimeYear. (.plus ^Year year amount-to-add)))
  (plus [_ amount-to-add unit]
    (DateTimeYear. (.plus ^Year year amount-to-add unit)))
  (until [_ endExclusive unit]
    (.until ^Year year endExclusive unit))

  TemporalAccessor
  (^boolean isSupported [_ ^TemporalField field]
    (.isSupported ^Year year field))
  (^long getLong [_ ^TemporalField field]
    (.getLong ^Year year field))

  Comparable
  (compareTo [_ x]
    (.compareTo ^Year year (.-year ^DateTimeYear x)))

  Object
  (equals [_ x]
    (and (instance? DateTimeYear x) (.equals year (.-year ^DateTimeYear x))))
  (hashCode [_]
    (.hashCode year))
  (toString [_]
    (.toString year)))


(deftype DateTimeYearMonth [year-month]
  SystemType
  (-type [_]
    :system/date-time)
  (-hash-into [_ sink]
    (.putByte ^PrimitiveSink sink (byte 6))
    (.putInt ^PrimitiveSink sink (.getYear ^YearMonth year-month))
    (.putInt ^PrimitiveSink sink (.getMonthValue ^YearMonth year-month)))
  (-equals [_ x]
    (cond
      (instance? DateTimeYearMonth x)
      (.equals year-month (.-year_month ^DateTimeYearMonth x))
      (instance? YearMonth x) (.equals year-month x)))

  time-core/Ordered
  (single-before? [_ x]
    (and (instance? DateTimeYearMonth x)
         (.isBefore ^YearMonth year-month (.-year_month ^DateTimeYearMonth x))))
  (single-after? [_ x]
    (and (instance? DateTimeYearMonth x)
         (.isAfter ^YearMonth year-month (.-year_month ^DateTimeYearMonth x))))

  Temporal
  (^boolean isSupported [_ ^TemporalUnit unit]
    (.isSupported ^YearMonth year-month unit))
  (plus [_ amount-to-add]
    (DateTimeYearMonth. (.plus ^YearMonth year-month amount-to-add)))
  (plus [_ amount-to-add unit]
    (DateTimeYearMonth. (.plus ^YearMonth year-month amount-to-add unit)))
  (until [_ endExclusive unit]
    (.until ^YearMonth year-month endExclusive unit))

  TemporalAccessor
  (^boolean isSupported [_ ^TemporalField field]
    (.isSupported ^YearMonth year-month field))
  (^long getLong [_ ^TemporalField field]
    (.getLong ^YearMonth year-month field))

  Comparable
  (compareTo [_ x]
    (.compareTo ^YearMonth year-month (.-year_month ^DateTimeYearMonth x)))

  Object
  (equals [_ x]
    (and (instance? DateTimeYearMonth x)
         (.equals year-month (.-year_month ^DateTimeYearMonth x))))
  (hashCode [_]
    (.hashCode year-month))
  (toString [_]
    (.toString year-month)))


(deftype DateTimeYearMonthDay [date]
  SystemType
  (-type [_]
    :system/date-time)
  (-hash-into [_ sink]
    (.putByte ^PrimitiveSink sink (byte 6))
    (.putInt ^PrimitiveSink sink (.getYear ^LocalDate date))
    (.putInt ^PrimitiveSink sink (.getMonthValue ^LocalDate date))
    (.putInt ^PrimitiveSink sink (.getDayOfMonth ^LocalDate date)))
  (-equals [_ x]
    (cond
      (instance? DateTimeYearMonthDay x)
      (.equals date (.-date ^DateTimeYearMonthDay x))
      (instance? LocalDate x) (.equals date x)))

  time-core/Ordered
  (single-before? [_ x]
    (and (instance? DateTimeYearMonthDay x)
         (.isBefore ^LocalDate date (.-date ^DateTimeYearMonthDay x))))
  (single-after? [_ x]
    (and (instance? DateTimeYearMonthDay x)
         (.isAfter ^LocalDate date (.-date ^DateTimeYearMonthDay x))))

  Temporal
  (^boolean isSupported [_ ^TemporalUnit unit]
    (.isSupported ^LocalDate date unit))
  (plus [_ amount-to-add]
    (DateTimeYearMonthDay. (.plus ^LocalDate date amount-to-add)))
  (plus [_ amount-to-add unit]
    (DateTimeYearMonthDay. (.plus ^LocalDate date amount-to-add unit)))
  (until [_ endExclusive unit]
    (.until ^LocalDate date endExclusive unit))

  TemporalAccessor
  (^boolean isSupported [_ ^TemporalField field]
    (.isSupported ^LocalDate date field))
  (^long getLong [_ ^TemporalField field]
    (.getLong ^LocalDate date field))

  Comparable
  (compareTo [_ x]
    (.compareTo ^LocalDate date (.-date ^DateTimeYearMonthDay x)))

  Object
  (equals [_ x]
    (and (instance? DateTimeYearMonthDay x)
         (.equals date (.-date ^DateTimeYearMonthDay x))))
  (hashCode [_]
    (.hashCode date))
  (toString [_]
    (.toString date)))


(defn date-time
  ([year]
   (DateTimeYear. (Year/of year)))
  ([year month]
   (DateTimeYearMonth. (YearMonth/of ^int year ^int month)))
  ([year month day]
   (DateTimeYearMonthDay. (LocalDate/of ^int year ^int month ^int day)))
  ([year month day hour]
   (LocalDateTime/of ^int year ^int month ^int day ^int hour 0))
  ([year month day hour minute]
   (LocalDateTime/of ^int year ^int month ^int day ^int hour ^int minute))
  ([year month day hour minute second]
   (LocalDateTime/of ^int year ^int month ^int day ^int hour ^int minute
                     ^int second))
  ([year month day hour minute second millis]
   (LocalDateTime/of ^int year ^int month ^int day ^int hour ^int minute
                     ^int second (int (* ^long millis 1000000))))
  ([year month day hour minute second millis zone-offset]
   (OffsetDateTime/of ^int year ^int month ^int day ^int hour ^int minute
                      ^int second (int (* ^long millis 1000000)) zone-offset)))


(defn parse-date-time* [s]
  (condp < (count s)
    13 (try
         (OffsetDateTime/parse s)
         (catch Exception _
           (LocalDateTime/parse s)))
    10 (LocalDateTime/parse (str s ":00"))
    7 (DateTimeYearMonthDay. (LocalDate/parse s))
    4 (DateTimeYearMonth. (YearMonth/parse s))
    (DateTimeYear. (Year/parse s))))


(defn- date-time-string? [s]
  (.matches (re-matcher #"([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1])(T([01][0-9]|2[0-3])(:[0-5][0-9](:([0-5][0-9]|60)(\.[0-9]+)?)?)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00))?)?)?)?" s)))


(defn parse-date-time [s]
  (if (date-time-string? s)
    (ba/try-one DateTimeParseException ::anom/incorrect (parse-date-time* s))
    (ba/incorrect (format "Invalid date-time value `%s`." s))))


(extend-protocol SystemType
  Year
  (-type [_]
    :system/date)
  (-hash-into [date sink]
    (.putByte ^PrimitiveSink sink (byte 5))
    (.putInt ^PrimitiveSink sink (.getValue date)))
  (-equals [date x]
    (cond
      (instance? Year x) (.equals date x)
      (instance? DateTimeYear x) (.equals date (.-year ^DateTimeYear x))))

  YearMonth
  (-type [_]
    :system/date)
  (-hash-into [date sink]
    (.putByte ^PrimitiveSink sink (byte 5))
    (.putInt ^PrimitiveSink sink (.getYear date))
    (.putInt ^PrimitiveSink sink (.getMonthValue date)))
  (-equals [date x]
    (cond
      (instance? YearMonth x) (.equals date x)
      (instance? DateTimeYearMonth x)
      (.equals date (.-year_month ^DateTimeYearMonth x))))

  LocalDate
  (-type [_]
    :system/date)
  (-hash-into [date sink]
    (.putByte ^PrimitiveSink sink (byte 5))
    (.putInt ^PrimitiveSink sink (.getYear date))
    (.putInt ^PrimitiveSink sink (.getMonthValue date))
    (.putInt ^PrimitiveSink sink (.getDayOfMonth date)))
  (-equals [date x]
    (cond
      (instance? LocalDate x) (.equals date x)
      (instance? DateTimeYearMonthDay x)
      (.equals date (.-date ^DateTimeYearMonthDay x))))

  LocalDateTime
  (-type [_] :system/date-time)
  (-hash-into [date-time sink]
    (.putByte ^PrimitiveSink sink (byte 6))
    (.putInt ^PrimitiveSink sink (.getYear date-time))
    (.putInt ^PrimitiveSink sink (.getMonthValue date-time))
    (.putInt ^PrimitiveSink sink (.getDayOfMonth date-time))
    (.putInt ^PrimitiveSink sink (.getHour date-time))
    (.putInt ^PrimitiveSink sink (.getMinute date-time))
    (.putInt ^PrimitiveSink sink (.getSecond date-time))
    (.putInt ^PrimitiveSink sink (.getNano date-time)))
  (-equals [date-time x]
    (cond
      (instance? LocalDateTime x) (.equals date-time x)))

  OffsetDateTime
  (-type [_] :system/date-time)
  (-hash-into [date-time sink]
    (.putByte ^PrimitiveSink sink (byte 6))
    (.putInt ^PrimitiveSink sink (.getYear date-time))
    (.putInt ^PrimitiveSink sink (.getMonthValue date-time))
    (.putInt ^PrimitiveSink sink (.getDayOfMonth date-time))
    (.putInt ^PrimitiveSink sink (.getHour date-time))
    (.putInt ^PrimitiveSink sink (.getMinute date-time))
    (.putInt ^PrimitiveSink sink (.getSecond date-time))
    (.putInt ^PrimitiveSink sink (.getNano date-time))
    (.putInt ^PrimitiveSink sink (.getTotalSeconds (.getOffset date-time))))
  (-equals [date-time x]
    (cond
      (instance? OffsetDateTime x) (.equals date-time x))))



;; ---- System.Time -----------------------------------------------------------

(extend-protocol SystemType
  LocalTime
  (-type [_] :system/time)
  (-hash-into [time sink]
    (.putByte ^PrimitiveSink sink (byte 7))
    (.putInt ^PrimitiveSink sink (.getHour time))
    (.putInt ^PrimitiveSink sink (.getMinute time))
    (.putInt ^PrimitiveSink sink (.getSecond time))
    (.putInt ^PrimitiveSink sink (.getNano time)))
  (-equals [time x]
    (some->> x (.equals time))))



;; ---- Other -----------------------------------------------------------------

(extend-protocol SystemType
  Object
  (-type [_])
  (-hash-into [_ _])
  (-equals [_ _] false)

  nil
  (-type [_])
  (-hash-into [_ _])
  (-equals [_ _]))
