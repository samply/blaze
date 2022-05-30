(ns blaze.elm.date-time
  "Implementation of the integer type.

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.anomaly :as ba :refer [throw-anom]]
    [blaze.elm.protocols :as p]
    [blaze.fhir.spec.type]
    [blaze.fhir.spec.type.system :as system]
    [java-time :as time])
  (:import
    [blaze.fhir.spec.type OffsetInstant]
    [blaze.fhir.spec.type.system DateTimeYear DateTimeYearMonth DateTimeYearMonthDay]
    [java.time LocalDate LocalDateTime LocalTime OffsetDateTime Year YearMonth Instant]
    [java.time.temporal ChronoField ChronoUnit Temporal TemporalAccessor]))


(set! *warn-on-reflection* true)


(def min-year (system/date 1))
(def date-time-min-year (system/date-time 1))
(def min-year-month (system/date 1 1))
(def date-time-min-year-month (system/date-time 1 1))
(def min-date (system/date 1 1 1))
(def date-time-min-date (system/date-time 1 1 1))
(def min-date-time (system/date-time 1 1 1 0 0 0 0))


(def max-year (system/date 9999))
(def date-time-max-year (system/date-time 9999))
(def max-year-month (system/date 9999 12))
(def date-time-max-year-month (system/date-time 9999 12))
(def max-date (system/date 9999 12 31))
(def date-time-max-date (system/date-time 9999 12 31))
(def max-date-time (system/date-time 9999 12 31 23 59 59 999))


(defrecord Period [months millis]
  p/Equal
  (equal [this other]
    (some->> other (.equals this)))

  p/Add
  (add [this other]
    (if (instance? Period other)
      (->Period
        (+ months (:months other))
        (+ millis (:millis other)))
      (throw (ex-info (str "Invalid RHS adding to Period. Expected Period but was `" (type other) "`.")
                      {:op :add :this this :other other}))))

  p/Divide
  (divide [_ other]
    (->Period (/ months other) (/ millis other)))

  p/Multiply
  (multiply [_ other]
    (->Period (* months other) (* millis other)))

  p/Negate
  (negate [_]
    (->Period (- months) (- millis)))

  p/Subtract
  (subtract [this other]
    (if (instance? Period other)
      (->Period
        (- months (:months other))
        (- millis (:millis other)))
      (throw (ex-info (str "Invalid RHS subtracting from Period. Expected Period but was `" (type other) "`.")
                      {:op :subtract :this this :other other})))))


(defn period [years months millis]
  (->Period (+ (* years 12) months) millis))


(defprotocol PrecisionNum
  "Returns the precision of a date-time instance."
  (precision-num [this]))


(defn- get-chrono-field [^TemporalAccessor ta ^long precision]
  (.getLong ta (case precision
                 0 ChronoField/YEAR
                 1 ChronoField/MONTH_OF_YEAR
                 2 ChronoField/DAY_OF_MONTH
                 3 ChronoField/HOUR_OF_DAY
                 4 ChronoField/MINUTE_OF_HOUR
                 5 ChronoField/SECOND_OF_MINUTE
                 6 ChronoField/MILLI_OF_SECOND)))


(defn- compare-to-precision
  "Compares two date time values up to the minimum of the specified precisions.

  Returns nil (unknown) if the fields up to the smaller precision are equal but
  one of the precisions is higher."
  ([dt-1 dt-2 p-1 p-2]
   (compare-to-precision dt-1 dt-2 p-1 p-2 0))
  ([dt-1 dt-2 p-1 p-2 p-start]
   (let [min-precision (min p-1 p-2)]
     (loop [precision p-start]
       (let [cmp (- (get-chrono-field dt-1 precision)
                    (get-chrono-field dt-2 precision))]
         (if (zero? cmp)
           (if (< precision min-precision)
             (recur (inc precision))
             (when (= p-1 p-2) 0))
           cmp))))))


(defrecord PrecisionLocalTime [local-time p-num]
  Comparable
  (compareTo [this other]
    (if (instance? PrecisionLocalTime other)
      (if-let [cmp (compare-to-precision (:local-time this) (:local-time other)
                                         (:p-num this) (:p-num other) 3)]
        cmp
        (throw (ClassCastException. "Precisions differ.")))
      (throw (ClassCastException. "Not a PrecisionLocalTime.")))))


