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
  (:refer-clojure :exclude [boolean? decimal? integer? str string? time type])
  (:require
   [blaze.anomaly :as ba]
   [blaze.util :refer [str]]
   [cognitect.anomalies :as anom])
  (:import
   [blaze.fhir.spec.type.system
    Date DateDate DateTime DateTimeDate DateTimeYear DateTimeYearMonth DateYear
    DateYearMonth]
   [com.google.common.hash PrimitiveSink]
   [java.io Writer]
   [java.nio.charset StandardCharsets]
   [java.time DateTimeException LocalDateTime LocalTime OffsetDateTime ZoneOffset]
   [java.time.format DateTimeFormatter DateTimeParseException]
   [java.time.temporal ChronoField]))

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
   (DateYear/of year))
  ([year month]
   (DateYearMonth/of year month))
  ([year month day]
   (DateDate/of year month day)))

(defn parse-date
  "Parses `s` into a System.Date.

  Returns an anomaly if `s` isn't a valid System.Date."
  [s]
  (if (nil? s)
    (ba/incorrect "nil date value")
    (ba/try-one DateTimeException ::anom/incorrect (Date/parse s))))

(extend-protocol SystemType
  DateYear
  (-type [date]
    (.type date))
  (-to-string [date]
    (.toString date))
  (-hash-into [date sink]
    (.hashInto date sink))
  (-equals [date x]
    (cond
      (instance? DateYear x) (.equals date x)
      (instance? DateTimeYear x) (.equals date (.toDate ^DateTimeYear x))))

  DateYearMonth
  (-type [date]
    (.type date))
  (-to-string [date]
    (.toString date))
  (-hash-into [date sink]
    (.hashInto date sink))
  (-equals [date x]
    (cond
      (instance? DateYearMonth x) (.equals date x)
      (instance? DateTimeYearMonth x) (.equals date (.toDate ^DateTimeYearMonth x))))

  DateDate
  (-type [date]
    (.type date))
  (-to-string [date]
    (.toString date))
  (-hash-into [date sink]
    (.hashInto date sink))
  (-equals [date x]
    (cond
      (instance? DateDate x) (.equals date x)
      (instance? DateTimeDate x) (.equals date (.toDate ^DateTimeDate x)))))

(defmethod print-method DateYear [^DateYear date ^Writer w]
  (.write w "#system/date\"")
  (.write w (str date))
  (.write w "\""))

