(ns blaze.elm.compiler.date-time-operators
  "18. Date and Time Operators"
  (:require
    [blaze.anomaly :refer [throw-anom]]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.macros :refer [defbinopp defunop defunopp]]
    [blaze.elm.date-time :as date-time]
    [blaze.elm.protocols :as p]
    [blaze.fhir.spec.type.system :as system]
    [cognitect.anomalies :as anom])
  (:import
    [java.time OffsetDateTime ZoneOffset]))


(set! *warn-on-reflection* true)


(defn- check-year-range [year]
  (when-not (< 0 year 10000)
    (throw-anom ::anom/incorrect (format "Year `%d` out of range." year))))


(defn- date
  ([year]
   (check-year-range year)
   (system/date year))
  ([year month]
   (check-year-range year)
   (system/date year month))
  ([year month day]
   (check-year-range year)
   (system/date year month day)))


(defn- date-time
  ([year]
   (check-year-range year)
   (system/date-time year))
  ([year month]
   (check-year-range year)
   (system/date-time year month))
  ([year month day]
   (check-year-range year)
   (system/date-time year month day)))


(defn- to-local-date-time
  [year month day hour minute second millis]
  (check-year-range year)
  (system/date-time year month day hour minute second millis))


(defn- to-local-date-time-with-offset
  "Creates a DateTime with a local date time adjusted for the offset of the
  evaluation request."
  [now year month day hour minute second millis timezone-offset]
  (check-year-range year)
  (-> ^OffsetDateTime
      (system/date-time year month day hour minute second millis
                        (ZoneOffset/ofTotalSeconds (* timezone-offset 3600)))
      (.withOffsetSameInstant (.getOffset ^OffsetDateTime now))
      (.toLocalDateTime)))


(defrecord YearExpression [year]
  core/Expression
  (-eval [_ context resource scope]
    (some-> (core/-eval year context resource scope) date)))


(defrecord DateTimeYearExpression [year]
  core/Expression
  (-eval [_ context resource scope]
    (some-> (core/-eval year context resource scope) date-time)))


(defrecord YearMonthExpression [year month]
  core/Expression
  (-eval [_ context resource scope]
    (when-let [year (core/-eval year context resource scope)]
      (if-let [month (core/-eval month context resource scope)]
        (date year month)
        (date year)))))


(defrecord DateTimeYearMonthExpression [year month]
  core/Expression
  (-eval [_ context resource scope]
    (when-let [year (core/-eval year context resource scope)]
      (if-let [month (core/-eval month context resource scope)]
        (date-time year month)
        (date-time year)))))


(defrecord LocalDateExpression [year month day]
  core/Expression
  (-eval [_ context resource scope]
    (when-let [year (core/-eval year context resource scope)]
      (if-let [month (core/-eval month context resource scope)]
        (if-let [day (core/-eval day context resource scope)]
          (date year month day)
          (date year month))
        (date year)))))


(defrecord DateTimeYearMonthDayExpression [year month day]
  core/Expression
  (-eval [_ context resource scope]
    (when-let [year (core/-eval year context resource scope)]
      (if-let [month (core/-eval month context resource scope)]
        (if-let [day (core/-eval day context resource scope)]
          (date-time year month day)
          (date-time year month))
        (date-time year)))))


;; 18.6. Date
(defmethod core/compile* :elm.compiler.type/date
  [context {:keys [year month day]}]
  (let [year (some->> year (core/compile* context))
        month (some->> month (core/compile* context))
        day (some->> day (core/compile* context))]
    (cond
      (and (int? day) (int? month) (int? year))
      (date year month day)

      (some? day)
      (->LocalDateExpression year month day)

      (and (int? month) (int? year))
      (date year month)

      (some? month)
      (->YearMonthExpression year month)

      (int? year)
      (date year)

      :else
      (some-> year ->YearExpression))))


;; 18.7. DateFrom
(defunop date-from [x]
  (p/date-from x))