(defn local-time
  ([hour]
   (->PrecisionLocalTime (LocalTime/of hour 0) 3))
  ([hour minute]
   (->PrecisionLocalTime (LocalTime/of hour minute) 4))
  ([hour minute second]
   (->PrecisionLocalTime (LocalTime/of hour minute second) 5))
  ([hour minute second milli]
   (->PrecisionLocalTime (LocalTime/of hour minute second (* milli 1000000)) 6)))


(defn local-time? [x]
  (instance? PrecisionLocalTime x))


(defn temporal? [x]
  (or (instance? Temporal x) (local-time? x)))


(def min-time (local-time 0 0 0 0))
(def max-time (local-time 23 59 59 999))


(def ^:private precision->p-num
  {ChronoUnit/YEARS 0
   ChronoUnit/MONTHS 1
   ChronoUnit/WEEKS 2
   ChronoUnit/DAYS 2
   ChronoUnit/HOURS 3
   ChronoUnit/MINUTES 4
   ChronoUnit/SECONDS 5
   ChronoUnit/MILLIS 6})


(def ^:private p-num->precision
  [ChronoUnit/YEARS
   ChronoUnit/MONTHS
   ChronoUnit/DAYS
   ChronoUnit/HOURS
   ChronoUnit/MINUTES
   ChronoUnit/SECONDS
   ChronoUnit/MILLIS])


(extend-protocol PrecisionNum
  Year
  (precision-num [_] 0)
  DateTimeYear
  (precision-num [_] 0)
  YearMonth
  (precision-num [_] 1)
  DateTimeYearMonth
  (precision-num [_] 1)
  LocalDate
  (precision-num [_] 2)
  DateTimeYearMonthDay
  (precision-num [_] 2)
  LocalDateTime
  (precision-num [_] 6))



;; 12. Comparison Operators

;; 12.1. Equal
(extend-protocol p/Equal
  PrecisionLocalTime
  (equal [x y]
    (when (instance? PrecisionLocalTime y)
      (when-let [cmp (compare-to-precision (:local-time x) (:local-time y)
                                           (:p-num x) (:p-num y) 3)]
        (zero? cmp)))))


;; 12.3. Greater
(extend-protocol p/Greater
  Year
  (greater [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 0 (precision-num other))]
        (> cmp 0))))

  DateTimeYear
  (greater [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 0 (precision-num other))]
        (> cmp 0))))

  YearMonth
  (greater [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision-num other))]
        (> cmp 0))))

  DateTimeYearMonth
  (greater [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision-num other))]
        (> cmp 0))))

  LocalDate
  (greater [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision-num other))]
        (> cmp 0))))

  DateTimeYearMonthDay
  (greater [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision-num other))]
        (> cmp 0))))

  LocalDateTime
  (greater [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 6 (precision-num other))]
        (> cmp 0))))

  PrecisionLocalTime
  (greater [this other]
    (when (instance? PrecisionLocalTime other)
      (when-let [cmp (compare-to-precision (:local-time this) (:local-time other)
                                           (:p-num this) (:p-num other) 3)]
        (> cmp 0)))))


;; 12.4. GreaterOrEqual
(extend-protocol p/GreaterOrEqual
  Year
  (greater-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 0 (precision-num other))]
        (>= cmp 0))))

  DateTimeYear
  (greater-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 0 (precision-num other))]
        (>= cmp 0))))

  YearMonth
  (greater-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision-num other))]
        (>= cmp 0))))

  DateTimeYearMonth
  (greater-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision-num other))]
        (>= cmp 0))))

  LocalDate
  (greater-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision-num other))]
        (>= cmp 0))))

  DateTimeYearMonthDay
  (greater-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision-num other))]
        (>= cmp 0))))

  LocalDateTime
  (greater-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 6 (precision-num other))]
        (>= cmp 0))))

  PrecisionLocalTime
  (greater-or-equal [this other]
    (when (instance? PrecisionLocalTime other)
      (when-let [cmp (compare-to-precision (:local-time this) (:local-time other)
                                           (:p-num this) (:p-num other) 3)]
        (>= cmp 0)))))