(defmethod print-dup DateYear [^DateYear date ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.system.DateYear/of ")
  (.write w (str date))
  (.write w ")"))

(defmethod print-method DateYearMonth [^DateYearMonth date ^Writer w]
  (.write w "#system/date\"")
  (.write w (str date))
  (.write w "\""))

(defmethod print-dup DateYearMonth [^DateYearMonth date ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.system.DateYearMonth/of ")
  (.write w (str (.year date)))
  (.write w " ")
  (.write w (str (.month date)))
  (.write w ")"))

(defmethod print-method DateDate [^DateDate date ^Writer w]
  (.write w "#system/date\"")
  (.write w (str date))
  (.write w "\""))

(defmethod print-dup DateDate [^DateDate date ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.system.DateDate/of ")
  (.write w (str (.year date)))
  (.write w " ")
  (.write w (str (.month date)))
  (.write w " ")
  (.write w (str (.day date)))
  (.write w ")"))

;; ---- System.DateTime -------------------------------------------------------

(defn date-time?
  "Returns true if `x` is a System.DateTime."
  [x]
  (identical? :system/date-time (-type x)))

(defn date-time
  ([year]
   (DateTimeYear/of year))
  ([year month]
   (DateTimeYearMonth/of year month))
  ([year month day]
   (DateTimeDate/of year month day))
  ([year month day hour]
   (LocalDateTime/of (int year) (int month) (int day) (int hour) 0))
  ([year month day hour minute]
   (LocalDateTime/of (int year) (int month) (int day) (int hour) (int minute)))
  ([year month day hour minute second]
   (LocalDateTime/of (int year) (int month) (int day) (int hour) (int minute)
                     (int second)))
  ([year month day hour minute second millis]
   (.checkValidValue DateTime/YEAR_RANGE year ChronoField/YEAR);
   (LocalDateTime/of (int year) (int month) (int day) (int hour) (int minute)
                     (int second) (unchecked-multiply-int (int millis) 1000000)))
  ([year month day hour minute second millis zone-offset]
   (.checkValidValue DateTime/YEAR_RANGE year ChronoField/YEAR);
   (OffsetDateTime/of (int year) (int month) (int day) (int hour) (int minute)
                      (int second) (unchecked-multiply-int (int millis) 1000000)
                      zone-offset)))

(defn parse-date-time
  "Parses `s` into a System.DateTime.

  Returns an anomaly if `s` isn't a valid System.DateTime."
  [s]
  (if (nil? s)
    (ba/incorrect "nil date-time value")
    (ba/try-one DateTimeException ::anom/incorrect (DateTime/parse s))))

(defmethod print-method DateTimeYear [^DateTimeYear date-time ^Writer w]
  (.write w "#system/date-time\"")
  (.write w (str date-time))
  (.write w "\""))

(defmethod print-dup DateTimeYear [^DateTimeYear date-time ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.system.DateTimeYear/of ")
  (.write w (str date-time))
  (.write w ")"))

(defmethod print-method DateTimeYearMonth [^DateTimeYearMonth date-time ^Writer w]
  (.write w "#system/date-time\"")
  (.write w (str date-time))
  (.write w "\""))

(defmethod print-dup DateTimeYearMonth [^DateTimeYearMonth date-time ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.system.DateTimeYearMonth/of ")
  (.write w (str (.year date-time)))
  (.write w " ")
  (.write w (str (.month date-time)))
  (.write w ")"))

(defmethod print-method DateTimeDate [^DateTimeDate date-time ^Writer w]
  (.write w "#system/date-time\"")
  (.write w (str date-time))
  (.write w "\""))

(defmethod print-dup DateTimeDate [^DateTimeDate date-time ^Writer w]
  (.write w "#=(blaze.fhir.spec.type.system.DateTimeDate/of ")
  (.write w (str (.year date-time)))
  (.write w " ")
  (.write w (str (.month date-time)))
  (.write w " ")
  (.write w (str (.day date-time)))
  (.write w ")"))

(defmethod print-method LocalDateTime [^LocalDateTime dateTime ^Writer w]
  (.write w "#system/date-time\"")
  (.write w (str dateTime))
  (.write w "\""))

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

(defmethod print-method OffsetDateTime [^OffsetDateTime dateTime ^Writer w]
  (.write w "#system/date-time\"")
  (.write w (str dateTime))
  (.write w "\""))

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

(extend-protocol SystemType
  DateTimeYear
  (-type [date-time]
    (.type date-time))
  (-to-string [date-time]
    (.toString date-time))
  (-hash-into [date-time sink]
    (.hashInto date-time sink))
  (-equals [date-time x]
    (cond
      (instance? DateTimeYear x) (.equals date-time x)
      (instance? DateYear x) (.equals date-time (.toDateTime ^DateYear x))))

  DateTimeYearMonth
  (-type [date-time]
    (.type date-time))
  (-to-string [date-time]
    (.toString date-time))
  (-hash-into [date-time sink]
    (.hashInto date-time sink))
  (-equals [date-time x]
    (cond
      (instance? DateTimeYearMonth x) (.equals date-time x)
      (instance? DateYearMonth x) (.equals date-time (.toDateTime ^DateYearMonth x))))

  DateTimeDate
  (-type [date-time]
    (.type date-time))
  (-to-string [date-time]
    (.toString date-time))
  (-hash-into [date-time sink]
    (.hashInto date-time sink))
  (-equals [date-time x]
    (cond
      (instance? DateTimeDate x) (.equals date-time x)
      (instance? DateDate x) (.equals date-time (.toDateTime ^DateDate x))))

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

(defprotocol LowerBound
  (-lower-bound [date-time]))

(defn date-time-lower-bound
  "Returns the lower bound of `date-time` as seconds since epoch.

  In case of local dates the UTC time zone is used."
  [date-time]
  (-lower-bound date-time))

(extend-protocol LowerBound
  DateYear
  (-lower-bound [date]
    (-lower-bound (.atStartOfYear date)))
  DateTimeYear
  (-lower-bound [date]
    (-lower-bound (.atStartOfYear date)))
  DateYearMonth
  (-lower-bound [date]
    (-lower-bound (.atStartOfMonth date)))
  DateTimeYearMonth
  (-lower-bound [date]
    (-lower-bound (.atStartOfMonth date)))
  DateDate
  (-lower-bound [date]
    (-lower-bound (.atStartOfDay date)))
  DateTimeDate
  (-lower-bound [date]
    (-lower-bound (.atStartOfDay date)))
  LocalDateTime
  (-lower-bound [date-time]
    (-lower-bound (.atOffset date-time ZoneOffset/UTC)))
  OffsetDateTime
  (-lower-bound [date-time]
    (.toEpochSecond date-time)))

(def ^:private lower-bound-seconds
  (-lower-bound (DateYear/of 1)))

(extend-protocol LowerBound
  nil
  (-lower-bound [_]
    lower-bound-seconds))

(defprotocol UpperBound
  (-upper-bound [date-time]))

(defn date-time-upper-bound
  "Returns the upper bound of `date-time` as seconds since epoch.

  In case of local dates the UTC time zone is used."
  [date-time]
  (-upper-bound date-time))

(extend-protocol UpperBound
  DateYear
  (-upper-bound [year]
    (-upper-bound (.atEndOfYear year)))
  DateTimeYear
  (-upper-bound [year]
    (-upper-bound (.atEndOfYear year)))
  DateYearMonth
  (-upper-bound [year-month]
    (-upper-bound (.atEndOfMonth year-month)))
  DateTimeYearMonth
  (-upper-bound [year-month]
    (-upper-bound (.atEndOfMonth year-month)))
  DateDate
  (-upper-bound [date]
    (-upper-bound (.atTime date 23 59 59)))
  DateTimeDate
  (-upper-bound [date]
    (-upper-bound (.atTime date 23 59 59)))
  LocalDateTime
  (-upper-bound [date-time]
    (-upper-bound (.atOffset date-time ZoneOffset/UTC)))
  OffsetDateTime
  (-upper-bound [date-time]
    (.toEpochSecond date-time)))

(def ^:private upper-bound-seconds
  (-upper-bound (DateYear/of 9999)))

(extend-protocol UpperBound
  nil
  (-upper-bound [_]
    upper-bound-seconds))

;; ---- System.Time -----------------------------------------------------------

(defn time?
  "Returns true if `x` is a System.Time."
  [x]
  (identical? :system/time (-type x)))

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

(defn parse-time
  "Parses `s` into a System.Time.

  Returns an anomaly if `s` isn't a valid System.Time."
  [s]
  (if (nil? s)
    (ba/incorrect "nil time value")
    (ba/try-one DateTimeParseException ::anom/incorrect (LocalTime/parse s))))

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
