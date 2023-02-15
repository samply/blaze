(ns blaze.http.util-test
  (:require
    [blaze.http.util :as hu]
    [blaze.http.util-spec]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [juxt.iota :refer [given]]))


(st/instrument)


(test/use-fixtures :each tu/fixture)


(deftest parse-header-value-test
  (testing "one element"
    (given (hu/parse-header-value "a=b")
      count := 1
      [0 :name] := "a"
      [0 :value] := "b"))

  (testing "two elements"
    (given (hu/parse-header-value "a=b,c=d")
      count := 2
      [0 :name] := "a"
      [0 :value] := "b"
      [1 :name] := "c"
      [1 :value] := "d"))

  (testing "one element with one param"
    (given (hu/parse-header-value "a=b;c=d")
      count := 1
      [0 :name] := "a"
      [0 :value] := "b"
      [0 :params 0 :name] := "c"
      [0 :params 0 :value] := "d"))

  (testing "one element with two params"
    (given (hu/parse-header-value "a=b;c=d;e=f")
      count := 1
      [0 :name] := "a"
      [0 :value] := "b"
      [0 :params 0 :name] := "c"
      [0 :params 0 :value] := "d"
      [0 :params 1 :name] := "e"
      [0 :params 1 :value] := "f"))

  (testing "one element with one param and a second element with no param"
    (given (hu/parse-header-value "a=b;c=d,e=f")
      count := 2
      [0 :name] := "a"
      [0 :value] := "b"
      [0 :params 0 :name] := "c"
      [0 :params 0 :value] := "d"
      [1 :name] := "e"
      [1 :value] := "f")))