;; 12.5. Less
(extend-protocol p/Less
  Year
  (less [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 0 (precision-num other))]
        (< cmp 0))))

  DateTimeYear
  (less [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 0 (precision-num other))]
        (< cmp 0))))

  YearMonth
  (less [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision-num other))]
        (< cmp 0))))

  DateTimeYearMonth
  (less [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision-num other))]
        (< cmp 0))))

  LocalDate
  (less [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision-num other))]
        (< cmp 0))))

  DateTimeYearMonthDay
  (less [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision-num other))]
        (< cmp 0))))

  LocalDateTime
  (less [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 6 (precision-num other))]
        (< cmp 0))))

  PrecisionLocalTime
  (less [this other]
    (when (instance? PrecisionLocalTime other)
      (when-let [cmp (compare-to-precision (:local-time this) (:local-time other)
                                           (:p-num this) (:p-num other) 3)]
        (< cmp 0)))))


;; 12.6. LessOrEqual
(extend-protocol p/LessOrEqual
  Year
  (less-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 0 (precision-num other))]
        (<= cmp 0))))

  DateTimeYear
  (less-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 0 (precision-num other))]
        (<= cmp 0))))

  YearMonth
  (less-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision-num other))]
        (<= cmp 0))))

  DateTimeYearMonth
  (less-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision-num other))]
        (<= cmp 0))))

  LocalDate
  (less-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision-num other))]
        (<= cmp 0))))

  DateTimeYearMonthDay
  (less-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision-num other))]
        (<= cmp 0))))

  LocalDateTime
  (less-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 6 (precision-num other))]
        (<= cmp 0))))

  PrecisionLocalTime
  (less-or-equal [this other]
    (when (instance? PrecisionLocalTime other)
      (when-let [cmp (compare-to-precision (:local-time this) (:local-time other)
                                           (:p-num this) (:p-num other) 3)]
        (<= cmp 0)))))



;; 16. Arithmetic Operators

;; 16.2. Add
(extend-protocol p/Add
  Year
  (add [this other]
    (if (instance? Period other)
      (time/plus this (time/years (quot (:months other) 12)))
      (throw (ex-info (str "Invalid RHS adding to Year. Expected Period but was `" (type other) "`.")
                      {:op :add :this this :other other}))))

  DateTimeYear
  (add [this other]
    (if (instance? Period other)
      (time/plus this (time/years (quot (:months other) 12)))
      (throw (ex-info (str "Invalid RHS adding to Year. Expected Period but was `" (type other) "`.")
                      {:op :add :this this :other other}))))

  YearMonth
  (add [this other]
    (if (instance? Period other)
      (time/plus this (time/months (:months other)))
      (throw (ex-info (str "Invalid RHS adding to YearMonth. Expected Period but was `" (type other) "`.")
                      {:op :add :this this :other other}))))

  DateTimeYearMonth
  (add [this other]
    (if (instance? Period other)
      (time/plus this (time/months (:months other)))
      (throw (ex-info (str "Invalid RHS adding to YearMonth. Expected Period but was `" (type other) "`.")
                      {:op :add :this this :other other}))))

  LocalDate
  (add [this other]
    (if (instance? Period other)
      (time/plus this (time/months (:months other)) (time/days (quot (:millis other) 86400000)))
      (throw (ex-info (str "Invalid RHS adding to LocalDate. Expected Period but was `" (type other) "`.")
                      {:op :add :this this :other other}))))

  DateTimeYearMonthDay
  (add [this other]
    (if (instance? Period other)
      (time/plus this (time/months (:months other)) (time/days (quot (:millis other) 86400000)))
      (throw (ex-info (str "Invalid RHS adding to LocalDate. Expected Period but was `" (type other) "`.")
                      {:op :add :this this :other other}))))

  LocalDateTime
  (add [this other]
    (if (instance? Period other)
      (time/plus this (time/months (:months other)) (time/nanos (* (:millis other) 1000000)))
      (throw (ex-info (str "Invalid RHS adding to LocalDateTime. Expected Period but was `" (type other) "`.")
                      {:op :add :this this :other other}))))

  PrecisionLocalTime
  (add [this other]
    (if (instance? Period other)
      (->PrecisionLocalTime (.plusNanos ^LocalTime (:local-time this) (* (:millis other) 1000000)) (:p-num this))
      (throw (ex-info (str "Invalid RHS adding to LocalTime. Expected Period but was `" (type other) "`.")
                      {:op :add :this this :other other})))))


