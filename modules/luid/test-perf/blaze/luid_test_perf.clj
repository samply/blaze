(ns blaze.luid-test-perf
  (:require
    [blaze.luid :as luid]
    [clojure.test :refer [deftest is testing]]
    [criterium.core :as criterium]))


(comment
  (let [clock (Clock/systemUTC)
        rng (ThreadLocalRandom/current)]
    (criterium/quick-bench (luid/luid clock rng)))

  )
