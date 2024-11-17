(ns blaze.elm.compiler.date-time-operators
  "18. Date and Time Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.macros :refer [defbinopp defunop defunopp reify-expr]]
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
(defn- date-op
  ([year]
   (reify
     system/SystemType
     (-type [_] :system/date)

     core/Expression
     (-static [_]
       false)
     (-attach-cache [_ cache]
       (core/attach-cache-helper date-op cache year))
     (-patient-count [_]
       nil)
     (-resolve-refs [_ expression-defs]
       (core/resolve-refs-helper date-op expression-defs year))
     (-resolve-params [_ parameters]
       (core/resolve-params-helper date-op parameters year))
     (-optimize [_ db]
       (core/optimize-helper date-op db year))
     (-eval [_ context resource scope]
       (some-> (core/-eval year context resource scope) system/date))
     (-form [_]
       (list 'date (core/-form year)))

     Object
     (equals [this other]
       (.equals ^Object (core/-form this) (core/-form other)))
     (hashCode [this]
       (.hashCode ^Object (core/-form this)))))
  ([year month]
   (reify
     system/SystemType
     (-type [_] :system/date)

     core/Expression
     (-static [_]
       false)
     (-attach-cache [_ cache]
       (core/attach-cache-helper date-op cache year month))
     (-patient-count [_]
       nil)
     (-resolve-refs [_ expression-defs]
       (core/resolve-refs-helper date-op expression-defs year month))
     (-resolve-params [_ parameters]
       (core/resolve-params-helper date-op parameters year month))
     (-optimize [_ db]
       (core/optimize-helper date-op db year month))
     (-eval [_ context resource scope]
       (when-let [year (core/-eval year context resource scope)]
         (if-let [month (core/-eval month context resource scope)]
           (system/date year month)
           (system/date year))))
     (-form [_]
       (list 'date (core/-form year) (core/-form month)))

     Object
     (equals [this other]
       (.equals ^Object (core/-form this) (core/-form other)))
     (hashCode [this]
       (.hashCode ^Object (core/-form this)))))
  ([year month day]
   (reify
     system/SystemType
     (-type [_] :system/date)

     core/Expression
     (-static [_]
       false)
     (-attach-cache [_ cache]
       (core/attach-cache-helper date-op cache year month day))
     (-patient-count [_]
       nil)
     (-resolve-refs [_ expression-defs]
       (core/resolve-refs-helper date-op expression-defs year month day))
     (-resolve-params [_ parameters]
       (core/resolve-params-helper date-op parameters year month day))
     (-optimize [_ db]
       (core/optimize-helper date-op db year month day))
     (-eval [_ context resource scope]
       (when-let [year (core/-eval year context resource scope)]
         (if-let [month (core/-eval month context resource scope)]
           (if-let [day (core/-eval day context resource scope)]
             (system/date year month day)
             (system/date year month))
           (system/date year))))
     (-form [_]
       (list 'date (core/-form year) (core/-form month) (core/-form day)))

     Object
     (equals [this other]
       (.equals ^Object (core/-form this) (core/-form other)))
     (hashCode [this]
       (.hashCode ^Object (core/-form this))))))

(defmethod core/compile* :elm.compiler.type/date
  [context {:keys [year month day]}]
  (let [year (some->> year (core/compile* context))
        month (some->> month (core/compile* context))
        day (some->> day (core/compile* context))]
    (cond
      (and (int? day) (int? month) (int? year))
      (system/date year month day)

      (some? day)
      (date-op year month day)

      (and (int? month) (int? year))
      (system/date year month)

      (some? month)
      (date-op year month)

      (int? year)
      (system/date year)

      :else
      (some-> year date-op))))

;; 18.7. DateFrom
(defunop date-from [x]
  (p/date-from x))

;; 18.8. DateTime
(defn- date-time-static-op
  [year month day hour minute second millisecond timezone-offset]
  (reify-expr core/Expression
    (-eval [_ {:keys [now]} _ _]
      (to-local-date-time-with-offset
       now year month day hour minute second millisecond timezone-offset))
    (-form [_]
      (list 'date-time (core/-form year) (core/-form month)
            (core/-form day) (core/-form hour) (core/-form minute)
            (core/-form second) (core/-form millisecond)
            (core/-form timezone-offset)))))

(defn- date-time-dynamic-op
  ([year month day hour minute second millisecond]
   (reify-expr core/Expression
     (-resolve-refs [_ expression-defs]
       (date-time-dynamic-op
        (core/-resolve-refs year expression-defs)
        (core/-resolve-refs month expression-defs)
        (core/-resolve-refs day expression-defs)
        (core/-resolve-refs hour expression-defs)
        (core/-resolve-refs minute expression-defs)
        (core/-resolve-refs second expression-defs)
        (core/-resolve-refs millisecond expression-defs)))
     (-resolve-params [_ parameters]
       (date-time-dynamic-op
        (core/-resolve-params year parameters)
        (core/-resolve-params month parameters)
        (core/-resolve-params day parameters)
        (core/-resolve-params hour parameters)
        (core/-resolve-params minute parameters)
        (core/-resolve-params second parameters)
        (core/-resolve-params millisecond parameters)))
     (-optimize [_ db]
       (date-time-dynamic-op
        (core/-optimize year db)
        (core/-optimize month db)
        (core/-optimize day db)
        (core/-optimize hour db)
        (core/-optimize minute db)
        (core/-optimize second db)
        (core/-optimize millisecond db)))
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
             (core/-form second) (core/-form millisecond)))))
  ([year month day hour minute second millisecond timezone-offset]
   (reify-expr core/Expression
     (-resolve-refs [_ expression-defs]
       (date-time-dynamic-op
        (core/-resolve-refs year expression-defs)
        (core/-resolve-refs month expression-defs)
        (core/-resolve-refs day expression-defs)
        (core/-resolve-refs hour expression-defs)
        (core/-resolve-refs minute expression-defs)
        (core/-resolve-refs second expression-defs)
        (core/-resolve-refs millisecond expression-defs)
        (core/-resolve-refs timezone-offset expression-defs)))
     (-resolve-params [_ parameters]
       (date-time-dynamic-op
        (core/-resolve-params year parameters)
        (core/-resolve-params month parameters)
        (core/-resolve-params day parameters)
        (core/-resolve-params hour parameters)
        (core/-resolve-params minute parameters)
        (core/-resolve-params second parameters)
        (core/-resolve-params millisecond parameters)
        (core/-resolve-params timezone-offset parameters)))
     (-optimize [_ db]
       (date-time-dynamic-op
        (core/-optimize year db)
        (core/-optimize month db)
        (core/-optimize day db)
        (core/-optimize hour db)
        (core/-optimize minute db)
        (core/-optimize second db)
        (core/-optimize millisecond db)
        (core/-optimize timezone-offset db)))
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
             (core/-form timezone-offset))))))

(defn- date-time-dynamic-timezone-offset-op
  [year month day hour minute second millisecond timezone-offset]
  (reify-expr core/Expression
    (-resolve-refs [_ expression-defs]
      (date-time-dynamic-timezone-offset-op
       (core/-resolve-refs year expression-defs)
       (core/-resolve-refs month expression-defs)
       (core/-resolve-refs day expression-defs)
       (core/-resolve-refs hour expression-defs)
       (core/-resolve-refs minute expression-defs)
       (core/-resolve-refs second expression-defs)
       (core/-resolve-refs millisecond expression-defs)
       (core/-resolve-refs timezone-offset expression-defs)))
    (-resolve-params [_ parameters]
      (let [timezone-offset (core/-resolve-params timezone-offset parameters)]
        (if (number? timezone-offset)
          (date-time-dynamic-op
           (core/-resolve-params year parameters)
           (core/-resolve-params month parameters)
           (core/-resolve-params day parameters)
           (core/-resolve-params hour parameters)
           (core/-resolve-params minute parameters)
           (core/-resolve-params second parameters)
           (core/-resolve-params millisecond parameters)
           timezone-offset)
          (date-time-dynamic-timezone-offset-op
           (core/-resolve-params year parameters)
           (core/-resolve-params month parameters)
           (core/-resolve-params day parameters)
           (core/-resolve-params hour parameters)
           (core/-resolve-params minute parameters)
           (core/-resolve-params second parameters)
           (core/-resolve-params millisecond parameters)
           timezone-offset))))
    (-optimize [_ db]
      (let [timezone-offset (core/-optimize timezone-offset db)]
        (if (number? timezone-offset)
          (date-time-dynamic-op
           (core/-optimize year db)
           (core/-optimize month db)
           (core/-optimize day db)
           (core/-optimize hour db)
           (core/-optimize minute db)
           (core/-optimize second db)
           (core/-optimize millisecond db)
           timezone-offset)
          (date-time-dynamic-timezone-offset-op
           (core/-optimize year db)
           (core/-optimize month db)
           (core/-optimize day db)
           (core/-optimize hour db)
           (core/-optimize minute db)
           (core/-optimize second db)
           (core/-optimize millisecond db)
           timezone-offset))))
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
            (core/-form timezone-offset)))))

(defn- date-time-date-op [year month day]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper date-time-date-op cache year month day))
    (-resolve-refs [_ expression-defs]
      (core/resolve-refs-helper date-time-date-op expression-defs year month day))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper date-time-date-op parameters year month day))
    (-optimize [_ db]
      (core/optimize-helper date-time-date-op db year month day))
    (-eval [_ context resource scope]
      (when-let [year (core/-eval year context resource scope)]
        (if-let [month (core/-eval month context resource scope)]
          (if-let [day (core/-eval day context resource scope)]
            (system/date-time year month day)
            (system/date-time year month))
          (system/date-time year))))
    (-form [_]
      (list 'date-time (core/-form year) (core/-form month)
            (core/-form day)))))