(defn- minimum-value-msg [x]
  (format "Predecessor: argument `%s` is already the minimum value." x))


(defn- minimum-value-anom [x]
  (ba/incorrect (minimum-value-msg x)))


;; 16.18. Predecessor
(extend-protocol p/Predecessor
  Year
  (predecessor [x]
    (if (time/after? x min-year)
      (.minusYears x 1)
      (throw-anom (minimum-value-anom x))))

  DateTimeYear
  (predecessor [x]
    (if (time/after? x date-time-min-year)
      (time/minus x (time/years 1))
      (throw-anom (minimum-value-anom x))))

  YearMonth
  (predecessor [x]
    (if (time/after? x min-year-month)
      (.minusMonths x 1)
      (throw-anom (minimum-value-anom x))))

  DateTimeYearMonth
  (predecessor [x]
    (if (time/after? x date-time-min-year-month)
      (time/minus x (time/months 1))
      (throw-anom (minimum-value-anom x))))

  LocalDate
  (predecessor [x]
    (if (time/after? x min-date)
      (.minusDays x 1)
      (throw-anom (minimum-value-anom x))))

  DateTimeYearMonthDay
  (predecessor [x]
    (if (time/after? x date-time-min-date)
      (time/minus x (time/days 1))
      (throw-anom (minimum-value-anom x))))

  LocalDateTime
  (predecessor [x]
    (if (time/after? x min-date-time)
      (.minusNanos x 1000000)
      (throw-anom (minimum-value-anom x))))

  PrecisionLocalTime
  (predecessor [{:keys [local-time p-num] :as x}]
    (if (p/greater x min-time)
      (->PrecisionLocalTime (.minus ^LocalTime local-time 1 ^ChronoUnit (p-num->precision p-num)) p-num)
      (throw-anom (minimum-value-anom x)))))


;; 16.20. Subtract
(extend-protocol p/Subtract
  Year
  (subtract [this other]
    (if (instance? Period other)
      (let [result (time/minus this (time/years (quot (:months other) 12)))]
        (if (time/before? result min-year)
          (throw (ex-info "Out of range." {:op :subtract :this this :other other}))
          result))
      (throw (ex-info (str "Invalid RHS adding to Year. Expected Period but was `" (type other) "`.")
                      {:op :subtract :this this :other other}))))

  DateTimeYear
  (subtract [this other]
    (if (instance? Period other)
      (let [result (time/minus this (time/years (quot (:months other) 12)))]
        (if (time/before? result date-time-min-year)
          (throw (ex-info "Out of range." {:op :subtract :this this :other other}))
          result))
      (throw (ex-info (str "Invalid RHS adding to Year. Expected Period but was `" (type other) "`.")
                      {:op :subtract :this this :other other}))))

  YearMonth
  (subtract [this other]
    (if (instance? Period other)
      (let [result (time/minus this (time/months (:months other)))]
        (if (time/before? result min-year-month)
          (throw (ex-info "Out of range." {:op :subtract :this this :other other}))
          result))
      (throw (ex-info (str "Invalid RHS adding to YearMonth. Expected Period but was `" (type other) "`.")
                      {:op :subtract :this this :other other}))))

  DateTimeYearMonth
  (subtract [this other]
    (if (instance? Period other)
      (let [result (time/minus this (time/months (:months other)))]
        (if (time/before? result date-time-min-year-month)
          (throw (ex-info "Out of range." {:op :subtract :this this :other other}))
          result))
      (throw (ex-info (str "Invalid RHS adding to YearMonth. Expected Period but was `" (type other) "`.")
                      {:op :subtract :this this :other other}))))

  LocalDate
  (subtract [this other]
    (if (instance? Period other)
      (let [result (time/minus
                     this
                     (time/months (:months other))
                     (time/days (quot (:millis other) 86400000)))]
        (if (time/before? result min-date)
          (throw (ex-info "Out of range." {:op :subtract :this this :other other}))
          result))
      (throw (ex-info (str "Invalid RHS adding to LocalDate. Expected Period but was `" (type other) "`.")
                      {:op :subtract :this this :other other}))))

  DateTimeYearMonthDay
  (subtract [this other]
    (if (instance? Period other)
      (let [result (time/minus
                     this
                     (time/months (:months other))
                     (time/days (quot (:millis other) 86400000)))]
        (if (time/before? result date-time-min-date)
          (throw (ex-info "Out of range." {:op :subtract :this this :other other}))
          result))
      (throw (ex-info (str "Invalid RHS adding to LocalDate. Expected Period but was `" (type other) "`.")
                      {:op :subtract :this this :other other}))))

  LocalDateTime
  (subtract [this other]
    (if (instance? Period other)
      (let [result (-> this
                       (.minusMonths (:months other))
                       (.minusNanos (* (:millis other) 1000000)))]
        (if (>= (.compareTo result min-date-time) 0)
          result
          (throw (ex-info "Out of range." {:op :subtract :this this :other other}))))
      (throw (ex-info (str "Invalid RHS adding to LocalDateTime. Expected Period but was `" (type other) "`.")
                      {:op :subtract :this this :other other}))))

  PrecisionLocalTime
  (subtract [this other]
    (if (instance? Period other)
      ;; TODO: don't wrap
      (->PrecisionLocalTime (.minusNanos ^LocalTime (:local-time this) (* (:millis other) 1000000)) (:p-num this))
      (throw (ex-info (str "Invalid RHS adding to LocalTime. Expected Period but was `" (type other) "`.")
                      {:op :subtract :this this :other other})))))


