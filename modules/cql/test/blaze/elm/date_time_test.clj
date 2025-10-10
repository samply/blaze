(ns blaze.elm.date-time-test
  (:require
   [blaze.elm.compiler :as c]
   [blaze.elm.date-time]
   [blaze.elm.date-time-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest]]))

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
