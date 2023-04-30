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
  (:refer-clojure :exclude [boolean? decimal? integer? string? time type])
  (:require
    [blaze.anomaly :as ba]
    [cognitect.anomalies :as anom]
    [java-time.core :as time-core])
  (:import
    [com.google.common.hash PrimitiveSink]
    [java.io Writer]
    [java.nio.charset StandardCharsets]
    [java.time LocalDate LocalDateTime LocalTime OffsetDateTime Year YearMonth ZoneOffset]
    [java.time.format DateTimeFormatter DateTimeParseException]
    [java.time.temporal Temporal TemporalAccessor TemporalField TemporalUnit]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defprotocol SystemType
  (-type [_])
  (-to-string [_])
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
  (-to-string [b]
    (.toString b))
  (-hash-into [b sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 0))
      (.putBoolean b)))
  (-equals [b x]
    (some->> x (.equals b))))


(defn boolean? [x]
  (identical? :system/boolean (-type x)))



;; ---- System.Integer --------------------------------------------------------

(extend-protocol SystemType
  Integer
  (-type [_]
    :system/integer)
  (-to-string [i]
    (.toString i))
  (-hash-into [i sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 2))
      (.putInt i)))
  (-equals [i x]
    (some->> x (.equals i))))


(defn integer? [x]
  (identical? :system/integer (-type x)))



;; ---- System.Long -----------------------------------------------------------

(extend-protocol SystemType
  Long
  (-type [_]
    :system/long)
  (-to-string [i]
    (.toString i))
  (-hash-into [l sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 3))
      (.putInt l)))
  (-equals [l x]
    (some->> x (.equals l))))


(defn long? [x]
  (identical? :system/long (-type x)))



;; ---- System.String ---------------------------------------------------------

(extend-protocol SystemType
  String
  (-type [_]
    :system/string)
  (-to-string [s]
    s)
  (-hash-into [s sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 1))
      (.putString s StandardCharsets/UTF_8)))
  (-equals [s x]
    (some->> x (.equals s))))


(defn string? [x]
  (identical? :system/string (-type x)))



;; ---- System.Decimal --------------------------------------------------------

(extend-protocol SystemType
  BigDecimal
  (-type [_]
    :system/decimal)
  (-to-string [d]
    (.toString d))
  (-hash-into [d sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 4))
      (.putString (str d) StandardCharsets/UTF_8)))
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

(defn date?
  "Returns true if `x` is a System.Date."
  [x]
  (identical? :system/date (-type x)))


(defn date
  "Returns a System.Date"
  ([year]
   (Year/of year))
  ([year month]
   (YearMonth/of (int year) (int month)))
  ([year month day]
   (LocalDate/of (int year) (int month) (int day))))


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

(defn date-time?
  "Returns true if `x` is a System.DateTime."
  [x]
  (identical? :system/date-time (-type x)))


