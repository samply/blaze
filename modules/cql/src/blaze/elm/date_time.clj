(ns blaze.elm.date-time
  "Implementation of the integer type.

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:refer-clojure :exclude [str])
  (:require
   [blaze.elm.protocols :as p]
   [blaze.fhir.spec.type]
   [blaze.fhir.spec.type.system :as system]
   [blaze.time :as bt]
   [blaze.util :refer [str]]
   [java-time.api :as time])
  (:import
   [blaze.fhir.spec.type.system
    Date DateDate DateTime DateTimeDate DateTimeYear DateTimeYearMonth DateYear
    DateYearMonth]
   [java.time DateTimeException LocalDateTime LocalTime OffsetDateTime]
   [java.time.temporal ChronoField ChronoUnit Temporal TemporalAccessor]))

(set! *warn-on-reflection* true)

(def min-date (system/date 1 1 1))
(def min-date-time (system/date-time 1 1 1 0 0 0 0))
(def max-date (system/date 9999 12 31))
(def max-date-time (system/date-time 9999 12 31 23 59 59 999))

(defrecord Period [months millis]
  p/Equal
  (equal [this other _]
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
                      {:op :subtract :this this :other other}))))

  Object
  (toString [_]
    (format "Period[month = %d, millis = %d]" months millis)))

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

(defn- compare-fields
  "Compares the fields of two date time values starting at `p-start` up to the
  minimum of the specified precisions.

  Returns nil (unknown) if the fields up to the smaller precision are equal but
  one of the precisions is higher."
  [dt-1 dt-2 p-1 p-2 p-start]
  (let [min-precision (min p-1 p-2)]
    (loop [precision p-start]
      (let [cmp (- (get-chrono-field dt-1 precision)
                   (get-chrono-field dt-2 precision))]
        (if (zero? cmp)
          (if (< precision min-precision)
            (recur (inc precision))
            (when (= p-1 p-2) 0))
          cmp)))))

(defn- same-offset? [^OffsetDateTime x y]
  (and (instance? OffsetDateTime y)
       (.equals (.getOffset x) (.getOffset ^OffsetDateTime y))))

(defn- local
  "Returns `x` as local date-time to compare it with `other` at the precision
  `p-num` relative to the evaluation request at `now`. Returns `x` unchanged if
  it is no DateTime value with offset.

  DateTime values without offset have the offset of the evaluation request.
  DateTime values with offset are taken as written at day precision or coarser
  and if `other` has the same offset. Otherwise they are normalized to the
  offset of the evaluation request."
  [x other ^long p-num now]
  (if (instance? OffsetDateTime x)
    (let [x ^OffsetDateTime x]
      (if (or (< p-num 3) (same-offset? x other))
        (.toLocalDateTime x)
        (.toLocalDateTime (.withOffsetSameInstant x (.getOffset ^OffsetDateTime now)))))
    x))

(defn- compare-to-precision
  "Compares two date time values up to the minimum of the specified precisions
  relative to the evaluation request at `now`.

  Returns nil (unknown) if the fields up to the smaller precision are equal but
  one of the precisions is higher."
  [dt-1 dt-2 p-1 p-2 now]
  (let [p-num (min p-1 p-2)]
    (compare-fields (local dt-1 dt-2 p-num now) (local dt-2 dt-1 p-num now)
                    p-1 p-2 0)))

