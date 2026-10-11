(ns blaze.elm.date-time-test
  (:require
   [blaze.elm.compiler :as c]
   [blaze.elm.date-time :as date-time]
   [blaze.elm.date-time-spec]
   [blaze.elm.interval :refer [interval]]
   [blaze.elm.protocols :as p]
   [blaze.test-util :as tu :refer [given-thrown]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]])
  (:import
   [java.time ZoneOffset]
   [java.time.temporal ChronoUnit]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest print-form-test
  (are [date res] (= res (pr-str (c/form date)))
    #system/date"2023" "#system/date \"2023\""
    #system/date"2023-11" "#system/date \"2023-11\""
    #system/date"2023-11-02" "#system/date \"2023-11-02\""
    #system/date-time"2023" "#system/date-time \"2023\""
    #system/date-time"2023-11" "#system/date-time \"2023-11\""
    #system/date-time"2023-11-02" "#system/date-time \"2023-11-02\""
    #system/date-time"2023-11-02T14:49" "#system/date-time \"2023-11-02T14:49:00\""))

(def ^:private date-time-with-offset
  #system/date-time "2025-02-18T00:30:00+01:00")

(deftest offset-date-time-test
  (testing "Equal"
    (testing "another DateTime value with offset is compared by its instant"
      (are [y pred] (pred (p/equal date-time-with-offset y))
        #system/date-time "2025-02-17T23:30:00Z" true?
        #system/date-time "2025-02-17T23:31:00Z" false?))

    (testing "a DateTime value without offset is compared as written"
      (are [y pred] (pred (p/equal date-time-with-offset y))
        #system/date-time "2025-02-18T00:30:00" true?
        #system/date-time "2025-02-17T23:30:00" false?))

    (is (nil? (p/equal date-time-with-offset nil))))

  (testing "Equivalent"
    (are [y pred] (pred (p/equivalent date-time-with-offset y))
      #system/date-time "2025-02-17T23:30:00Z" true?
      #system/date-time "2025-02-17T23:31:00Z" false?
      #system/date-time "2025-02-18T00:30:00" true?
      nil false?))

  (testing "GreaterOrEqual"
    (are [y pred] (pred (p/greater-or-equal date-time-with-offset y))
      #system/date-time "2025-02-17T23:30:00Z" true?
      #system/date-time "2025-02-17T23:31:00Z" false?
      #system/date-time "2025-02-18T00:30:00" true?
      #system/date-time "2025-02-18T00:31:00" false?))

  (testing "DifferenceBetween is the same as for the local date-times"
    (testing "in the offset of the first value at hour precision"
      (is (= (p/difference-between #system/date-time "2025-02-18T00:30:00"
                                   #system/date-time "2025-02-18T04:30:00"
                                   ChronoUnit/HOURS)
             (p/difference-between date-time-with-offset
                                   #system/date-time "2025-02-18T03:30:00Z"
                                   ChronoUnit/HOURS))))

    (testing "as written at day precision"
      (is (= (p/difference-between #system/date-time "2025-02-18T00:30:00"
                                   #system/date-time "2025-02-20T23:30:00"
                                   ChronoUnit/DAYS)
             (p/difference-between date-time-with-offset
                                   #system/date-time "2025-02-20T23:30:00-01:00"
                                   ChronoUnit/DAYS)))))

  (testing "After"
    (testing "without precision the instants are compared"
      (are [y pred] (pred (p/after date-time-with-offset y nil))
        #system/date-time "2025-02-17T23:00:00Z" true?
        #system/date-time "2025-02-18T00:00:00Z" false?))

    (testing "at day precision the values are compared as written"
      (are [y pred] (pred (p/after date-time-with-offset y ChronoUnit/DAYS))
        #system/date-time "2025-02-17T23:59:00Z" true?
        #system/date-time "2025-02-18T00:00:00Z" false?)))

  (testing "Add with invalid RHS"
    (given-thrown (p/add date-time-with-offset 1)
      :message := "Invalid RHS adding to OffsetDateTime. Expected Period but was `class java.lang.Long`."))

  (testing "Subtract with invalid RHS"
    (given-thrown (p/subtract date-time-with-offset 1)
      :message := "Invalid RHS subtracting from OffsetDateTime. Expected Period but was `class java.lang.Long`.")))

(def ^:private now-utc #system/date-time "2025-01-01T00:00:00Z")
(def ^:private now-plus-one #system/date-time "2025-01-01T00:00:00+01:00")

(deftest same-offset?-test
  (are [o1 o2 now pred] (pred (date-time/same-offset? o1 o2 now))
    nil nil nil true?
    ZoneOffset/UTC ZoneOffset/UTC nil true?
    ZoneOffset/UTC (ZoneOffset/ofHours 1) nil false?
    nil ZoneOffset/UTC now-utc true?
    (ZoneOffset/ofHours 1) nil now-utc false?
    (ZoneOffset/ofHours 1) nil now-plus-one true?))

(deftest normalize-offset?-test
  (testing "values with the same offset are not normalized"
    (are [x y now] (false? (date-time/normalize-offset? x y now nil))
      date-time-with-offset date-time-with-offset now-utc
      date-time-with-offset #system/date-time "2025-02-18T00:30:00" now-plus-one
      #system/date-time "2025-02-18T00:30:00" date-time-with-offset now-plus-one
      (interval date-time-with-offset date-time-with-offset) date-time-with-offset now-utc
      (interval date-time-with-offset date-time/max-date-time) date-time-with-offset now-plus-one))

  (testing "values without offset are not normalized"
    (are [x y] (false? (date-time/normalize-offset? x y nil nil))
      #system/date-time "2025-02-18T00:30:00" #system/date-time "2025-02-18T00:30:00"
      1 2
      nil nil))

  (testing "values with different offsets are normalized"
    (are [x y now] (true? (date-time/normalize-offset? x y now nil))
      date-time-with-offset #system/date-time "2025-02-17T23:30:00Z" now-utc
      date-time-with-offset #system/date-time "2025-02-17T23:30:00Z" now-plus-one
      date-time-with-offset #system/date-time "2025-02-18T00:30:00" now-utc
      #system/date-time "2025-02-18T00:30:00" date-time-with-offset now-utc
      (interval date-time-with-offset date-time/max-date-time) date-time-with-offset now-utc
      (interval date-time-with-offset #system/date-time "2025-02-19T00:30:00Z") nil now-plus-one
      nil (interval date-time-with-offset #system/date-time "2025-02-19T00:30:00Z") now-plus-one))

  (testing "at hour precision or finer"
    (are [precision pred]
         (pred (date-time/normalize-offset? date-time-with-offset
                                            #system/date-time "2025-02-17T23:30:00Z"
                                            now-utc precision))
      ChronoUnit/YEARS false?
      ChronoUnit/MONTHS false?
      ChronoUnit/WEEKS false?
      ChronoUnit/DAYS false?
      ChronoUnit/HOURS true?
      ChronoUnit/MILLIS true?)))

(deftest normalize-offset-test
  (testing "DateTime values with offset are converted into the offset of now"
    (is (= #system/date-time "2025-02-17T23:30:00"
           (date-time/normalize-offset date-time-with-offset now-utc))))

  (testing "interval bounds with offset are converted into the offset of now"
    (is (= (interval #system/date-time "2025-02-17T23:30:00"
                     date-time/max-date-time)
           (date-time/normalize-offset
            (interval date-time-with-offset date-time/max-date-time)
            now-utc))))

  (testing "other values are kept"
    (are [x] (= x (date-time/normalize-offset x nil))
      nil
      1
      #system/date-time "2025-02-18T00:30:00"
      (interval 1 2))))