(deftype DateTimeYear [year]
  SystemType
  (-type [_]
    :system/date-time)
  (-to-string [_]
    (str year))
  (-hash-into [_ sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 6))
      (.putInt (.getValue ^Year year))))
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
  (-to-string [_]
    (str year-month))
  (-hash-into [_ sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 6))
      (.putInt (.getYear ^YearMonth year-month))
      (.putInt (.getMonthValue ^YearMonth year-month))))
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
  (-to-string [_]
    (str date))
  (-hash-into [_ sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 6))
      (.putInt (.getYear ^LocalDate date))
      (.putInt (.getMonthValue ^LocalDate date))
      (.putInt (.getDayOfMonth ^LocalDate date))))
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
   (DateTimeYearMonth. (YearMonth/of (int year) (int month))))
  ([year month day]
   (DateTimeYearMonthDay. (LocalDate/of (int year) (int month) (int day))))
  ([year month day hour]
   (LocalDateTime/of (int year) (int month) (int day) (int hour) 0))
  ([year month day hour minute]
   (LocalDateTime/of (int year) (int month) (int day) (int hour) (int minute)))
  ([year month day hour minute second]
   (LocalDateTime/of (int year) (int month) (int day) (int hour) (int minute)
                     (int second)))
  ([year month day hour minute second millis]
   (LocalDateTime/of (int year) (int month) (int day) (int hour) (int minute)
                     (int second) (unchecked-multiply-int (int millis) 1000000)))
  ([year month day hour minute second millis zone-offset]
   (OffsetDateTime/of (int year) (int month) (int day) (int hour) (int minute)
                      (int second) (unchecked-multiply-int (int millis) 1000000)
                      zone-offset)))


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
  (-to-string [date]
    (str date))
  (-hash-into [date sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 5))
      (.putInt (.getValue date))))
  (-equals [date x]
    (cond
      (instance? Year x) (.equals date x)
      (instance? DateTimeYear x) (.equals date (.-year ^DateTimeYear x))))

  YearMonth
  (-type [_]
    :system/date)
  (-to-string [date]
    (str date))
  (-hash-into [date sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 5))
      (.putInt (.getYear date))
      (.putInt (.getMonthValue date))))
  (-equals [date x]
    (cond
      (instance? YearMonth x) (.equals date x)
      (instance? DateTimeYearMonth x)
      (.equals date (.-year_month ^DateTimeYearMonth x))))

  LocalDate
  (-type [_]
    :system/date)
  (-to-string [date]
    (str date))
  (-hash-into [date sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 5))
      (.putInt (.getYear date))
      (.putInt (.getMonthValue date))
      (.putInt (.getDayOfMonth date))))
  (-equals [date x]
    (cond
      (instance? LocalDate x) (.equals date x)
      (instance? DateTimeYearMonthDay x)
      (.equals date (.-date ^DateTimeYearMonthDay x))))

  LocalDateTime
  (-type [_]
    :system/date-time)
  (-to-string [date-time]
    (.format DateTimeFormatter/ISO_LOCAL_DATE_TIME date-time))
  (-hash-into [date-time sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 6))
      (.putInt (.getYear date-time))
      (.putInt (.getMonthValue date-time))
      (.putInt (.getDayOfMonth date-time))
      (.putInt (.getHour date-time))
      (.putInt (.getMinute date-time))
      (.putInt (.getSecond date-time))
      (.putInt (.getNano date-time))))
  (-equals [date-time x]
    (cond
      (instance? LocalDateTime x) (.equals date-time x)))

  OffsetDateTime
  (-type [_]
    :system/date-time)
  (-to-string [date-time]
    (.format DateTimeFormatter/ISO_DATE_TIME date-time))
  (-hash-into [date-time sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 6))
      (.putInt (.getYear date-time))
      (.putInt (.getMonthValue date-time))
      (.putInt (.getDayOfMonth date-time))
      (.putInt (.getHour date-time))
      (.putInt (.getMinute date-time))
      (.putInt (.getSecond date-time))
      (.putInt (.getNano date-time))
      (.putInt (.getTotalSeconds (.getOffset date-time)))))
  (-equals [date-time x]
    (cond
      (instance? OffsetDateTime x) (.equals date-time x))))


(defmethod print-method OffsetDateTime [^OffsetDateTime date-time ^Writer w]
  (.write w (.toString date-time)))


(defn- epoch-seconds ^long [^LocalDateTime date-time]
  (.toEpochSecond (.atOffset date-time (ZoneOffset/UTC))))


(defprotocol LowerBound
  (-lower-bound [date-time]))


(defn date-time-lower-bound [date-time]
  (-lower-bound date-time))


(extend-protocol LowerBound
  Year
  (-lower-bound [year]
    (epoch-seconds (.atStartOfDay (.atDay year 1))))
  DateTimeYear
  (-lower-bound [year]
    (epoch-seconds (.atStartOfDay (.atDay ^Year (.-year year) 1))))
  YearMonth
  (-lower-bound [year-month]
    (epoch-seconds (.atStartOfDay (.atDay year-month 1))))
  DateTimeYearMonth
  (-lower-bound [year-month]
    (epoch-seconds (.atStartOfDay (.atDay ^YearMonth (.-year_month year-month) 1))))
  LocalDate
  (-lower-bound [date]
    (epoch-seconds (.atStartOfDay date)))
  DateTimeYearMonthDay
  (-lower-bound [date]
    (epoch-seconds (.atStartOfDay ^LocalDate (.date date))))
  LocalDateTime
  (-lower-bound [date-time]
    (epoch-seconds date-time))
  OffsetDateTime
  (-lower-bound [date-time]
    (.toEpochSecond date-time)))


