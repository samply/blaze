(ns life-fhir-store.elm.date-time
  "Implementation of the integer type.

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [clojure.spec.alpha :as s]
    [life-fhir-store.elm.protocols :as p])
  (:import
    [java.time LocalDate LocalDateTime LocalTime OffsetDateTime Year YearMonth]
    [java.time.temporal ChronoField ChronoUnit Temporal TemporalAccessor TemporalAmount]))


(def min-year (Year/of 1))
(def min-year-month (YearMonth/of 1 1))
(def min-date (LocalDate/of 1 1 1))
(def min-date-time (LocalDateTime/of 1 1 1 0 0 0 0))


(def max-year (Year/of 9999))
(def max-year-month (YearMonth/of 9999 12))
(def max-date (LocalDate/of 9999 12 31))
(def max-date-time (LocalDateTime/of 9999 12 31 23 59 59 999000000))


(defrecord Period [months seconds]
  p/Add
  (add [this other]
    (if (instance? Period other)
      (->Period
        (+ months (:months other))
        (+ seconds (:seconds other)))
      (throw (ex-info (str "Invalid RHS adding to Period. Expected Period but was `" (type other) "`.")
                      {:op :add :this this :other other}))))

  p/Divide
  (divide [_ other]
    (->Period (/ months other) (/ seconds other)))

  p/Multiply
  (multiply [_ other]
    (->Period (* months other) (* seconds other)))

  p/Negate
  (negate [_]
    (->Period (- months) (- seconds)))

  p/Subtract
  (subtract [this other]
    (if (instance? Period other)
      (->Period
        (- months (:months other))
        (- seconds (:seconds other)))
      (throw (ex-info (str "Invalid RHS subtracting from Period. Expected Period but was `" (type other) "`.")
                      {:op :subtract :this this :other other})))))


(s/fdef period
  :args (s/cat :years number? :months number? :seconds number?))

(defn period [years months seconds]
  (->Period (+ (* years 12) months) seconds))


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
  YearMonth
  (precision-num [_] 1)
  LocalDate
  (precision-num [_] 2)
  LocalDateTime
  (precision-num [_] 5))



;; 12. Comparison Operators

;; 12.1. Equal
(extend-protocol p/Equal
  PrecisionLocalTime
  (equal [this other]
    (when (instance? PrecisionLocalTime other)
      (when-let [cmp (compare-to-precision (:local-time this) (:local-time other)
                                           (:p-num this) (:p-num other) 3)]
        (zero? cmp)))))


;; 12.3. Greater
(extend-protocol p/Greater
  Year
  (greater [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 0 (precision-num other))]
        (> cmp 0))))

  YearMonth
  (greater [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision-num other))]
        (> cmp 0))))

  LocalDate
  (greater [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision-num other))]
        (> cmp 0))))

  LocalDateTime
  (greater [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 5 (precision-num other))]
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

  YearMonth
  (greater-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision-num other))]
        (>= cmp 0))))

  LocalDate
  (greater-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision-num other))]
        (>= cmp 0))))

  LocalDateTime
  (greater-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 5 (precision-num other))]
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

  YearMonth
  (less [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision-num other))]
        (< cmp 0))))

  LocalDate
  (less [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision-num other))]
        (< cmp 0))))

  LocalDateTime
  (less [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 5 (precision-num other))]
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

  YearMonth
  (less-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision-num other))]
        (<= cmp 0))))

  LocalDate
  (less-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision-num other))]
        (<= cmp 0))))

  LocalDateTime
  (less-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 5 (precision-num other))]
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
      (.plusYears this (quot (:months other) 12))
      (throw (ex-info (str "Invalid RHS adding to Year. Expected Period but was `" (type other) "`.")
                      {:op :add :this this :other other}))))

  YearMonth
  (add [this other]
    (if (instance? Period other)
      (.plusMonths this (:months other))
      (throw (ex-info (str "Invalid RHS adding to YearMonth. Expected Period but was `" (type other) "`.")
                      {:op :add :this this :other other}))))

  LocalDate
  (add [this other]
    (if (instance? Period other)
      (-> this
          (.plusMonths (:months other))
          (.plusDays (quot (:seconds other) 86400)))
      (throw (ex-info (str "Invalid RHS adding to LocalDate. Expected Period but was `" (type other) "`.")
                      {:op :add :this this :other other}))))

  LocalDateTime
  (add [this other]
    (if (instance? Period other)
      (-> this
          (.plusMonths (:months other))
          (.plusSeconds (:seconds other)))
      (throw (ex-info (str "Invalid RHS adding to LocalDateTime. Expected Period but was `" (type other) "`.")
                      {:op :add :this this :other other}))))

  PrecisionLocalTime
  (add [this other]
    (if (instance? Period other)
      (->PrecisionLocalTime (.plusSeconds (:local-time this) (:seconds other)) (:p-num this))
      (throw (ex-info (str "Invalid RHS adding to LocalTime. Expected Period but was `" (type other) "`.")
                      {:op :add :this this :other other})))))