(defn- date-time-year-month-op [year month]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper date-time-year-month-op cache year month))
    (-resolve-refs [_ expression-defs]
      (core/resolve-refs-helper date-time-year-month-op expression-defs year month))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper date-time-year-month-op parameters year month))
    (-optimize [_ db]
      (core/optimize-helper date-time-year-month-op db year month))
    (-eval [_ context resource scope]
      (when-let [year (core/-eval year context resource scope)]
        (if-let [month (core/-eval month context resource scope)]
          (system/date-time year month)
          (system/date-time year))))
    (-form [_]
      (list 'date-time (core/-form year) (core/-form month)))))

(defn- date-time-year-op [year]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper date-time-year-op cache year))
    (-resolve-refs [_ expression-defs]
      (core/resolve-refs-helper date-time-year-op expression-defs year))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper date-time-year-op parameters year))
    (-optimize [_ db]
      (core/optimize-helper date-time-year-op db year))
    (-eval [_ context resource scope]
      (some-> (core/-eval year context resource scope) system/date-time))
    (-form [_]
      (list 'date-time (core/-form year)))))

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
        (date-time-static-op year month day hour minute second millisecond
                             timezone-offset)

        (some? hour)
        (date-time-dynamic-op year month day hour minute second millisecond
                              timezone-offset)

        :else
        (throw (ex-info "Need at least an hour if timezone offset is given."
                        {:expression expression})))

      (some? timezone-offset)
      (if (some? hour)
        (date-time-dynamic-timezone-offset-op year month day hour minute second
                                              millisecond timezone-offset)
        (throw (ex-info "Need at least an hour if timezone offset is given."
                        {:expression expression})))

      :else
      (cond
        (and (int? millisecond) (int? second) (int? minute) (int? hour)
             (int? day) (int? month) (int? year))
        (system/date-time year month day hour minute second millisecond)

        (some? hour)
        (date-time-dynamic-op year month day hour minute second millisecond)

        (and (int? day) (int? month) (int? year))
        (system/date-time year month day)

        (some? day)
        (date-time-date-op year month day)

        (and (int? month) (int? year))
        (system/date-time year month)

        (some? month)
        (date-time-year-month-op year month)

        (int? year)
        (system/date-time year)

        :else
        (some-> year date-time-year-op)))))

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
(def ^:private now-expression
  (reify-expr core/Expression
    (-eval [_ {:keys [now]} _ _]
      (p/to-date-time now now))
    (-form [_]
      'now)))

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
(defn- time-op
  ([hour]
   (reify-expr core/Expression
     (-attach-cache [_ cache]
       (core/attach-cache-helper time-op cache hour))
     (-resolve-refs [_ expression-defs]
       (core/resolve-refs-helper time-op expression-defs hour))
     (-resolve-params [_ parameters]
       (core/resolve-params-helper time-op parameters hour))
     (-optimize [_ db]
       (core/optimize-helper time-op db hour))
     (-eval [_ context resource scope]
       (date-time/local-time (core/-eval hour context resource scope)))
     (-form [_]
       (list 'time (core/-form hour)))))
  ([hour minute]
   (reify-expr core/Expression
     (-attach-cache [_ cache]
       (core/attach-cache-helper time-op cache hour minute))
     (-resolve-refs [_ expression-defs]
       (core/resolve-refs-helper time-op expression-defs hour minute))
     (-resolve-params [_ parameters]
       (core/resolve-params-helper time-op parameters hour minute))
     (-optimize [_ db]
       (core/optimize-helper time-op db hour minute))
     (-eval [_ context resource scope]
       (date-time/local-time
        (core/-eval hour context resource scope)
        (core/-eval minute context resource scope)))
     (-form [_]
       (list 'time (core/-form hour) (core/-form minute)))))
  ([hour minute second]
   (reify-expr core/Expression
     (-attach-cache [_ cache]
       (core/attach-cache-helper time-op cache hour minute second))
     (-resolve-refs [_ expression-defs]
       (core/resolve-refs-helper time-op expression-defs hour minute second))
     (-resolve-params [_ parameters]
       (core/resolve-params-helper time-op parameters hour minute second))
     (-optimize [_ db]
       (core/optimize-helper time-op db hour minute second))
     (-eval [_ context resource scope]
       (date-time/local-time
        (core/-eval hour context resource scope)
        (core/-eval minute context resource scope)
        (core/-eval second context resource scope)))
     (-form [_]
       (list 'time (core/-form hour) (core/-form minute) (core/-form second)))))
  ([hour minute second millisecond]
   (reify-expr core/Expression
     (-resolve-refs [_ expression-defs]
       (time-op
        (core/-resolve-refs hour expression-defs)
        (core/-resolve-refs minute expression-defs)
        (core/-resolve-refs second expression-defs)
        (core/-resolve-refs millisecond expression-defs)))
     (-resolve-params [_ parameters]
       (time-op
        (core/-resolve-params hour parameters)
        (core/-resolve-params minute parameters)
        (core/-resolve-params second parameters)
        (core/-resolve-params millisecond parameters)))
     (-optimize [_ db]
       (time-op
        (core/-optimize hour db)
        (core/-optimize minute db)
        (core/-optimize second db)
        (core/-optimize millisecond db)))
     (-eval [_ context resource scope]
       (date-time/local-time
        (core/-eval hour context resource scope)
        (core/-eval minute context resource scope)
        (core/-eval second context resource scope)
        (core/-eval millisecond context resource scope)))
     (-form [_]
       (list 'time (core/-form hour) (core/-form minute) (core/-form second)
             (core/-form millisecond))))))

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
      (time-op hour minute second millisecond)

      (and (int? second) (int? minute) (int? hour))
      (date-time/local-time hour minute second)

      (some? second)
      (time-op hour minute second)

      (and (int? minute) (int? hour))
      (date-time/local-time hour minute)

      (some? minute)
      (time-op hour minute)

      (int? hour)
      (date-time/local-time hour)

      :else
      (time-op hour))))

(def ^:private time-of-day-expr
  (reify-expr core/Expression
    (-eval [_ {:keys [now]} _ _]
      (.toLocalTime ^OffsetDateTime now))
    (-form [_]
      'time-of-day)))

;; 18.21. TimeOfDay
(defmethod core/compile* :elm.compiler.type/time-of-day
  [_ _]
  time-of-day-expr)

(def ^:private today-expr
  (reify-expr core/Expression
    (-eval [_ {:keys [now]} _ _]
      (DateDate/fromLocalDate (.toLocalDate ^OffsetDateTime now)))
    (-form [_]
      'today)))

;; 18.22. Today
(defmethod core/compile* :elm.compiler.type/today
  [_ _]
  today-expr)
