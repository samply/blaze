(ns blaze.elm.ratio-test
  (:require
   [blaze.elm.compiler :as c]
   [blaze.elm.quantity :refer [quantity]]
   [blaze.elm.ratio :refer [ratio]]
   [blaze.elm.ratio-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest to-ratio-test
  (testing "attach-cache"
    (let [ratio (ratio (quantity 1 "1") (quantity 2 "1"))]
      (is (= [ratio] (st/with-instrument-disabled (c/attach-cache ratio ::cache)))))))