;; 16.15. Successor
(extend-protocol p/Successor
  Year
  (successor [x]
    (if (time/before? x max-year)
      (.plusYears x 1)
      (throw (ex-info "Successor: argument is already the maximum value."
                      {:x x}))))

  DateTimeYear
  (successor [x]
    (if (time/before? x date-time-max-year)
      (time/plus x (time/years 1))
      (throw (ex-info "Successor: argument is already the maximum value."
                      {:x x}))))

  YearMonth
  (successor [x]
    (if (time/before? x max-year-month)
      (.plusMonths x 1)
      (throw (ex-info "Successor: argument is already the maximum value."
                      {:x x}))))

  DateTimeYearMonth
  (successor [x]
    (if (time/before? x date-time-max-year-month)
      (time/plus x (time/months 1))
      (throw (ex-info "Successor: argument is already the maximum value."
                      {:x x}))))

  LocalDate
  (successor [x]
    (if (time/before? x max-date)
      (.plusDays x 1)
      (throw (ex-info "Successor: argument is already the maximum value."
                      {:x x}))))

  DateTimeYearMonthDay
  (successor [x]
    (if (time/before? x date-time-max-date)
      (time/plus x (time/days 1))
      (throw (ex-info "Successor: argument is already the maximum value."
                      {:x x}))))

  LocalDateTime
  (successor [x]
    (if (time/before? x max-date-time)
      (.plusNanos x 1000000)
      (throw (ex-info "Successor: argument is already the maximum value."
                      {:x x}))))

  PrecisionLocalTime
  (successor [{:keys [local-time p-num] :as x}]
    (if (p/less x max-time)
      (->PrecisionLocalTime (.plus ^LocalTime local-time 1 ^ChronoUnit (p-num->precision p-num)) p-num)
      (throw (ex-info "Successor: argument is already the maximum value."
                      {:x x})))))



;; 18. Date and Time Operators

;; 18.7. DateFrom
(extend-protocol p/DateFrom
  LocalDate
  (date-from [x]
    x)

  LocalDateTime
  (date-from [x]
    (.toLocalDate x)))