(defrecord PrecisionLocalTime [local-time p-num]
  Comparable
  (compareTo [this other]
    (if (instance? PrecisionLocalTime other)
      (if-let [cmp (compare-fields (:local-time this) (:local-time other)
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
  DateYear
  (precision-num [_] 0)
  DateTimeYear
  (precision-num [_] 0)
  DateYearMonth
  (precision-num [_] 1)
  DateTimeYearMonth
  (precision-num [_] 1)
  DateDate
  (precision-num [_] 2)
  DateTimeDate
  (precision-num [_] 2)
  LocalDateTime
  (precision-num [_] 6)
  OffsetDateTime
  (precision-num [_] 6))

(defn- comparison-p-num
  "Returns the precision number of comparisons with `other` at the optional
  `precision`."
  ^long [other precision]
  (cond-> (if other (long (precision-num other)) 6)
    precision (min (long (precision->p-num precision)))))

(defn- local-op
  "Applies `op` to the local date-times of `x` and `y` at the precision of their
  comparison."
  ([op x y now]
   (let [p-num (comparison-p-num y nil)]
     (op (local x y p-num now) (local y x p-num now) now)))
  ([op x y precision now]
   (local-op op x y precision (comparison-p-num y precision) now))
  ([op x y precision p-num now]
   (op (local x y p-num now) (local y x p-num now) precision now)))

(defn- with-offset-of
  "Returns `local-date-time` with the offset of `date-time` or nil if
  `local-date-time` is nil."
  [^OffsetDateTime date-time local-date-time]
  (some-> ^LocalDateTime local-date-time (.atOffset (.getOffset date-time))))

;; 12. Comparison Operators

;; 12.1. Equal
(extend-protocol p/Equal
  LocalDateTime
  (equal [x y now]
    (system/equals x (local y x 6 now)))

  OffsetDateTime
  (equal [x y now]
    (p/equal (local x y 6 now) (local y x 6 now) now))

  PrecisionLocalTime
  (equal [x y _]
    (when (instance? PrecisionLocalTime y)
      (when-let [cmp (compare-fields (:local-time x) (:local-time y)
                                     (:p-num x) (:p-num y) 3)]
        (zero? cmp)))))

;; 12.2. Equivalent
(extend-protocol p/Equivalent
  LocalDateTime
  (equivalent [x y now]
    (= x (local y x 6 now)))

  OffsetDateTime
  (equivalent [x y now]
    (p/equivalent (local x y 6 now) (local y x 6 now) now)))

;; 12.3. Greater
(extend-protocol p/Greater
  DateYear
  (greater [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 0 (precision-num other) now)]
        (> cmp 0))))

  DateTimeYear
  (greater [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 0 (precision-num other) now)]
        (> cmp 0))))

  DateYearMonth
  (greater [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision-num other) now)]
        (> cmp 0))))

  DateTimeYearMonth
  (greater [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision-num other) now)]
        (> cmp 0))))

  DateDate
  (greater [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision-num other) now)]
        (> cmp 0))))

  DateTimeDate
  (greater [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision-num other) now)]
        (> cmp 0))))

  LocalDateTime
  (greater [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 6 (precision-num other) now)]
        (> cmp 0))))

  OffsetDateTime
  (greater [x y now]
    (local-op p/greater x y now))

  PrecisionLocalTime
  (greater [this other _]
    (when (instance? PrecisionLocalTime other)
      (when-let [cmp (compare-fields (:local-time this) (:local-time other)
                                     (:p-num this) (:p-num other) 3)]
        (> cmp 0)))))

;; 12.4. GreaterOrEqual
(extend-protocol p/GreaterOrEqual
  DateYear
  (greater-or-equal [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 0 (precision-num other) now)]
        (>= cmp 0))))

  DateTimeYear
  (greater-or-equal [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 0 (precision-num other) now)]
        (>= cmp 0))))

  DateYearMonth
  (greater-or-equal [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision-num other) now)]
        (>= cmp 0))))

  DateTimeYearMonth
  (greater-or-equal [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision-num other) now)]
        (>= cmp 0))))

  DateDate
  (greater-or-equal [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision-num other) now)]
        (>= cmp 0))))

  DateTimeDate
  (greater-or-equal [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision-num other) now)]
        (>= cmp 0))))

  LocalDateTime
  (greater-or-equal [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 6 (precision-num other) now)]
        (>= cmp 0))))

  OffsetDateTime
  (greater-or-equal [x y now]
    (local-op p/greater-or-equal x y now))

  PrecisionLocalTime
  (greater-or-equal [this other _]
    (when (instance? PrecisionLocalTime other)
      (when-let [cmp (compare-fields (:local-time this) (:local-time other)
                                     (:p-num this) (:p-num other) 3)]
        (>= cmp 0)))))

