(ns blaze.luid-test-perf
  (:require
    [blaze.luid :as luid]
    [clojure.test :refer [deftest is testing]]
    [criterium.core :as criterium]))


(comment
  (criterium/quick-bench (luid/luid))

  )