;; 18.8. DateTime
(defmethod core/compile* :elm.compiler.type/date-time
  [context {:keys [year month day hour minute second millisecond]
            timezone-offset :timezoneOffset
            :as expression}]
  (let [year (some->> year (core/compile* context))
        month (some->> month (core/compile* context))
        day (some->> day (core/compile* context))
        hour (some->> hour (core/compile* context))
        minute (or (some->> minute (core/compile* context)) 0)
        second (or (some->> second (core/compile* context)) 0)
        millisecond (or (some->> millisecond (core/compile* context)) 0)
        timezone-offset (some->> timezone-offset (core/compile* context))]
    (cond
      (number? timezone-offset)
      (cond
        (and (int? millisecond) (int? second) (int? minute) (int? hour)
             (int? day) (int? month) (int? year))
        (reify core/Expression
          (-eval [_ {:keys [now]} _ _]
            (to-local-date-time-with-offset
              now year month day hour minute second millisecond timezone-offset)))

        (some? hour)
        (reify core/Expression
          (-eval [_ {:keys [now] :as context} resource scope]
            (to-local-date-time-with-offset
              now
              (core/-eval year context resource scope)
              (core/-eval month context resource scope)
              (core/-eval day context resource scope)
              (core/-eval hour context resource scope)
              (or (core/-eval minute context resource scope) 0)
              (or (core/-eval second context resource scope) 0)
              (or (core/-eval millisecond context resource scope) 0)
              timezone-offset)))

        :else
        (throw (ex-info "Need at least an hour if timezone offset is given."
                        {:expression expression})))

      (some? timezone-offset)
      (if (some? hour)
        (reify core/Expression
          (-eval [_ {:keys [now] :as context} resource scope]
            (to-local-date-time-with-offset
              now
              (core/-eval year context resource scope)
              (core/-eval month context resource scope)
              (core/-eval day context resource scope)
              (core/-eval hour context resource scope)
              (or (core/-eval minute context resource scope) 0)
              (or (core/-eval second context resource scope) 0)
              (or (core/-eval millisecond context resource scope) 0)
              (core/-eval timezone-offset context resource scope))))
        (throw (ex-info "Need at least an hour if timezone offset is given."
                        {:expression expression})))

      :else
      (cond
        (and (int? millisecond) (int? second) (int? minute) (int? hour)
             (int? day) (int? month) (int? year))
        (to-local-date-time year month day hour minute second millisecond)

        (some? hour)
        (reify core/Expression
          (-eval [_ context resource scope]
            (to-local-date-time
              (core/-eval year context resource scope)
              (core/-eval month context resource scope)
              (core/-eval day context resource scope)
              (core/-eval hour context resource scope)
              (or (core/-eval minute context resource scope) 0)
              (or (core/-eval second context resource scope) 0)
              (or (core/-eval millisecond context resource scope) 0))))

        (and (int? day) (int? month) (int? year))
        (date-time year month day)

        (some? day)
        (->DateTimeYearMonthDayExpression year month day)

        (and (int? month) (int? year))
        (date-time year month)

        (some? month)
        (->DateTimeYearMonthExpression year month)

        (int? year)
        (date-time year)

        :else
        (some-> year ->DateTimeYearExpression)))))


;; 18.9. DateTimeComponentFrom
(defunopp date-time-component-from [x precision]
  (p/date-time-component-from x precision))


;; 18.10. DifferenceBetween
(defbinopp difference-between [operand-1 operand-2 precision]
  (p/difference-between operand-1 operand-2 precision))


;; 18.11. DurationBetween
(defbinopp duration-between [operand-1 operand-2 precision]
  (p/duration-between operand-1 operand-2 precision))


;; 18.13. Now
(defrecord NowExpression []
  core/Expression
  (-eval [_ {:keys [now]} _ _]
    now))


(def now-expression (->NowExpression))


(defmethod core/compile* :elm.compiler.type/now [_ _]
  now-expression)


;; 18.14. SameAs
(defbinopp same-as [x y precision]
  (p/same-as x y precision))


;; 18.15. SameOrBefore
(defbinopp same-or-before [x y precision]
  (p/same-or-before x y precision))


;; 18.16. SameOrAfter
(defbinopp same-or-after [x y precision]
  (p/same-or-after x y precision))


;; 18.18. Time
(defmethod core/compile* :elm.compiler.type/time
  [context {:keys [hour minute second millisecond]}]
  (let [hour (some->> hour (core/compile* context))
        minute (some->> minute (core/compile* context))
        second (some->> second (core/compile* context))
        millisecond (some->> millisecond (core/compile* context))]
    (cond
      (and (int? millisecond) (int? second) (int? minute) (int? hour))
      (date-time/local-time hour minute second millisecond)

      (some? millisecond)
      (reify core/Expression
        (-eval [_ context resource scope]
          (date-time/local-time (core/-eval hour context resource scope)
                                (core/-eval minute context resource scope)
                                (core/-eval second context resource scope)
                                (core/-eval millisecond context resource scope))))

      (and (int? second) (int? minute) (int? hour))
      (date-time/local-time hour minute second)

      (some? second)
      (reify core/Expression
        (-eval [_ context resource scope]
          (date-time/local-time (core/-eval hour context resource scope)
                                (core/-eval minute context resource scope)
                                (core/-eval second context resource scope))))

      (and (int? minute) (int? hour))
      (date-time/local-time hour minute)

      (some? minute)
      (reify core/Expression
        (-eval [_ context resource scope]
          (date-time/local-time (core/-eval hour context resource scope)
                                (core/-eval minute context resource scope))))

      (int? hour)
      (date-time/local-time hour)

      :else
      (reify core/Expression
        (-eval [_ context resource scope]
          (date-time/local-time (core/-eval hour context resource scope)))))))


(defrecord TimeOfDayExpression []
  core/Expression
  (-eval [_ {:keys [now]} _ _]
    (.toLocalTime ^OffsetDateTime now)))


(def ^:private time-of-day-expr
  (->TimeOfDayExpression))


;; 18.21. TimeOfDay
(defmethod core/compile* :elm.compiler.type/time-of-day
  [_ _]
  time-of-day-expr)


(defrecord TodayExpression []
  core/Expression
  (-eval [_ {:keys [now]} _ _]
    (.toLocalDate ^OffsetDateTime now)))


(def ^:private today-expr
  (->TodayExpression))


;; 18.22. Today
(defmethod core/compile* :elm.compiler.type/today
  [_ _]
  today-expr)
