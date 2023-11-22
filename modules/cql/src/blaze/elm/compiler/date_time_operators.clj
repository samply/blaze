(ns blaze.elm.compiler.date-time-operators
  "18. Date and Time Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.macros :refer [defbinopp defunop defunopp]]
   [blaze.elm.date-time :as date-time]
   [blaze.elm.protocols :as p]
   [blaze.fhir.spec.type.system :as system])
  (:import
   [blaze.fhir.spec.type.system DateDate]
   [java.time OffsetDateTime ZoneOffset]))

(set! *warn-on-reflection* true)

(defn- to-local-date-time-with-offset
  "Creates a DateTime with a local date time adjusted for the offset of the
  evaluation request."
  [now year month day hour minute second millis timezone-offset]
  (-> ^OffsetDateTime
   (system/date-time year month day hour minute second millis
                     (ZoneOffset/ofTotalSeconds (* timezone-offset 3600)))
      (.withOffsetSameInstant (.getOffset ^OffsetDateTime now))
      (.toLocalDateTime)))

;; 18.6. Date
(defmethod core/compile* :elm.compiler.type/date
  [context {:keys [year month day]}]
  (let [year (some->> year (core/compile* context))
        month (some->> month (core/compile* context))
        day (some->> day (core/compile* context))]
    (cond
      (and (int? day) (int? month) (int? year))
      (system/date year month day)

      (some? day)
      (reify
        system/SystemType
        (-type [_] :system/date)
        core/Expression
        (-static [_]
          false)
        (-eval [_ context resource scope]
          (when-let [year (core/-eval year context resource scope)]
            (if-let [month (core/-eval month context resource scope)]
              (if-let [day (core/-eval day context resource scope)]
                (system/date year month day)
                (system/date year month))
              (system/date year))))
        (-form [_]
          (list 'date (core/-form year) (core/-form month) (core/-form day))))

      (and (int? month) (int? year))
      (system/date year month)

      (some? month)
      (reify
        system/SystemType
        (-type [_] :system/date)
        core/Expression
        (-static [_]
          false)
        (-eval [_ context resource scope]
          (when-let [year (core/-eval year context resource scope)]
            (if-let [month (core/-eval month context resource scope)]
              (system/date year month)
              (system/date year))))
        (-form [_]
          (list 'date (core/-form year) (core/-form month))))

      (int? year)
      (system/date year)

      :else
      (when year
        (reify
          system/SystemType
          (-type [_] :system/date)
          core/Expression
          (-static [_]
            false)
          (-eval [_ context resource scope]
            (some-> (core/-eval year context resource scope) system/date))
          (-form [_]
            (list 'date (core/-form year))))))))

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
          (-static [_]
            false)
          (-eval [_ {:keys [now]} _ _]
            (to-local-date-time-with-offset
             now year month day hour minute second millisecond timezone-offset))
          (-form [_]
            (list 'date-time (core/-form year) (core/-form month)
                  (core/-form day) (core/-form hour) (core/-form minute)
                  (core/-form second) (core/-form millisecond)
                  (core/-form timezone-offset))))

        (some? hour)
        (reify core/Expression
          (-static [_]
            false)
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
             timezone-offset))
          (-form [_]
            (list 'date-time (core/-form year) (core/-form month)
                  (core/-form day) (core/-form hour) (core/-form minute)
                  (core/-form second) (core/-form millisecond)
                  (core/-form timezone-offset))))

        :else
        (throw (ex-info "Need at least an hour if timezone offset is given."
                        {:expression expression})))

      (some? timezone-offset)
      (if (some? hour)
        (reify core/Expression
          (-static [_]
            false)
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
             (core/-eval timezone-offset context resource scope)))
          (-form [_]
            (list 'date-time (core/-form year) (core/-form month)
                  (core/-form day) (core/-form hour) (core/-form minute)
                  (core/-form second) (core/-form millisecond)
                  (core/-form timezone-offset))))
        (throw (ex-info "Need at least an hour if timezone offset is given."
                        {:expression expression})))

      :else
      (cond
        (and (int? millisecond) (int? second) (int? minute) (int? hour)
             (int? day) (int? month) (int? year))
        (system/date-time year month day hour minute second millisecond)

        (some? hour)
        (reify core/Expression
          (-static [_]
            false)
          (-eval [_ context resource scope]
            (system/date-time
             (core/-eval year context resource scope)
             (core/-eval month context resource scope)
             (core/-eval day context resource scope)
             (core/-eval hour context resource scope)
             (or (core/-eval minute context resource scope) 0)
             (or (core/-eval second context resource scope) 0)
             (or (core/-eval millisecond context resource scope) 0)))
          (-form [_]
            (list 'date-time (core/-form year) (core/-form month)
                  (core/-form day) (core/-form hour) (core/-form minute)
                  (core/-form second) (core/-form millisecond))))

        (and (int? day) (int? month) (int? year))
        (system/date-time year month day)

        (some? day)
        (reify core/Expression
          (-static [_]
            false)
          (-eval [_ context resource scope]
            (when-let [year (core/-eval year context resource scope)]
              (if-let [month (core/-eval month context resource scope)]
                (if-let [day (core/-eval day context resource scope)]
                  (system/date-time year month day)
                  (system/date-time year month))
                (system/date-time year))))
          (-form [_]
            (list 'date-time (core/-form year) (core/-form month)
                  (core/-form day))))

        (and (int? month) (int? year))
        (system/date-time year month)

        (some? month)
        (reify core/Expression
          (-static [_]
            false)
          (-eval [_ context resource scope]
            (when-let [year (core/-eval year context resource scope)]
              (if-let [month (core/-eval month context resource scope)]
                (system/date-time year month)
                (system/date-time year))))
          (-form [_]
            (list 'date-time (core/-form year) (core/-form month))))

        (int? year)
        (system/date-time year)

        :else
        (when year
          (reify core/Expression
            (-static [_]
              false)
            (-eval [_ context resource scope]
              (some-> (core/-eval year context resource scope) system/date-time))
            (-form [_]
              (list 'date-time (core/-form year)))))))))

;; 18.9. DateTimeComponentFrom
(defunopp date-time-component-from [x precision]
  (p/date-time-component-from x precision))

;; 18.10. DifferenceBetween
(defbinopp difference-between [operand-1 operand-2 ^:required precision]
  (p/difference-between operand-1 operand-2 precision))

;; 18.11. DurationBetween
(defbinopp duration-between [operand-1 operand-2 ^:required precision]
  (p/duration-between operand-1 operand-2 precision))

;; 18.13. Now
(defrecord NowExpression []
  core/Expression
  (-static [_]
    false)
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
        (-static [_]
          false)
        (-eval [_ context resource scope]
          (date-time/local-time (core/-eval hour context resource scope)
                                (core/-eval minute context resource scope)
                                (core/-eval second context resource scope)
                                (core/-eval millisecond context resource scope)))
        (-form [_]
          (list 'time (core/-form hour) (core/-form minute) (core/-form second)
                (core/-form millisecond))))

      (and (int? second) (int? minute) (int? hour))
      (date-time/local-time hour minute second)

      (some? second)
      (reify core/Expression
        (-static [_]
          false)
        (-eval [_ context resource scope]
          (date-time/local-time (core/-eval hour context resource scope)
                                (core/-eval minute context resource scope)
                                (core/-eval second context resource scope)))
        (-form [_]
          (list 'time (core/-form hour) (core/-form minute) (core/-form second))))

      (and (int? minute) (int? hour))
      (date-time/local-time hour minute)

      (some? minute)
      (reify core/Expression
        (-static [_]
          false)
        (-eval [_ context resource scope]
          (date-time/local-time (core/-eval hour context resource scope)
                                (core/-eval minute context resource scope)))
        (-form [_]
          (list 'time (core/-form hour) (core/-form minute))))

      (int? hour)
      (date-time/local-time hour)

      :else
      (reify core/Expression
        (-static [_]
          false)
        (-eval [_ context resource scope]
          (date-time/local-time (core/-eval hour context resource scope)))
        (-form [_]
          (list 'time (core/-form hour)))))))

(def ^:private time-of-day-expr
  (reify
    core/Expression
    (-static [_]
      false)
    (-eval [_ {:keys [now]} _ _]
      (.toLocalTime ^OffsetDateTime now))
    (-form [_]
      'time-of-day)))

;; 18.21. TimeOfDay
(defmethod core/compile* :elm.compiler.type/time-of-day
  [_ _]
  time-of-day-expr)

(def ^:private today-expr
  (reify core/Expression
    (-static [_]
      false)
    (-eval [_ {:keys [now]} _ _]
      (DateDate/fromLocalDate (.toLocalDate ^OffsetDateTime now)))
    (-form [_]
      'today)))

;; 18.22. Today
(defmethod core/compile* :elm.compiler.type/today
  [_ _]
  today-expr)