;; 12.5. Less
(extend-protocol p/Less
  DateYear
  (less [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 0 (precision-num other) now)]
        (< cmp 0))))

  DateTimeYear
  (less [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 0 (precision-num other) now)]
        (< cmp 0))))

  DateYearMonth
  (less [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision-num other) now)]
        (< cmp 0))))

  DateTimeYearMonth
  (less [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision-num other) now)]
        (< cmp 0))))

  DateDate
  (less [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision-num other) now)]
        (< cmp 0))))

  DateTimeDate
  (less [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision-num other) now)]
        (< cmp 0))))

  LocalDateTime
  (less [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 6 (precision-num other) now)]
        (< cmp 0))))

  OffsetDateTime
  (less [x y now]
    (local-op p/less x y now))

  PrecisionLocalTime
  (less [this other _]
    (when (instance? PrecisionLocalTime other)
      (when-let [cmp (compare-fields (:local-time this) (:local-time other)
                                     (:p-num this) (:p-num other) 3)]
        (< cmp 0)))))

;; 12.6. LessOrEqual
(extend-protocol p/LessOrEqual
  DateYear
  (less-or-equal [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 0 (precision-num other) now)]
        (<= cmp 0))))

  DateTimeYear
  (less-or-equal [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 0 (precision-num other) now)]
        (<= cmp 0))))

  DateYearMonth
  (less-or-equal [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision-num other) now)]
        (<= cmp 0))))

  DateTimeYearMonth
  (less-or-equal [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision-num other) now)]
        (<= cmp 0))))

  DateDate
  (less-or-equal [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision-num other) now)]
        (<= cmp 0))))

  DateTimeDate
  (less-or-equal [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision-num other) now)]
        (<= cmp 0))))

  LocalDateTime
  (less-or-equal [this other now]
    (when other
      (when-let [cmp (compare-to-precision this other 6 (precision-num other) now)]
        (<= cmp 0))))

  OffsetDateTime
  (less-or-equal [x y now]
    (local-op p/less-or-equal x y now))

  PrecisionLocalTime
  (less-or-equal [this other _]
    (when (instance? PrecisionLocalTime other)
      (when-let [cmp (compare-fields (:local-time this) (:local-time other)
                                     (:p-num this) (:p-num other) 3)]
        (<= cmp 0)))))

;; 16. Arithmetic Operators