;; 18.9. DateTimeComponentFrom
(extend-protocol p/DateTimeComponentFrom
  Year
  (date-time-component-from [x precision]
    (let [req-p-num (precision->p-num precision)]
      (when (<= req-p-num 0)
        (get-chrono-field x req-p-num))))

  DateTimeYear
  (date-time-component-from [x precision]
    (let [req-p-num (precision->p-num precision)]
      (when (<= req-p-num 0)
        (get-chrono-field x req-p-num))))

  YearMonth
  (date-time-component-from [x precision]
    (let [req-p-num (precision->p-num precision)]
      (when (<= req-p-num 1)
        (get-chrono-field x req-p-num))))

  DateTimeYearMonth
  (date-time-component-from [x precision]
    (let [req-p-num (precision->p-num precision)]
      (when (<= req-p-num 1)
        (get-chrono-field x req-p-num))))

  LocalDate
  (date-time-component-from [x precision]
    (let [req-p-num (precision->p-num precision)]
      (when (<= req-p-num 2)
        (get-chrono-field x req-p-num))))

  DateTimeYearMonthDay
  (date-time-component-from [x precision]
    (let [req-p-num (precision->p-num precision)]
      (when (<= req-p-num 2)
        (get-chrono-field x req-p-num))))

  LocalDateTime
  (date-time-component-from [x precision]
    (let [req-p-num (precision->p-num precision)]
      (when (<= req-p-num 6)
        (get-chrono-field x req-p-num))))

  PrecisionLocalTime
  (date-time-component-from [{:keys [local-time p-num]} precision]
    (let [req-p-num (precision->p-num precision)]
      (when (<= req-p-num p-num)
        (get-chrono-field local-time req-p-num)))))


;; 18.10. DifferenceBetween
(extend-protocol p/DifferenceBetween
  Year
  (difference-between [this other precision]
    (when other
      (when (<= (precision->p-num precision) (min 0 (precision-num other)))
        (.until this other precision))))

  DateTimeYear
  (difference-between [this other precision]
    (when other
      (when (<= (precision->p-num precision) (min 0 (precision-num other)))
        (.until this other precision))))

  YearMonth
  (difference-between [this other precision]
    (when other
      (when (<= (precision->p-num precision) (min 1 (precision-num other)))
        (.until this other precision))))

  DateTimeYearMonth
  (difference-between [this other precision]
    (when other
      (when (<= (precision->p-num precision) (min 1 (precision-num other)))
        (.until this other precision))))

  LocalDate
  (difference-between [this other precision]
    (when other
      (when (<= (precision->p-num precision) (min 2 (precision-num other)))
        (.until this other precision))))

  DateTimeYearMonthDay
  (difference-between [this other precision]
    (when other
      (when (<= (precision->p-num precision) (min 2 (precision-num other)))
        (.until this other precision))))

  LocalDateTime
  (difference-between [this other precision]
    (when other
      (when (<= (precision->p-num precision) (min 6 (precision-num other)))
        (let [duration (.until this other precision)]
          (cond
            (pos? duration)
            (inc duration)
            (neg? duration)
            (dec duration)
            :else
            duration)))))

  PrecisionLocalTime
  (difference-between [this other precision]
    (when (instance? PrecisionLocalTime other)
      (when (<= (precision->p-num precision) (min (:p-num this) (:p-num other)))
        (.until ^LocalTime (:local-time this) (:local-time other) precision)))))


;; 18.11. DurationBetween
(extend-protocol p/DurationBetween
  Year
  (duration-between [this other precision]
    (when other
      (when (<= (precision->p-num precision) (min 0 (precision-num other)))
        (.until this other precision))))

  DateTimeYear
  (duration-between [this other precision]
    (when other
      (when (<= (precision->p-num precision) (min 0 (precision-num other)))
        (.until this other precision))))

  YearMonth
  (duration-between [this other precision]
    (when other
      (when (<= (precision->p-num precision) (min 1 (precision-num other)))
        (.until this other precision))))

  DateTimeYearMonth
  (duration-between [this other precision]
    (when other
      (when (<= (precision->p-num precision) (min 1 (precision-num other)))
        (.until this other precision))))

  LocalDate
  (duration-between [this other precision]
    (when other
      (when (<= (precision->p-num precision) (min 2 (precision-num other)))
        (.until this other precision))))

  DateTimeYearMonthDay
  (duration-between [this other precision]
    (when other
      (when (<= (precision->p-num precision) (min 2 (precision-num other)))
        (.until this other precision))))

  LocalDateTime
  (duration-between [this other precision]
    (when other
      (when (<= (precision->p-num precision) (min 6 (precision-num other)))
        (.until this other precision))))

  PrecisionLocalTime
  (duration-between [this other precision]
    (when (instance? PrecisionLocalTime other)
      (when (<= (precision->p-num precision) (min (:p-num this) (:p-num other)))
        (.until ^LocalTime (:local-time this) (:local-time other) precision)))))


