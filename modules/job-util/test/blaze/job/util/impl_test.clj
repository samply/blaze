(ns blaze.job.util.impl-test
  (:require
   [blaze.job.util.impl :as impl]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest testing]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest retry-delay-micros-test
  (testing "without jitter the delay is 2^i milliseconds"
    (are [i delay] (= delay (impl/retry-delay-micros i 0.0))
      0 1000
      1 2000
      2 4000
      3 8000
      4 16000))

  (testing "a jitter of up to 2^i milliseconds is added"
    (are [i jitter delay] (= delay (impl/retry-delay-micros i jitter))
      0 0.5 1500
      0 0.999 1999
      1 0.5 3000
      4 0.25 20000
      4 0.999 31984)))
