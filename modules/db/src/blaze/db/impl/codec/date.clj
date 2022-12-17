(ns blaze.db.impl.codec.date
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.byte-string :as bs]
    [blaze.db.impl.codec :refer [number]]
    [blaze.fhir.spec.type.system])
  (:import
    [blaze.fhir.spec.type.system DateTimeYear DateTimeYearMonth
                                 DateTimeYearMonthDay]
    [java.time LocalDate LocalDateTime OffsetDateTime Year YearMonth
               ZoneOffset]))


(set! *warn-on-reflection* true)


(defn- epoch-seconds ^long [^LocalDateTime date-time]
  (.toEpochSecond (.atOffset date-time (ZoneOffset/UTC))))


(defprotocol LowerBound
  (-encode-lower-bound [date-time]))


(extend-protocol LowerBound
  Year
  (-encode-lower-bound [year]
    (number (epoch-seconds (.atStartOfDay (.atDay year 1)))))
  DateTimeYear
  (-encode-lower-bound [year]
    (number (epoch-seconds (.atStartOfDay (.atDay ^Year (.-year year) 1)))))
  YearMonth
  (-encode-lower-bound [year-month]
    (number (epoch-seconds (.atStartOfDay (.atDay year-month 1)))))
  DateTimeYearMonth
  (-encode-lower-bound [year-month]
    (number (epoch-seconds (.atStartOfDay (.atDay ^YearMonth (.-year_month year-month) 1)))))
  LocalDate
  (-encode-lower-bound [date]
    (number (epoch-seconds (.atStartOfDay date))))
  DateTimeYearMonthDay
  (-encode-lower-bound [date]
    (number (epoch-seconds (.atStartOfDay ^LocalDate (.date date)))))
  LocalDateTime
  (-encode-lower-bound [date-time]
    (number (epoch-seconds date-time)))
  OffsetDateTime
  (-encode-lower-bound [date-time]
    (number (.toEpochSecond date-time))))


(defn encode-lower-bound
  "Encodes the lower bound of the implicit range of `date-time`."
  [date-time]
  (-encode-lower-bound date-time))


(defprotocol UpperBound
  (-encode-upper-bound [date-time]))


(extend-protocol UpperBound
  Year
  (-encode-upper-bound [year]
    (number (dec (epoch-seconds (.atStartOfDay (.atDay (.plusYears year 1) 1))))))
  DateTimeYear
  (-encode-upper-bound [year]
    (number (dec (epoch-seconds (.atStartOfDay (.atDay (.plusYears ^Year (.year year) 1) 1))))))
  YearMonth
  (-encode-upper-bound [year-month]
    (number (dec (epoch-seconds (.atStartOfDay (.atDay (.plusMonths year-month 1) 1))))))
  DateTimeYearMonth
  (-encode-upper-bound [year-month]
    (number (dec (epoch-seconds (.atStartOfDay (.atDay (.plusMonths ^YearMonth (.-year_month year-month) 1) 1))))))
  LocalDate
  (-encode-upper-bound [date]
    (number (dec (epoch-seconds (.atStartOfDay (.plusDays date 1))))))
  DateTimeYearMonthDay
  (-encode-upper-bound [date]
    (number (dec (epoch-seconds (.atStartOfDay (.plusDays ^LocalDate (.date date) 1))))))
  LocalDateTime
  (-encode-upper-bound [date-time]
    (number (epoch-seconds date-time)))
  OffsetDateTime
  (-encode-upper-bound [date-time]
    (number (.toEpochSecond date-time))))


(defn encode-upper-bound
  "Encodes the upper bound of the implicit range of `date-time`."
  [date-time]
  (-encode-upper-bound date-time))


(defn- encode-range* [lower-bound upper-bound]
  (-> (bb/allocate (+ 2 (bs/size lower-bound) (bs/size upper-bound)))
      (bb/put-byte-string! lower-bound)
      (bb/put-byte! 0)
      (bb/put-byte-string! upper-bound)
      (bb/put-byte! (bs/size lower-bound))
      bb/flip!
      bs/from-byte-buffer!))


(defn encode-range
  "Encodes the implicit range of `date-time` or the explicit range from `start`
  to `end`."
  ([date-time]
   (encode-range date-time date-time))
  ([start end]
   (encode-range* (encode-lower-bound (or start (Year/of 1)))
                  (encode-upper-bound (or end (Year/of 9999))))))


(defn lower-bound-bytes
  "Returns the bytes of the lower bound from the encoded `date-range-bytes`."
  [date-range-bytes]
  (let [lower-bound-size-idx (unchecked-dec-int (bs/size date-range-bytes))]
    (bs/subs date-range-bytes 0 (bs/nth date-range-bytes lower-bound-size-idx))))


(defn upper-bound-bytes
  "Returns the bytes of the upper bound from the encoded `date-range-bytes`."
  [date-range-bytes]
  (let [lower-bound-size-idx (unchecked-dec-int (bs/size date-range-bytes))
        start (unchecked-inc-int (int (bs/nth date-range-bytes lower-bound-size-idx)))]
    (bs/subs date-range-bytes start lower-bound-size-idx)))
