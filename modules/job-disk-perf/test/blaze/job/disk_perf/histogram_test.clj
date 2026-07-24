(ns blaze.job.disk-perf.histogram-test
  (:require
   [blaze.job.disk-perf.histogram :as histogram]
   [blaze.job.disk-perf.histogram-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest empty-test
  (let [h (histogram/create)]
    (is (zero? (histogram/total h)))
    (is (zero? (histogram/quantile h 0.5)))
    (is (zero? (histogram/maximum h)))))

(defn- relative-error [expected actual]
  (/ (abs (- (double actual) (double expected))) (double expected)))

(deftest quantile-test
  (testing "single value"
    (let [h (histogram/create)]
      (histogram/record! h 1000)
      (is (= 1 (histogram/total h)))
      (is (> 0.05 (relative-error 1000 (histogram/quantile h 0.5))))
      (is (> 0.05 (relative-error 1000 (histogram/maximum h))))))

  (testing "uniform distribution from 1 µs to 1 ms"
    (let [h (histogram/create)]
      (doseq [nanos (range 1000 1000001 1000)]
        (histogram/record! h nanos))
      (is (= 1000 (histogram/total h)))
      (is (> 0.05 (relative-error 500000 (histogram/quantile h 0.5))))
      (is (> 0.05 (relative-error 950000 (histogram/quantile h 0.95))))
      (is (> 0.05 (relative-error 990000 (histogram/quantile h 0.99))))
      (is (> 0.05 (relative-error 1000000 (histogram/maximum h))))))

  (testing "values below one nanosecond are clamped to the first bucket"
    (let [h (histogram/create)]
      (histogram/record! h 0)
      (is (= 1 (histogram/total h)))
      (is (pos? (histogram/quantile h 0.5)))))

  (testing "very large values are clamped to the last bucket"
    (let [h (histogram/create)]
      (histogram/record! h Long/MAX_VALUE)
      (is (= 1 (histogram/total h)))
      (is (pos? (histogram/quantile h 0.5))))))

(deftest merge-into-test
  (let [a (histogram/create)
        b (histogram/create)]
    (histogram/record! a 1000)
    (histogram/record! b 2000000)
    (histogram/merge-into! a b)
    (is (= 2 (histogram/total a)))
    (is (> 0.05 (relative-error 1000 (histogram/quantile a 0.5))))
    (is (> 0.05 (relative-error 2000000 (histogram/quantile a 1.0))))
    (is (> 0.05 (relative-error 2000000 (histogram/maximum a))))

    (testing "the source histogram is unchanged"
      (is (= 1 (histogram/total b))))))
