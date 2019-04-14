(ns life-fhir-store.elm.date-time
  "Implementation of the integer type.

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [clojure.spec.alpha :as s]
    [life-fhir-store.elm.protocols :as p])
  (:import
    [java.time LocalDate LocalDateTime LocalTime OffsetDateTime Year YearMonth]
    [java.time.temporal ChronoField ChronoUnit TemporalAccessor]))


(def min-year (Year/of 1))
(def min-year-month (YearMonth/of 1 1))
(def min-date (LocalDate/of 1 1 1))
(def min-date-time (LocalDateTime/of 1 1 1 0 0 0 0))


(def max-year (Year/of 9999))
(def max-year-month (YearMonth/of 9999 12))
(def max-date (LocalDate/of 9999 12 31))
(def max-date-time (LocalDateTime/of 9999 12 31 23 59 59 999000000))
(def max-time (LocalTime/of 23 59 59 999000000))


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


(defprotocol Precision
  "Returns the precision of a date-time instance."
  (precision [this]))


(defn- get-chrono-field [^TemporalAccessor ta ^long precision]
  (.getLong ta (case precision
                 0 ChronoField/YEAR
                 1 ChronoField/MONTH_OF_YEAR
                 2 ChronoField/DAY_OF_MONTH
                 3 ChronoField/HOUR_OF_DAY
                 4 ChronoField/MINUTE_OF_HOUR
                 5 ChronoField/SECOND_OF_MINUTE)))


(defn- compare-to-precision
  "Compares two date time values up to the minimum of the specified precisions.

  Returns nil (unknown) if the fields up to the smaller precision are equal but
  one of the precisions is higher."
  [dt-1 dt-2 p-1 p-2]
  (let [min-precision (min p-1 p-2)]
    (loop [precision 0]
      (let [cmp (- (get-chrono-field dt-1 precision)
                   (get-chrono-field dt-2 precision))]
        (if (zero? cmp)
          (if (< precision min-precision)
            (recur (inc precision))
            (when (= p-1 p-2) 0))
          cmp)))))


(def ^:private chrono-unit->precision
  {ChronoUnit/YEARS 0
   ChronoUnit/MONTHS 1
   ChronoUnit/WEEKS 2
   ChronoUnit/DAYS 2
   ChronoUnit/HOURS 3
   ChronoUnit/MINUTES 4
   ChronoUnit/SECONDS 5})


(extend-protocol Precision
  Year
  (precision [_] 0)
  YearMonth
  (precision [_] 1)
  LocalDate
  (precision [_] 2)
  LocalDateTime
  (precision [_] 5))



;; 12. Comparison Operators

;; 12.3. Greater
(extend-protocol p/Greater
  Year
  (greater [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 0 (precision other))]
        (> cmp 0))))

  YearMonth
  (greater [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision other))]
        (> cmp 0))))

  LocalDate
  (greater [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision other))]
        (> cmp 0))))

  LocalDateTime
  (greater [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 5 (precision other))]
        (> cmp 0))))

  LocalTime
  (greater [this other]
    (some->> other (.isAfter this))))


;; 12.4. GreaterOrEqual
(extend-protocol p/GreaterOrEqual
  Year
  (greater-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 0 (precision other))]
        (>= cmp 0))))

  YearMonth
  (greater-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision other))]
        (>= cmp 0))))

  LocalDate
  (greater-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision other))]
        (>= cmp 0))))

  LocalDateTime
  (greater-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 5 (precision other))]
        (>= cmp 0))))

  LocalTime
  (greater-or-equal [this other]
    (when other
      (or (.isAfter this other) (= this other)))))


;; 12.5. Less
(extend-protocol p/Less
  Year
  (less [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 0 (precision other))]
        (< cmp 0))))

  YearMonth
  (less [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision other))]
        (< cmp 0))))

  LocalDate
  (less [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision other))]
        (< cmp 0))))

  LocalDateTime
  (less [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 5 (precision other))]
        (< cmp 0))))

  LocalTime
  (less [this other]
    (some->> other (.isBefore this))))


;; 12.6. LessOrEqual
(extend-protocol p/LessOrEqual
  Year
  (less-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 0 (precision other))]
        (<= cmp 0))))

  YearMonth
  (less-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 1 (precision other))]
        (<= cmp 0))))

  LocalDate
  (less-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 2 (precision other))]
        (<= cmp 0))))

  LocalDateTime
  (less-or-equal [this other]
    (when other
      (when-let [cmp (compare-to-precision this other 5 (precision other))]
        (<= cmp 0))))

  LocalTime
  (less-or-equal [this other]
    (when other
      (or (.isBefore this other) (= this other)))))



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

  LocalTime
  (add [this other]
    (if (instance? Period other)
      (.plusSeconds this (:seconds other))
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

  LocalTime
  (predecessor [x]
    (if (.isAfter x LocalTime/MIN)
      (.minusNanos x 1000000)
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

  LocalTime
  (subtract [this other]
    (if (instance? Period other)
      (.minusSeconds this (:seconds other))
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

  LocalTime
  (successor [x]
    (if (.isBefore x max-time)
      (.plusNanos x 1000000)
      (throw (ex-info "Predecessor: argument is already the maximum value."
                      {:x x})))))



;; 18. Date and Time Operators

;; 18.11. DurationBetween
(extend-protocol p/DurationBetween
  nil
  (duration-between [_ _ _])

  Year
  (duration-between [this other chrono-unit]
    (when other
      (when (<= (chrono-unit->precision chrono-unit) (min 0 (precision other)))
        (.until this other chrono-unit))))

  YearMonth
  (duration-between [this other chrono-unit]
    (when other
      (when (<= (chrono-unit->precision chrono-unit) (min 1 (precision other)))
        (.until this other chrono-unit))))

  LocalDate
  (duration-between [this other chrono-unit]
    (when other
      (when (<= (chrono-unit->precision chrono-unit) (min 2 (precision other)))
        (.until this other chrono-unit))))

  LocalDateTime
  (duration-between [this other chrono-unit]
    (when other
      (when (<= (chrono-unit->precision chrono-unit) (min 5 (precision other)))
        (.until this other chrono-unit)))))



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