;; 18.14. SameAs
(extend-protocol p/SameAs
  Year
  (same-as [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 0 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (= cmp 0)))
        (p/equal this other))))

  DateTimeYear
  (same-as [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 0 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (= cmp 0)))
        (p/equal this other))))

  YearMonth
  (same-as [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 1 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (= cmp 0)))
        (p/equal this other))))

  DateTimeYearMonth
  (same-as [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 1 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (= cmp 0)))
        (p/equal this other))))

  LocalDate
  (same-as [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 2 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (= cmp 0)))
        (p/equal this other))))

  DateTimeYearMonthDay
  (same-as [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 2 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (= cmp 0)))
        (p/equal this other))))

  LocalDateTime
  (same-as [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 6 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (= cmp 0)))
        (p/equal this other))))

  PrecisionLocalTime
  (same-as [this other precision]
    (when (instance? PrecisionLocalTime other)
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min (:p-num this) (:p-num other)))
          (when-let [cmp (compare-to-precision (:local-time this) (:local-time other)
                                               p-num p-num 3)]
            (= cmp 0)))
        (p/equal this other)))))


;; 18.15. SameOrBefore
(extend-protocol p/SameOrBefore
  Year
  (same-or-before [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 0 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (<= cmp 0)))
        (p/less-or-equal this other))))

  DateTimeYear
  (same-or-before [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 0 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (<= cmp 0)))
        (p/less-or-equal this other))))

  YearMonth
  (same-or-before [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 1 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (<= cmp 0)))
        (p/less-or-equal this other))))

  DateTimeYearMonth
  (same-or-before [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 1 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (<= cmp 0)))
        (p/less-or-equal this other))))

  LocalDate
  (same-or-before [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 2 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (<= cmp 0)))
        (p/less-or-equal this other))))

  DateTimeYearMonthDay
  (same-or-before [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 2 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (<= cmp 0)))
        (p/less-or-equal this other))))

  LocalDateTime
  (same-or-before [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 6 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (<= cmp 0)))
        (p/less-or-equal this other))))

  PrecisionLocalTime
  (same-or-before [this other precision]
    (when (instance? PrecisionLocalTime other)
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min (:p-num this) (:p-num other)))
          (when-let [cmp (compare-to-precision (:local-time this) (:local-time other)
                                               p-num p-num 3)]
            (<= cmp 0)))
        (p/less-or-equal this other)))))


;; 18.16. SameOrAfter
(extend-protocol p/SameOrAfter
  Year
  (same-or-after [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 0 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (>= cmp 0)))
        (p/greater-or-equal this other))))

  DateTimeYear
  (same-or-after [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 0 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (>= cmp 0)))
        (p/greater-or-equal this other))))

  YearMonth
  (same-or-after [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 1 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (>= cmp 0)))
        (p/greater-or-equal this other))))

  DateTimeYearMonth
  (same-or-after [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 1 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (>= cmp 0)))
        (p/greater-or-equal this other))))

  LocalDate
  (same-or-after [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 2 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (>= cmp 0)))
        (p/greater-or-equal this other))))

  DateTimeYearMonthDay
  (same-or-after [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 2 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (>= cmp 0)))
        (p/greater-or-equal this other))))

  LocalDateTime
  (same-or-after [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 6 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (>= cmp 0)))
        (p/greater-or-equal this other))))

  PrecisionLocalTime
  (same-or-after [this other precision]
    (when (instance? PrecisionLocalTime other)
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min (:p-num this) (:p-num other)))
          (when-let [cmp (compare-to-precision (:local-time this) (:local-time other)
                                               p-num p-num 3)]
            (>= cmp 0)))
        (p/greater-or-equal this other)))))


;; 19.2. After
(extend-protocol p/After
  Year
  (after [this other precision]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 0 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (> cmp 0))
          (p/greater this other)))))

  DateTimeYear
  (after [this other precision]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 0 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (> cmp 0))
          (p/greater this other)))))

  YearMonth
  (after [this other precision]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 1 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (> cmp 0))
          (p/greater this other)))))

  DateTimeYearMonth
  (after [this other precision]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 1 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (> cmp 0))
          (p/greater this other)))))

  LocalDate
  (after [this other precision]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 2 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (> cmp 0))
          (p/greater this other)))))

  DateTimeYearMonthDay
  (after [this other precision]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 2 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (> cmp 0))
          (p/greater this other)))))

  LocalDateTime
  (after [this other precision]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 6 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (> cmp 0))
          (p/greater this other)))))

  PrecisionLocalTime
  (after [this other precision]
    (when (instance? PrecisionLocalTime other)
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min (:p-num this) (:p-num other))))
          (when-let [cmp (compare-to-precision (:local-time this) (:local-time other)
                                               p-num p-num 3)]
            (> cmp 0))
          (p/greater this other))))))