(defmacro catch-date-time-error [& body]
  `(try
     ~@body
     (catch DateTimeException ~'_)))

;; 16.2. Add
(extend-protocol p/Add
  DateYear
  (add [this other]
    (if (instance? Period other)
      (catch-date-time-error (.plusYears this (quot (:months other) 12)))
      (throw (ex-info (str "Invalid RHS adding to DateYear. Expected Period but was `" (type other) "`.")
                      {:op :add :this this :other other}))))

  DateTimeYear
  (add [this other]
    (if (instance? Period other)
      (catch-date-time-error (.plusYears this (quot (:months other) 12)))
      (throw (ex-info (str "Invalid RHS adding to DateYear. Expected Period but was `" (type other) "`.")
                      {:op :add :this this :other other}))))

  DateYearMonth
  (add [this other]
    (if (instance? Period other)
      (catch-date-time-error (.plusMonths this (:months other)))
      (throw (ex-info (str "Invalid RHS adding to DateYearMonth. Expected Period but was `" (type other) "`.")
                      {:op :add :this this :other other}))))

  DateTimeYearMonth
  (add [this other]
    (if (instance? Period other)
      (catch-date-time-error (.plusMonths this (:months other)))
      (throw (ex-info (str "Invalid RHS adding to DateYearMonth. Expected Period but was `" (type other) "`.")
                      {:op :add :this this :other other}))))

  DateDate
  (add [this other]
    (if (instance? Period other)
      (catch-date-time-error
       (-> (bt/plus-unit this (:months other) ChronoUnit/MONTHS)
           (bt/plus-unit (quot (:millis other) 86400000) ChronoUnit/DAYS)))
      (throw (ex-info (str "Invalid RHS adding to DateDate. Expected Period but was `" (type other) "`.")
                      {:op :add :this this :other other}))))

  DateTimeDate
  (add [this other]
    (if (instance? Period other)
      (catch-date-time-error
       (-> (bt/plus-unit this (:months other) ChronoUnit/MONTHS)
           (bt/plus-unit (quot (:millis other) 86400000) ChronoUnit/DAYS)))
      (throw (ex-info (str "Invalid RHS adding to DateDate. Expected Period but was `" (type other) "`.")
                      {:op :add :this this :other other}))))

  LocalDateTime
  (add [this other]
    (if (instance? Period other)
      (-> (bt/plus-unit this (:months other) ChronoUnit/MONTHS)
          (bt/plus-unit (* (:millis other) 1000000) ChronoUnit/NANOS))
      (throw (ex-info (str "Invalid RHS adding to LocalDateTime. Expected Period but was `" (type other) "`.")
                      {:op :add :this this :other other}))))

  OffsetDateTime
  (add [x y]
    (with-offset-of x (p/add (.toLocalDateTime x) y)))

  PrecisionLocalTime
  (add [this other]
    (if (instance? Period other)
      (->PrecisionLocalTime (.plusNanos ^LocalTime (:local-time this) (* (:millis other) 1000000)) (:p-num this))
      (throw (ex-info (str "Invalid RHS adding to LocalTime. Expected Period but was `" (type other) "`.")
                      {:op :add :this this :other other})))))

;; 16.18. Predecessor
(extend-protocol p/Predecessor
  DateYear
  (predecessor [x]
    (catch-date-time-error (.plusYears x -1)))

  DateTimeYear
  (predecessor [x]
    (catch-date-time-error (.plusYears x -1)))

  DateYearMonth
  (predecessor [x]
    (catch-date-time-error (.plusMonths x -1)))

  DateTimeYearMonth
  (predecessor [x]
    (catch-date-time-error (.plusMonths x -1)))

  DateDate
  (predecessor [x]
    (catch-date-time-error (.plusDays x -1)))

  DateTimeDate
  (predecessor [x]
    (catch-date-time-error (.plusDays x -1)))

  LocalDateTime
  (predecessor [x]
    (when (time/after? x min-date-time)
      (.minusNanos x 1000000)))

  OffsetDateTime
  (predecessor [x]
    (with-offset-of x (p/predecessor (.toLocalDateTime x))))

  PrecisionLocalTime
  (predecessor [{:keys [local-time p-num] :as x}]
    (when (p/greater x min-time nil)
      (->PrecisionLocalTime (.minus ^LocalTime local-time 1 ^ChronoUnit (p-num->precision p-num)) p-num))))

;; 16.20. Subtract
(extend-protocol p/Subtract
  DateYear
  (subtract [this other]
    (if (instance? Period other)
      (catch-date-time-error (.plusYears this (- (quot (:months other) 12))))
      (throw (ex-info (str "Invalid RHS adding to DateYear. Expected Period but was `" (type other) "`.")
                      {:op :subtract :this this :other other}))))

  DateTimeYear
  (subtract [this other]
    (if (instance? Period other)
      (catch-date-time-error (.plusYears this (- (quot (:months other) 12))))
      (throw (ex-info (str "Invalid RHS adding to DateYear. Expected Period but was `" (type other) "`.")
                      {:op :subtract :this this :other other}))))

  DateYearMonth
  (subtract [this other]
    (if (instance? Period other)
      (catch-date-time-error (.plusMonths this (- (:months other))))
      (throw (ex-info (str "Invalid RHS adding to DateYearMonth. Expected Period but was `" (type other) "`.")
                      {:op :subtract :this this :other other}))))

  DateTimeYearMonth
  (subtract [this other]
    (if (instance? Period other)
      (catch-date-time-error (.plusMonths this (- (:months other))))
      (throw (ex-info (str "Invalid RHS adding to DateYearMonth. Expected Period but was `" (type other) "`.")
                      {:op :subtract :this this :other other}))))

  DateDate
  (subtract [this other]
    (if (instance? Period other)
      (catch-date-time-error
       (-> (bt/plus-unit this (- (:months other)) ChronoUnit/MONTHS)
           (bt/plus-unit (- (quot (:millis other) 86400000)) ChronoUnit/DAYS)))
      (throw (ex-info (str "Invalid RHS adding to DateDate. Expected Period but was `" (type other) "`.")
                      {:op :subtract :this this :other other}))))

  DateTimeDate
  (subtract [this other]
    (if (instance? Period other)
      (catch-date-time-error
       (-> (bt/plus-unit this (- (:months other)) ChronoUnit/MONTHS)
           (bt/plus-unit (- (quot (:millis other) 86400000)) ChronoUnit/DAYS)))
      (throw (ex-info (str "Invalid RHS adding to DateDate. Expected Period but was `" (type other) "`.")
                      {:op :subtract :this this :other other}))))

  LocalDateTime
  (subtract [this other]
    (if (instance? Period other)
      (let [result (-> this
                       (.minusMonths (:months other))
                       (.minusNanos (* (:millis other) 1000000)))]
        (when (>= (.compareTo result min-date-time) 0)
          result))
      (throw (ex-info (str "Invalid RHS adding to LocalDateTime. Expected Period but was `" (type other) "`.")
                      {:op :subtract :this this :other other}))))

  OffsetDateTime
  (subtract [x y]
    (with-offset-of x (p/subtract (.toLocalDateTime x) y)))

  PrecisionLocalTime
  (subtract [this other]
    (if (instance? Period other)
      ;; TODO: don't wrap
      (->PrecisionLocalTime (.minusNanos ^LocalTime (:local-time this) (* (:millis other) 1000000)) (:p-num this))
      (throw (ex-info (str "Invalid RHS adding to LocalTime. Expected Period but was `" (type other) "`.")
                      {:op :subtract :this this :other other})))))

;; 16.15. Successor
(extend-protocol p/Successor
  DateYear
  (successor [x]
    (catch-date-time-error (.plusYears x 1)))

  DateTimeYear
  (successor [x]
    (catch-date-time-error (.plusYears x 1)))

  DateYearMonth
  (successor [x]
    (catch-date-time-error (.plusMonths x 1)))

  DateTimeYearMonth
  (successor [x]
    (catch-date-time-error (.plusMonths x 1)))

  DateDate
  (successor [x]
    (catch-date-time-error (.plusDays x 1)))

  DateTimeDate
  (successor [x]
    (catch-date-time-error (.plusDays x 1)))

  LocalDateTime
  (successor [x]
    (when (time/before? x max-date-time)
      (.plusNanos x 1000000)))

  OffsetDateTime
  (successor [x]
    (with-offset-of x (p/successor (.toLocalDateTime x))))

  PrecisionLocalTime
  (successor [{:keys [local-time p-num] :as x}]
    (when (p/less x max-time nil)
      (->PrecisionLocalTime (.plus ^LocalTime local-time 1 ^ChronoUnit (p-num->precision p-num)) p-num))))

;; 18. Date and Time Operators

;; 18.7. DateFrom
(extend-protocol p/DateFrom
  Date
  (date-from [x]
    x)

  DateTime
  (date-from [x]
    (.toDate x))

  LocalDateTime
  (date-from [x]
    (DateDate/fromLocalDate (.toLocalDate x)))

  OffsetDateTime
  (date-from [x]
    (p/date-from (.toLocalDateTime x))))

;; 18.9. DateTimeComponentFrom
(extend-protocol p/DateTimeComponentFrom
  DateYear
  (date-time-component-from [x precision]
    (let [req-p-num (precision->p-num precision)]
      (when (<= req-p-num 0)
        (get-chrono-field x req-p-num))))

  DateTimeYear
  (date-time-component-from [x precision]
    (let [req-p-num (precision->p-num precision)]
      (when (<= req-p-num 0)
        (get-chrono-field x req-p-num))))

  DateYearMonth
  (date-time-component-from [x precision]
    (let [req-p-num (precision->p-num precision)]
      (when (<= req-p-num 1)
        (get-chrono-field x req-p-num))))

  DateTimeYearMonth
  (date-time-component-from [x precision]
    (let [req-p-num (precision->p-num precision)]
      (when (<= req-p-num 1)
        (get-chrono-field x req-p-num))))

  DateDate
  (date-time-component-from [x precision]
    (let [req-p-num (precision->p-num precision)]
      (when (<= req-p-num 2)
        (get-chrono-field x req-p-num))))

  DateTimeDate
  (date-time-component-from [x precision]
    (let [req-p-num (precision->p-num precision)]
      (when (<= req-p-num 2)
        (get-chrono-field x req-p-num))))

  LocalDateTime
  (date-time-component-from [x precision]
    (let [req-p-num (precision->p-num precision)]
      (when (<= req-p-num 6)
        (get-chrono-field x req-p-num))))

  OffsetDateTime
  (date-time-component-from [x precision]
    (p/date-time-component-from (.toLocalDateTime x) precision))

  PrecisionLocalTime
  (date-time-component-from [{:keys [local-time p-num]} precision]
    (let [req-p-num (precision->p-num precision)]
      (when (<= req-p-num p-num)
        (get-chrono-field local-time req-p-num)))))

;; 18.10. DifferenceBetween
(extend-protocol p/DifferenceBetween
  DateYear
  (difference-between [this other precision _]
    (when other
      (when (<= (precision->p-num precision) (min 0 (precision-num other)))
        (.until this other precision))))

  DateTimeYear
  (difference-between [this other precision now]
    (when other
      (when (<= (precision->p-num precision) (min 0 (precision-num other)))
        (.until this (local other this 6 now) precision))))

  DateYearMonth
  (difference-between [this other precision _]
    (when other
      (when (<= (precision->p-num precision) (min 1 (precision-num other)))
        (.until this other precision))))

  DateTimeYearMonth
  (difference-between [this other precision now]
    (when other
      (when (<= (precision->p-num precision) (min 1 (precision-num other)))
        (.until this (local other this 6 now) precision))))

  DateDate
  (difference-between [this other precision _]
    (when other
      (when (<= (precision->p-num precision) (min 2 (precision-num other)))
        (.until this other precision))))

  DateTimeDate
  (difference-between [this other precision now]
    (when other
      (when (<= (precision->p-num precision) (min 2 (precision-num other)))
        (.until this (local other this 6 now) precision))))

  LocalDateTime
  (difference-between [this other precision now]
    (when other
      (when (<= (precision->p-num precision) (min 6 (precision-num other)))
        (let [duration (.until this (local other this 6 now) precision)]
          (cond
            (pos? duration)
            (inc duration)
            (neg? duration)
            (dec duration)
            :else
            duration)))))

  ;; durations are always calculated in the offset of the evaluation request
  OffsetDateTime
  (difference-between [x y precision now]
    (local-op p/difference-between x y precision 6 now))

  PrecisionLocalTime
  (difference-between [this other precision _]
    (when (instance? PrecisionLocalTime other)
      (when (<= (precision->p-num precision) (min (:p-num this) (:p-num other)))
        (.until ^LocalTime (:local-time this) (:local-time other) precision)))))

;; 18.11. DurationBetween
(extend-protocol p/DurationBetween
  DateYear
  (duration-between [this other precision _]
    (when other
      (when (<= (precision->p-num precision) (min 0 (precision-num other)))
        (.until this other precision))))

  DateTimeYear
  (duration-between [this other precision now]
    (when other
      (when (<= (precision->p-num precision) (min 0 (precision-num other)))
        (.until this (local other this 6 now) precision))))

  DateYearMonth
  (duration-between [this other precision _]
    (when other
      (when (<= (precision->p-num precision) (min 1 (precision-num other)))
        (.until this other precision))))

  DateTimeYearMonth
  (duration-between [this other precision now]
    (when other
      (when (<= (precision->p-num precision) (min 1 (precision-num other)))
        (.until this (local other this 6 now) precision))))

  DateDate
  (duration-between [this other precision _]
    (when other
      (when (<= (precision->p-num precision) (min 2 (precision-num other)))
        (.until this other precision))))

  DateTimeDate
  (duration-between [this other precision now]
    (when other
      (when (<= (precision->p-num precision) (min 2 (precision-num other)))
        (.until this (local other this 6 now) precision))))

  LocalDateTime
  (duration-between [this other precision now]
    (when other
      (when (<= (precision->p-num precision) (min 6 (precision-num other)))
        (.until this (local other this 6 now) precision))))

  ;; durations are always calculated in the offset of the evaluation request
  OffsetDateTime
  (duration-between [x y precision now]
    (local-op p/duration-between x y precision 6 now))

  PrecisionLocalTime
  (duration-between [this other precision _]
    (when (instance? PrecisionLocalTime other)
      (when (<= (precision->p-num precision) (min (:p-num this) (:p-num other)))
        (.until ^LocalTime (:local-time this) (:local-time other) precision)))))

;; 18.14. SameAs
(extend-protocol p/SameAs
  DateYear
  (same-as [this other precision now]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 0 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (= cmp 0)))
        (p/equal this other now))))

  DateTimeYear
  (same-as [this other precision now]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 0 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (= cmp 0)))
        (p/equal this other now))))

  DateYearMonth
  (same-as [this other precision now]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 1 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (= cmp 0)))
        (p/equal this other now))))

  DateTimeYearMonth
  (same-as [this other precision now]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 1 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (= cmp 0)))
        (p/equal this other now))))

  DateDate
  (same-as [this other precision now]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 2 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (= cmp 0)))
        (p/equal this other now))))

  DateTimeDate
  (same-as [this other precision now]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 2 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (= cmp 0)))
        (p/equal this other now))))

  LocalDateTime
  (same-as [this other precision now]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 6 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (= cmp 0)))
        (p/equal this other now))))

  OffsetDateTime
  (same-as [x y precision now]
    (local-op p/same-as x y precision now))

  PrecisionLocalTime
  (same-as [this other precision now]
    (when (instance? PrecisionLocalTime other)
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min (:p-num this) (:p-num other)))
          (when-let [cmp (compare-fields (:local-time this) (:local-time other)
                                         p-num p-num 3)]
            (= cmp 0)))
        (p/equal this other now)))))

;; 18.15. SameOrBefore
(extend-protocol p/SameOrBefore
  DateYear
  (same-or-before [this other precision now]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 0 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (<= cmp 0)))
        (p/less-or-equal this other now))))

  DateTimeYear
  (same-or-before [this other precision now]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 0 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (<= cmp 0)))
        (p/less-or-equal this other now))))

  DateYearMonth
  (same-or-before [this other precision now]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 1 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (<= cmp 0)))
        (p/less-or-equal this other now))))

  DateTimeYearMonth
  (same-or-before [this other precision now]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 1 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (<= cmp 0)))
        (p/less-or-equal this other now))))

  DateDate
  (same-or-before [this other precision now]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 2 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (<= cmp 0)))
        (p/less-or-equal this other now))))

  DateTimeDate
  (same-or-before [this other precision now]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 2 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (<= cmp 0)))
        (p/less-or-equal this other now))))

  LocalDateTime
  (same-or-before [this other precision now]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 6 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (<= cmp 0)))
        (p/less-or-equal this other now))))

  OffsetDateTime
  (same-or-before [x y precision now]
    (local-op p/same-or-before x y precision now))

  PrecisionLocalTime
  (same-or-before [this other precision now]
    (when (instance? PrecisionLocalTime other)
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min (:p-num this) (:p-num other)))
          (when-let [cmp (compare-fields (:local-time this) (:local-time other)
                                         p-num p-num 3)]
            (<= cmp 0)))
        (p/less-or-equal this other now)))))

;; 18.16. SameOrAfter
(extend-protocol p/SameOrAfter
  DateYear
  (same-or-after [this other precision now]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 0 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (>= cmp 0)))
        (p/greater-or-equal this other now))))

  DateTimeYear
  (same-or-after [this other precision now]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 0 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (>= cmp 0)))
        (p/greater-or-equal this other now))))

  DateYearMonth
  (same-or-after [this other precision now]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 1 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (>= cmp 0)))
        (p/greater-or-equal this other now))))

  DateTimeYearMonth
  (same-or-after [this other precision now]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 1 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (>= cmp 0)))
        (p/greater-or-equal this other now))))

  DateDate
  (same-or-after [this other precision now]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 2 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (>= cmp 0)))
        (p/greater-or-equal this other now))))

  DateTimeDate
  (same-or-after [this other precision now]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 2 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (>= cmp 0)))
        (p/greater-or-equal this other now))))

  LocalDateTime
  (same-or-after [this other precision now]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min 6 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (>= cmp 0)))
        (p/greater-or-equal this other now))))

  OffsetDateTime
  (same-or-after [x y precision now]
    (local-op p/same-or-after x y precision now))

  PrecisionLocalTime
  (same-or-after [this other precision now]
    (when (instance? PrecisionLocalTime other)
      (if-let [p-num (some-> precision precision->p-num)]
        (when (<= p-num (min (:p-num this) (:p-num other)))
          (when-let [cmp (compare-fields (:local-time this) (:local-time other)
                                         p-num p-num 3)]
            (>= cmp 0)))
        (p/greater-or-equal this other now)))))

;; 19.2. After
(extend-protocol p/After
  DateYear
  (after [this other precision now]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 0 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (> cmp 0))
          (p/greater this other now)))))

  DateTimeYear
  (after [this other precision now]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 0 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (> cmp 0))
          (p/greater this other now)))))

  DateYearMonth
  (after [this other precision now]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 1 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (> cmp 0))
          (p/greater this other now)))))

  DateTimeYearMonth
  (after [this other precision now]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 1 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (> cmp 0))
          (p/greater this other now)))))

  DateDate
  (after [this other precision now]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 2 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (> cmp 0))
          (p/greater this other now)))))

  DateTimeDate
  (after [this other precision now]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 2 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (> cmp 0))
          (p/greater this other now)))))

  LocalDateTime
  (after [this other precision now]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 6 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (> cmp 0))
          (p/greater this other now)))))

  OffsetDateTime
  (after [x y precision now]
    (local-op p/after x y precision now))

  PrecisionLocalTime
  (after [this other precision now]
    (when (instance? PrecisionLocalTime other)
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min (:p-num this) (:p-num other))))
          (when-let [cmp (compare-fields (:local-time this) (:local-time other)
                                         p-num p-num 3)]
            (> cmp 0))
          (p/greater this other now))))))

;; 19.3. Before
(extend-protocol p/Before
  DateYear
  (before [this other precision now]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 0 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (< cmp 0))
          (p/less this other now)))))

  DateTimeYear
  (before [this other precision now]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 0 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (< cmp 0))
          (p/less this other now)))))

  DateYearMonth
  (before [this other precision now]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 1 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (< cmp 0))
          (p/less this other now)))))

  DateTimeYearMonth
  (before [this other precision now]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 1 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (< cmp 0))
          (p/less this other now)))))

  DateDate
  (before [this other precision now]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 2 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (< cmp 0))
          (p/less this other now)))))

  DateTimeDate
  (before [this other precision now]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 2 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (< cmp 0))
          (p/less this other now)))))

  LocalDateTime
  (before [this other precision now]
    (when other
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min 6 (precision-num other))))
          (when-let [cmp (compare-to-precision this other p-num p-num now)]
            (< cmp 0))
          (p/less this other now)))))

  OffsetDateTime
  (before [x y precision now]
    (local-op p/before x y precision now))

  PrecisionLocalTime
  (before [this other precision now]
    (when (instance? PrecisionLocalTime other)
      (let [p-num (some-> precision precision->p-num)]
        (if (some-> p-num (<= (min (:p-num this) (:p-num other))))
          (when-let [cmp (compare-fields (:local-time this) (:local-time other)
                                         p-num p-num 3)]
            (< cmp 0))
          (p/less this other now))))))

;; 22. Type Operators

;; 22.22. ToDate
(extend-protocol p/ToDate
  Date
  (to-date [this _]
    this)

  DateTime
  (to-date [this _]
    (.toDate this))

  LocalDateTime
  (to-date [this _]
    (DateDate/fromLocalDate (.toLocalDate this)))

  OffsetDateTime
  (to-date [this _]
    (DateDate/fromLocalDate (.toLocalDate this))))

;; 22.23. ToDateTime
(extend-protocol p/ToDateTime
  Date
  (to-date-time [this _]
    (.toDateTime this))

  DateTime
  (to-date-time [this _]
    this)

  LocalDateTime
  (to-date-time [this _]
    this)

  OffsetDateTime
  (to-date-time [this _]
    this))

;; 22.30. ToString
(extend-protocol p/ToString
  PrecisionLocalTime
  (to-string [{:keys [local-time]}]
    (str local-time))

  DateYear
  (to-string [x]
    (str x))

  DateTimeYear
  (to-string [x]
    (str x))

  DateYearMonth
  (to-string [x]
    (str x))

  DateTimeYearMonth
  (to-string [x]
    (str x))

  DateDate
  (to-string [x]
    (str x))

  DateTimeDate
  (to-string [x]
    (str x))

  LocalDateTime
  (to-string [x]
    (str x))

  OffsetDateTime
  (to-string [x]
    (str x)))

;; 22.31. ToTime
(extend-protocol p/ToTime
  LocalTime
  (to-time [this _]
    this)

  LocalDateTime
  (to-time [this _]
    (.toLocalTime this))

  OffsetDateTime
  (to-time [this now]
    (-> (.withOffsetSameInstant this (.getOffset ^OffsetDateTime now))
        (.toLocalTime)))

  PrecisionLocalTime
  (to-time [this _]
    (.-local_time this)))