(def ^:private lower-bound-seconds
  (-lower-bound (Year/of 1)))


(extend-protocol LowerBound
  nil
  (-lower-bound [_]
    lower-bound-seconds))


(defprotocol UpperBound
  (-upper-bound [date-time]))


(defn date-time-upper-bound [date-time]
  (-upper-bound date-time))


(extend-protocol UpperBound
  Year
  (-upper-bound [year]
    (dec (epoch-seconds (.atStartOfDay (.atDay (.plusYears year 1) 1)))))
  DateTimeYear
  (-upper-bound [year]
    (dec (epoch-seconds (.atStartOfDay (.atDay (.plusYears ^Year (.year year) 1) 1)))))
  YearMonth
  (-upper-bound [year-month]
    (dec (epoch-seconds (.atStartOfDay (.atDay (.plusMonths year-month 1) 1)))))
  DateTimeYearMonth
  (-upper-bound [year-month]
    (dec (epoch-seconds (.atStartOfDay (.atDay (.plusMonths ^YearMonth (.-year_month year-month) 1) 1)))))
  LocalDate
  (-upper-bound [date]
    (dec (epoch-seconds (.atStartOfDay (.plusDays date 1)))))
  DateTimeYearMonthDay
  (-upper-bound [date]
    (dec (epoch-seconds (.atStartOfDay (.plusDays ^LocalDate (.date date) 1)))))
  LocalDateTime
  (-upper-bound [date-time]
    (epoch-seconds date-time))
  OffsetDateTime
  (-upper-bound [date-time]
    (.toEpochSecond date-time)))


(def ^:private upper-bound-seconds
  (-upper-bound (Year/of 9999)))


(extend-protocol UpperBound
  nil
  (-upper-bound [_]
    upper-bound-seconds))



;; ---- System.Time -----------------------------------------------------------

(extend-protocol SystemType
  LocalTime
  (-type [_]
    :system/time)
  (-to-string [time]
    (.format DateTimeFormatter/ISO_LOCAL_TIME time))
  (-hash-into [time sink]
    (doto ^PrimitiveSink sink
      (.putByte (byte 7))
      (.putInt (.getHour time))
      (.putInt (.getMinute time))
      (.putInt (.getSecond time))
      (.putInt (.getNano time))))
  (-equals [time x]
    (some->> x (.equals time))))


(defn time
  "Returns a System.Time"
  ([hour minute]
   (LocalTime/of (int hour) (int minute)))
  ([hour minute second]
   (LocalTime/of (int hour) (int minute) (int second)))
  ([hour minute second millis]
   (LocalTime/of (int hour) (int minute) (int second)
                 (unchecked-multiply-int (int millis) 1000000))))


(defn parse-time* [s]
  (LocalTime/parse s))


(defn- time-string? [s]
  (.matches (re-matcher #"([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?" s)))


(defn parse-time
  "Parses `s` into a System.Time.

  Returns an anomaly if `s` isn't a valid System.Time."
  [s]
  (if (time-string? s)
    (ba/try-one DateTimeParseException ::anom/incorrect (parse-time* s))
    (ba/incorrect (format "Invalid date-time value `%s`." s))))


;; ---- Other -----------------------------------------------------------------

(extend-protocol SystemType
  Object
  (-type [_])
  (-to-string [o]
    (.toString o))
  (-hash-into [_ _])
  (-equals [_ _] false)

  nil
  (-type [_])
  (-to-string [_]
    "nil")
  (-hash-into [_ _])
  (-equals [_ _]))
