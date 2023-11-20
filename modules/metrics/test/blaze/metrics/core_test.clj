(ns blaze.metrics.core-test
  (:require
   [blaze.metrics.core :as metrics]
   [blaze.metrics.core-spec]
   [blaze.test-util :as tu]
   [clojure.datafy :as datafy]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest collect-test
  (testing "with no metrics"
    (is (empty? (metrics/collect (metrics/collector [])))))

  (testing "with one metric"
    (given (metrics/collect (metrics/collector
                              [(metrics/counter-metric "foo_total" "" [] [])]))
      [0 :name] := "foo"))

  (testing "with two metrics"
    (given (metrics/collect (metrics/collector
                              [(metrics/counter-metric "foo_total" "" [] [])
                               (metrics/counter-metric "bar_total" "" [] [])]))
      [0 :name] := "foo"
      [1 :name] := "bar")))

(deftest counter-metric-test
  (testing "with one label"
    (testing "with one sample"
      (given (datafy/datafy (metrics/counter-metric "foo_total" "" ["name"] [{:label-values ["bar"] :value 1.0}]))
        :name := "foo"
        :type := :counter
        [:samples 0 :label-names] := ["name"]
        [:samples 0 :label-values] := ["bar"]
        [:samples 0 :value] := 1.0))))

(deftest gauge-metric-test
  (testing "with one label"
    (testing "with one sample"
      (given (datafy/datafy (metrics/gauge-metric "foo" "" ["name"] [{:label-values ["bar"] :value 1.0}]))
        :name := "foo"
        :type := :gauge
        [:samples 0 :label-names] := ["name"]
        [:samples 0 :label-values] := ["bar"]
        [:samples 0 :value] := 1.0))))
