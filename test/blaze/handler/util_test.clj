(ns blaze.handler.util-test
  (:require
    [blaze.handler.util :refer :all]
    [clojure.test :refer :all]))


(deftest preference-test
  (is (= "representation"
         (preference {"prefer" "return=representation"} "return"))))