;; 19.3. Before
(extend-protocol p/Before
  Year
  (before [this other precision]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 0 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (< cmp 0))
          (p/less this other)))))

  DateTimeYear
  (before [this other precision]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 0 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (< cmp 0))
          (p/less this other)))))

  YearMonth
  (before [this other precision]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 1 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (< cmp 0))
          (p/less this other)))))

  DateTimeYearMonth
  (before [this other precision]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 1 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (< cmp 0))
          (p/less this other)))))

  LocalDate
  (before [this other precision]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 2 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (< cmp 0))
          (p/less this other)))))

  DateTimeYearMonthDay
  (before [this other precision]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 2 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (< cmp 0))
          (p/less this other)))))

  LocalDateTime
  (before [this other precision]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 6 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (< cmp 0))
          (p/less this other)))))

  PrecisionLocalTime
  (before [this other precision]
    (when (instance? PrecisionLocalTime other)
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min (:p-num this) (:p-num other))))
          (when-let [cmp (compare-to-precision (:local-time this) (:local-time other)
                                               p-num p-num 3)]
            (< cmp 0))
          (p/less this other))))))



;; 22. Type Operators

;; 22.22. ToDate
(extend-protocol p/ToDate
  nil
  (to-date [_ _])

  String
  (to-date [s _]
    (-> (system/parse-date s)
        (ba/exceptionally (constantly nil))))

  Year
  (to-date [this _]
    this)

  DateTimeYear
  (to-date [this _]
    (.-year this))

  YearMonth
  (to-date [this _]
    this)

  DateTimeYearMonth
  (to-date [this _]
    (.-year_month this))

  LocalDate
  (to-date [this _]
    this)

  DateTimeYearMonthDay
  (to-date [this _]
    (.-date this))

  LocalDateTime
  (to-date [this _]
    (.toLocalDate this))

  OffsetDateTime
  (to-date [this now]
    (-> (.withOffsetSameInstant this (.getOffset ^OffsetDateTime now))
        (.toLocalDate))))


;; 22.23. ToDateTime
(extend-protocol p/ToDateTime
  nil
  (to-date-time [_ _])

  Instant
  (to-date-time [this now]
    (-> (.atOffset this (.getOffset ^OffsetDateTime now))
        (.toLocalDateTime)))

  OffsetInstant
  (to-date-time [this now]
    (p/to-date-time (.value this) now))

  Year
  (to-date-time [this _]
    (DateTimeYear. this))

  DateTimeYear
  (to-date-time [this _]
    this)

  YearMonth
  (to-date-time [this _]
    (DateTimeYearMonth. this))

  DateTimeYearMonth
  (to-date-time [this _]
    this)

  LocalDate
  (to-date-time [this _]
    (DateTimeYearMonthDay. this))

  DateTimeYearMonthDay
  (to-date-time [this _]
    this)

  LocalDateTime
  (to-date-time [this _]
    this)

  OffsetDateTime
  (to-date-time [this now]
    (-> (.withOffsetSameInstant this (.getOffset ^OffsetDateTime now))
        (.toLocalDateTime)))

  String
  (to-date-time [s now]
    (p/to-date-time (system/parse-date-time s) now)))


;; 22.30. ToString
(extend-protocol p/ToString
  PrecisionLocalTime
  (to-string [{:keys [local-time]}]
    (str local-time))

  Year
  (to-string [x]
    (str x))

  DateTimeYear
  (to-string [x]
    (str x))

  YearMonth
  (to-string [x]
    (str x))

  DateTimeYearMonth
  (to-string [x]
    (str x))

  LocalDate
  (to-string [x]
    (str x))

  DateTimeYearMonthDay
  (to-string [x]
    (str x))

  LocalDateTime
  (to-string [x]
    (str x)))
