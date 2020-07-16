(ns blaze.handler.util-test
  (:require
    [blaze.handler.util :refer [preference]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer [deftest is]]))


(st/instrument)


(deftest preference-test
  (is (= "representation"
         (preference {"prefer" "return=representation"} "return"))))