;; 16.15. Predecessor
(extend-protocol p/Predecessor
  Year
  (predecessor [x]
    (if (.isAfter x min-year)
      (.minusYears x 1)
      (throw (ex-info "Predecessor: argument is already the minimum value."
                      {:x x}))))

  YearMonth
  (predecessor [x]
    (if (.isAfter x min-year-month)
      (.minusMonths x 1)
      (throw (ex-info "Predecessor: argument is already the minimum value."
                      {:x x}))))

  LocalDate
  (predecessor [x]
    (if (.isAfter x min-date)
      (.minusDays x 1)
      (throw (ex-info "Predecessor: argument is already the minimum value."
                      {:x x}))))

  LocalDateTime
  (predecessor [x]
    (if (.isAfter x min-date-time)
      (.minusNanos x 1000000)
      (throw (ex-info "Predecessor: argument is already the minimum value."
                      {:x x}))))

  PrecisionLocalTime
  (predecessor [{:keys [local-time p-num] :as x}]
    (if (p/greater x min-time)
      (->PrecisionLocalTime (.minus ^LocalTime local-time 1 (p-num->precision p-num)) p-num)
      (throw (ex-info "Predecessor: argument is already the minimum value."
                      {:x x})))))


;; 16.17. Subtract
(extend-protocol p/Subtract
  Year
  (subtract [this other]
    (if (instance? Period other)
      (.minusYears this (int (quot (:months other) 12)))
      (throw (ex-info (str "Invalid RHS adding to Year. Expected Period but was `" (type other) "`.")
                      {:op :subtract :this this :other other}))))

  YearMonth
  (subtract [this other]
    (if (instance? Period other)
      (.minusMonths this (int (:months other)))
      (throw (ex-info (str "Invalid RHS adding to YearMonth. Expected Period but was `" (type other) "`.")
                      {:op :subtract :this this :other other}))))

  LocalDate
  (subtract [this other]
    (if (instance? Period other)
      (-> this
          (.minusMonths (int (:months other)))
          (.minusDays (int (quot (:seconds other) 86400))))
      (throw (ex-info (str "Invalid RHS adding to LocalDate. Expected Period but was `" (type other) "`.")
                      {:op :subtract :this this :other other}))))

  LocalDateTime
  (subtract [this other]
    (if (instance? Period other)
      (-> this
          (.minusMonths (:months other))
          (.minusSeconds (:seconds other)))
      (throw (ex-info (str "Invalid RHS adding to LocalDateTime. Expected Period but was `" (type other) "`.")
                      {:op :subtract :this this :other other}))))

  PrecisionLocalTime
  (subtract [this other]
    (if (instance? Period other)
      (->PrecisionLocalTime (.minusSeconds (:local-time this) (:seconds other)) (:p-num this))
      (throw (ex-info (str "Invalid RHS adding to LocalTime. Expected Period but was `" (type other) "`.")
                      {:op :subtract :this this :other other})))))


;; 16.15. Successor
(extend-protocol p/Successor
  Year
  (successor [x]
    (if (.isBefore x max-year)
      (.plusYears x 1)
      (throw (ex-info "Predecessor: argument is already the maximum value."
                      {:x x}))))

  YearMonth
  (successor [x]
    (if (.isBefore x max-year-month)
      (.plusMonths x 1)
      (throw (ex-info "Predecessor: argument is already the maximum value."
                      {:x x}))))

  LocalDate
  (successor [x]
    (if (.isBefore x max-date)
      (.plusDays x 1)
      (throw (ex-info "Predecessor: argument is already the maximum value."
                      {:x x}))))

  LocalDateTime
  (successor [x]
    (if (.isBefore x max-date-time)
      (.plusNanos x 1000000)
      (throw (ex-info "Predecessor: argument is already the maximum value."
                      {:x x}))))

  PrecisionLocalTime
  (successor [{:keys [local-time p-num] :as x}]
    (if (p/less x max-time)
      (->PrecisionLocalTime (.plus ^LocalTime local-time 1 (p-num->precision p-num)) p-num)
      (throw (ex-info "Predecessor: argument is already the maximum value."
                      {:x x})))))



;; 18. Date and Time Operators

;; 18.11. DurationBetween
(extend-protocol p/DurationBetween
  Year
  (duration-between [this other precision]
    (when other
      (when (<= (precision->p-num precision) (min 0 (precision-num other)))
        (.until this other precision))))

  YearMonth
  (duration-between [this other precision]
    (when other
      (when (<= (precision->p-num precision) (min 1 (precision-num other)))
        (.until this other precision))))

  LocalDate
  (duration-between [this other precision]
    (when other
      (when (<= (precision->p-num precision) (min 2 (precision-num other)))
        (.until this other precision))))

  LocalDateTime
  (duration-between [this other precision]
    (when other
      (when (<= (precision->p-num precision) (min 5 (precision-num other)))
        (.until this other precision)))))


