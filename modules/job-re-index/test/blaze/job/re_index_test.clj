(ns blaze.job.re-index-test
  (:require
   [blaze.log]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is]]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest foo-test
  (is true))
