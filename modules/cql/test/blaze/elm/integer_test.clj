(ns blaze.elm.integer-test
  (:require
   [blaze.elm.integer]
   [blaze.elm.protocols :as p]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

;; 16.14. Multiply
(deftest multiply-test
  (testing "works on java.lang.Integer instances"
    (is (= 6 (p/multiply (int 2) (int 3))))))