;; 18.15. SameOrBefore
(extend-protocol p/SameOrBefore
  Year
  (same-or-before [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (if (<= p-num (min 0 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (<= cmp 0)))
        (p/less-or-equal this other))))

  YearMonth
  (same-or-before [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (if (<= p-num (min 1 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (<= cmp 0)))
        (p/less-or-equal this other))))

  LocalDate
  (same-or-before [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (if (<= p-num (min 2 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (<= cmp 0)))
        (p/less-or-equal this other))))

  LocalDateTime
  (same-or-before [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (if (<= p-num (min 5 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (<= cmp 0)))
        (p/less-or-equal this other))))

  PrecisionLocalTime
  (same-or-before [this other precision]
    (when (instance? PrecisionLocalTime other)
      (if-let [p-num (some-> precision precision->p-num)]
        (if (<= p-num (min (:p-num this) (:p-num other)))
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
        (if (<= p-num (min 0 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (>= cmp 0)))
        (p/greater-or-equal this other))))

  YearMonth
  (same-or-after [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (if (<= p-num (min 1 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (>= cmp 0)))
        (p/greater-or-equal this other))))

  LocalDate
  (same-or-after [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (if (<= p-num (min 2 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (>= cmp 0)))
        (p/greater-or-equal this other))))

  LocalDateTime
  (same-or-after [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (if (<= p-num (min 5 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (>= cmp 0)))
        (p/greater-or-equal this other))))

  PrecisionLocalTime
  (same-or-after [this other precision]
    (when (instance? PrecisionLocalTime other)
      (if-let [p-num (some-> precision precision->p-num)]
        (if (<= p-num (min (:p-num this) (:p-num other)))
          (when-let [cmp (compare-to-precision (:local-time this) (:local-time other)
                                               p-num p-num 3)]
            (>= cmp 0)))
        (p/greater-or-equal this other)))))


;; 19.2. After
(extend-protocol p/After
  Year
  (after [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (if (<= p-num (min 0 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (> cmp 0)))
        (p/greater this other))))

  YearMonth
  (after [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (if (<= p-num (min 1 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (> cmp 0)))
        (p/greater this other))))

  LocalDate
  (after [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (if (<= p-num (min 2 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (> cmp 0)))
        (p/greater this other))))

  LocalDateTime
  (after [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (if (<= p-num (min 5 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (> cmp 0)))
        (p/greater this other))))

  PrecisionLocalTime
  (after [this other precision]
    (when (instance? PrecisionLocalTime other)
      (if-let [p-num (some-> precision precision->p-num)]
        (if (<= p-num (min (:p-num this) (:p-num other)))
          (when-let [cmp (compare-to-precision (:local-time this) (:local-time other)
                                               p-num p-num 3)]
            (> cmp 0)))
        (p/greater this other)))))


;; 19.3. Before
(extend-protocol p/Before
  Year
  (before [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (if (<= p-num (min 0 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (< cmp 0)))
        (p/less this other))))

  YearMonth
  (before [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (if (<= p-num (min 1 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (< cmp 0)))
        (p/less this other))))

  LocalDate
  (before [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (if (<= p-num (min 2 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (< cmp 0)))
        (p/less this other))))

  LocalDateTime
  (before [this other precision]
    (when other
      (if-let [p-num (some-> precision precision->p-num)]
        (if (<= p-num (min 5 (precision-num other)))
          (when-let [cmp (compare-to-precision this other p-num p-num)]
            (< cmp 0)))
        (p/less this other))))

  PrecisionLocalTime
  (before [this other precision]
    (when (instance? PrecisionLocalTime other)
      (if-let [p-num (some-> precision precision->p-num)]
        (if (<= p-num (min (:p-num this) (:p-num other)))
          (when-let [cmp (compare-to-precision (:local-time this) (:local-time other)
                                               p-num p-num 3)]
            (< cmp 0)))
        (p/less this other)))))



;; 22. Type Operators

;; 22.19. ToDate
(extend-protocol p/ToDate
  nil
  (to-date [_ _])

  String
  (to-date [this _]
    (case (.length this)
      4
      (try (Year/parse this) (catch Exception _))
      7
      (try (YearMonth/parse this) (catch Exception _))
      10
      (try (LocalDate/parse this) (catch Exception _))
      nil))

  Year
  (to-date [this _]
    this)

  YearMonth
  (to-date [this _]
    this)

  LocalDate
  (to-date [this _]
    this)

  LocalDateTime
  (to-date [this _]
    (.toLocalDate this))

  OffsetDateTime
  (to-date [this now]
    (-> (.withOffsetSameInstant this (.getOffset ^OffsetDateTime now))
        (.toLocalDate))))


;; 22.20. ToDateTime
(extend-protocol p/ToDateTime
  nil
  (to-date-time [_ _])

  Year
  (to-date-time [this _]
    this)

  YearMonth
  (to-date-time [this _]
    this)

  LocalDate
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
  (to-date-time [s _]
    ;; TODO: implement
    (throw (Exception. (str "Not implemented yet `ToDateTime('" s "')`.")))))
